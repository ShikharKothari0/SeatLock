\# SeatLock



SeatLock is a distributed reservation engine built around one core problem:



> \*\*Many users attempt to reserve a limited number of unique resources simultaneously, while ensuring each resource is allocated to at most one user.\*\*



The project is modeled as a \*\*venue-based event ticketing system\*\*, where users reserve seats for concerts, movies, or sporting events. Although ticketing is the primary use case, the same reservation pattern applies to domains such as airline seat booking, hotel reservations, and limited-inventory flash sales.



\## Features



\* Distributed seat reservation with zero overselling

\* Temporary seat holds with automatic expiration

\* Event-driven booking workflow

\* High-concurrency reservation handling

\* Redis-based distributed locking

\* Kafka-powered asynchronous event processing

\* Performance and load testing

\* Production-ready observability and deployment pipeline



\## Tech Stack



\* \*\*Java 21\*\*

\* \*\*Spring Boot 3\*\*

\* \*\*PostgreSQL\*\*

\* \*\*Redis\*\*

\* \*\*Apache Kafka\*\*

\* \*\*Flyway\*\*

\* \*\*Docker\*\*

\* \*\*Testcontainers\*\*

\* \*\*Resilience4j\*\*

\* \*\*Prometheus \& Grafana\*\*

\* \*\*GitHub Actions\*\*

\* \*\*Kubernetes\*\*



\## Project Goals



SeatLock is designed to explore production-grade backend engineering concepts, including:



\* Distributed systems

\* Concurrency control

\* Event-driven architecture

\* Caching strategies

\* Fault tolerance

\* Scalability

\* Load testing and observability



The emphasis is on building a system that remains correct and reliable under heavy concurrent demand, where preventing duplicate reservations is the primary engineering challenge.



