CREATE TABLE razorpay_attempt (
 id UUID PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES app_user(id),
 quote_id UUID NOT NULL UNIQUE REFERENCES checkout_quote(id),
 idempotency_key VARCHAR(255) NOT NULL UNIQUE,
 amount_minor BIGINT NOT NULL CHECK(amount_minor>=100),
 currency VARCHAR(3) NOT NULL CHECK(currency='INR'),
 status VARCHAR(30) NOT NULL CHECK(status IN ('CREATING','PENDING','EXPIRED','SUCCEEDED','REFUND_PENDING','REFUNDED')),
 order_id VARCHAR(80) UNIQUE,
 payment_id VARCHAR(80) UNIQUE,
 refund_id VARCHAR(80),
 booking_response TEXT,
 expires_at TIMESTAMPTZ NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 checked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK ((status='SUCCEEDED') = (booking_response IS NOT NULL))
);
CREATE INDEX razorpay_recovery ON razorpay_attempt(checked_at) WHERE status<>'SUCCEEDED' AND status<>'REFUNDED';
