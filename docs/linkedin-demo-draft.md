# LinkedIn draft — review after the public recording

I built SeatLock, an event-ticket booking project using Java 21, Spring Boot, React and TypeScript.

The problem I wanted to explore was simple: what happens when multiple people try to book the same seat?

SeatLock combines PostgreSQL transactions, Redis locking and a Kafka-backed event flow. The customer journey includes sign-in, event discovery, seat selection, price confirmation, simulated payment and booking history. Administrators can create venues, edit event drafts and publish them.

The work also covered less visible failure cases: reservation expiry, retries after uncertain responses, preserving booking receipts, and recovering the database from a backup.

Payments are simulated; this is a personal engineering project.

[Add the reviewed demo video and GitHub repository link here.]

---

Editorial notes (remove before posting):
- Do not say the project is publicly deployed until the hosted journey is verified.
- Add concurrency figures only with the corresponding retained report and workload details.
- If hosting has been shut down, link the recording and runnable repository rather than an inactive live URL.
- This draft has not been published.
