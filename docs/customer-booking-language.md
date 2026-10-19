# Customer booking language

Updated 18 October 2026. Customer screens describe selecting seats, continuing to checkout, completing a reservation and checking a booking. Internal hold, quote, snapshot and server terminology remains in code and technical documentation rather than customer-facing booking copy.

- Seat selection uses “Continue to checkout”, “Reserved” and “Cancel reservation”. “Clear new selection” distinguishes newly selected seats from an existing reservation.
- The countdown says “Complete your booking within”; expiry explains that the reservation expired and offers a return to seat selection.
- Checkout offers “Update total” and “Continue to payment”. Price expiry asks the customer to review the updated total.
- An uncertain confirmation offers “Check booking status” while preserving the original confirmation payload and idempotency key.
- Closed sales, unavailable seats and expired reservations receive distinct messages. Reservation pages do not render arbitrary backend error text.
- Booking history and receipts use customer-facing wording. The homepage no longer includes the Active Locks placeholder. Demo-payment disclosure remains visible.

Reservation APIs, time limits and confirmation semantics are unchanged. Payment simulation remains the next functional milestone.

Validation: frontend build and lint passed; 17 unit tests and 10 controlled-browser scenarios passed. Browser coverage includes partial selection, closed sales, the expiry dialog, cancellation, lost responses, refresh, recovery and mobile layout. Desktop checkout and mobile seat-selection screenshots were visually reviewed. This pass did not rerun the live infrastructure suite; its scripts were updated to use the new control labels.
