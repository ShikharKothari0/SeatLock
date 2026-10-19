FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl && addgroup -S seatlock && adduser -S seatlock -G seatlock
WORKDIR /app
COPY --from=build /build/target/SeatLock-0.0.1-SNAPSHOT.jar app.jar
USER seatlock
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=6 CMD curl -fsS http://localhost:${PORT:-8080}/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "app.jar"]
