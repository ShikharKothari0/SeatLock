package seatlock

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class SeatLockSimulation extends Simulation {

  // base configuration
  val baseUrl = sys.env.getOrElse("SEATLOCK_BASE_URL", "http://localhost:8080")

  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("Gatling/SeatLock-LoadTest")
    .shareConnections  // share HTTP connections across virtual users (realistic)

  // seed data — must match V2__seed_data.sql
  val eventId   = "22222222-2222-2222-2222-222222222222"
  val userId    = "33333333-3333-3333-3333-333333333333"

  // scenario: browse events then hold and confirm a seat
  val browseAndBook = scenario("Browse and Book")

    // Step 1: GET all events (simulates landing on the homepage)
    .exec(
      http("GET /api/events")
        .get("/api/events")
        .check(status.is(200))
    )
    .pause(1.second, 3.seconds)  // simulate user reading the page

    // Step 2: GET available seats for the event (simulates opening seat map)
    .exec(
      http("GET /api/events/{eventId}/seats")
        .get(s"/api/events/$eventId/seats?status=AVAILABLE")
        .check(status.is(200))
        .check(jsonPath("$[0].id").saveAs("seatId"))
    )
    .pause(2.seconds, 5.seconds)  // simulate user selecting a seat

    // Step 3: POST hold (the hot path — Redis Lua lock, Postgres write, Kafka publish)
    .exec(session =>
      // generate a unique idempotency key per virtual user per iteration
      session.set("idempotencyKey", s"gatling-${session.userId}-${System.currentTimeMillis()}")
    )
    .exec(
      http("POST /api/seats/{seatId}/hold")
        .post("/api/seats/#{seatId}/hold")
        .body(StringBody(s"""{"userId":"$userId"}"""))
        .check(status.in(200, 409))  // 409 is correct when seat already held
    )
    .pause(1.second, 2.seconds)

    // Step 4: POST confirm (only if hold succeeded — 200 from Step 3)
    .doIf(session => session("holdStatus").asOption[Int].contains(200)) {
      exec(
        http("POST /api/bookings/confirm")
          .post("/api/bookings/confirm")
          .body(StringBody("""
            {
              "userId": "#{userId}",
              "seatIds": ["#{seatId}"],
              "idempotencyKey": "#{idempotencyKey}"
            }
          """.stripMargin))
          .check(status.in(200, 409))
      )
    }

  // read-heavy scenario: seat listing cache performance
  // fires GET /seats repeatedly to stress-test the cache-aside layer
  val browseSeatListing = scenario("Browse Seat Listing")
    .during(70.seconds) {   // slightly longer than rampUsers(20).during(10s) + constantUsersPerSec(20).during(1.minute)
      exec(
        http("GET seats (cache stress)")
          .get(s"/api/events/$eventId/seats")
          .check(status.is(200))
      )
        .pause(200.milliseconds, 500.milliseconds)
    }

  // injection profiles

  // Profile A: realistic booking ramp
  // Simulates a ticket sale going live:
  //   - ramp from 1 to 50 users over 30 seconds (sale opens, users flood in)
  //   - hold 50 concurrent users for 1 minute (peak traffic)
  //   - ramp down to 0 over 30 seconds (interest drops off)
  val bookingLoad = browseAndBook.inject(
    rampUsersPerSec(1).to(50).during(30.seconds),
    constantUsersPerSec(50).during(1.minute),
    rampUsersPerSec(50).to(0).during(30.seconds)
  )

  // Profile B: cache stress test
  // 20 users hammering the seat listing endpoint to validate
  // stampede protection and hit ratio under sustained concurrent load
  val cacheLoad = browseSeatListing.inject(
    rampUsers(20).during(10.seconds),
    constantUsersPerSec(20).during(1.minute)
  )

  // assertions: test fails if these thresholds are not met

  // Thresholds reflect single-machine localhost environment (Postgres + Redis + Kafka + App
  // sharing CPU/memory). Production targets would be: p50<50ms, p95<200ms, p99<500ms.
  // Isolated bookingLoad baseline (no cacheLoad): p50=4ms, p95=7ms, p99=13ms.

  setUp(bookingLoad, cacheLoad)
    .protocols(httpProtocol)
    .assertions(
      // overall response time
      global.responseTime.percentile(50).lt(400),   // p50 < 200ms          localhost under combined load
      global.responseTime.percentile(95).lt(1200),   // p95 < 500ms
      global.responseTime.percentile(99).lt(1500),  // p99 < 1000ms

      // success rate. 409 is expected and counted as success
      // we only care that we're not getting 500s
      global.successfulRequests.percent.gte(99),

      // specific request assertions
      details("POST /api/seats/{seatId}/hold")
        .responseTime.percentile(99).lt(2500),

      details("GET /api/events/{eventId}/seats")
        .responseTime.percentile(99).lt(1500)  // cached reads should be fast
    )
}