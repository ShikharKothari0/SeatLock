# Razorpay Test Mode

This integration is sandbox-only. The existing demo provider remains the default.
No credentials are committed and no live payments are supported. Enabling the
gateway rejects any key that does not begin with `rzp_test_`.

## Enable on Render after creating a Razorpay account

1. Open Razorpay's dashboard in **Test Mode** and generate test API keys.
2. Configure automatic capture in that dashboard. SeatLock confirms a booking
   only after the provider reports `captured`; authorisation alone is insufficient.
3. Set these environment variables privately on the Render backend:
   - `SEATLOCK_RAZORPAY_ENABLED=true`
   - `RAZORPAY_KEY_ID`: the test key ID (public identifier).
   - `RAZORPAY_KEY_SECRET`: the test API secret.
   - `RAZORPAY_WEBHOOK_SECRET`: a separate random secret of at least 32 characters.
4. Add a **Test Mode** webhook with the same webhook secret:
   `https://seatlock-api-cuvu.onrender.com/api/payments/razorpay/webhook`
   Subscribe to `payment.captured`, `payment.failed` and `order.paid`.
5. Deploy the backend and frontend. No Razorpay secret belongs in Vercel or in
   a `VITE_` variable. The frontend reads the active provider from the backend.
6. Complete a hosted sandbox transaction using Razorpay's documented test
   payment details. Verify the dashboard payment, SeatLock receipt and retained
   booking after refresh. Also test dismissing checkout and a declined payment.

Leave the feature disabled until keys and the webhook are configured. Switching
providers does not alter existing bookings; persisted demo attempts can still be
checked. Do not disable Razorpay while any gateway payment or refund is pending:
its recovery worker requires the configured test credentials.

## Flow and invariants

- Prices, currency and seat IDs come from the authenticated customer's saved
  quote. Minimum gateway total is INR 1.00. Zero-price checkout is unsupported
  while Razorpay is enabled; the demo provider must be used for those events.
- A unique payment attempt is committed before order creation. Its UUID is the
  provider receipt. Repeating the same request returns the same attempt; a
  different request cannot reuse its key or quote.
- A lost order-creation response leaves `CREATING`. Recovery searches orders by
  the exact receipt, validates the result and attaches it. It never blindly
  repeats the create call. If no order exists, an operator must investigate; the
  UI does not pretend that the payment failed or create a replacement order.
- API keys stay on the server. Browser callbacks use a constant-time HMAC check
  over the stored order ID and returned payment ID. Payment amount, currency,
  order and captured state are checked against the provider API before booking.
- Only the webhook endpoint bypasses session authentication/CSRF; it requires
  HMAC verification of the original request bytes with a separate secret. It
  treats notifications as a prompt to reconcile rather than trusting their
  delivery order. The attempt row lock and booking idempotency prevent duplicate
  bookings when callbacks, polling and webhooks race.
- Booking confirmation still checks the original quote and reservation tokens,
  expiry and sale status. A captured payment that cannot become a booking enters
  `REFUND_PENDING`. Refund requests use a stable `X-Refund-Idempotency` key;
  retries after a lost response cannot issue a different refund request.
- Pending refunds are fetched until the provider reports `processed`; only then
  does the UI show `REFUNDED`. Existing sold seats are never reassigned.
- Network/server failures remain retryable. Payment declines do not close the
  order prematurely because another payment attempt can succeed on that order.

## Recovery and limits

The worker checks up to ten attempts every 30 seconds. Order/payment recovery
runs for the first day; pending refunds remain eligible afterwards. Signed
notifications and owner-triggered reconciliation can still process older
attempts. Free hosting can suspend the worker: durable records survive, but
recovery resumes only when the application runs again.

Inspect unresolved `CREATING`, `REFUND_PENDING` or unusual amount/order mismatches
in `razorpay_attempt` and the Razorpay Test dashboard. Never change a booking to
successful based on a screenshot. A refund marked failed by the provider stays
pending in SeatLock for manual investigation; it is not reported as refunded.
Payments externally partially refunded before verification are also held for
review. An administrator recovery UI is not included in this version.

Automated tests use a mocked gateway with real isolated PostgreSQL/Redis for
booking logic, plus controlled browser fixtures. They do not replace the final
hosted Razorpay sandbox check. No account or keys were supplied during development.

## References

- https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/integration-steps/
- https://razorpay.com/docs/payments/dashboard/test-live-modes/
- https://razorpay.com/docs/webhooks/validate-test/
- https://github.com/razorpay/markdown-docs/blob/master/api/refunds/normal-refunds-idempotent.md
