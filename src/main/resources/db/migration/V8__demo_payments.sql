CREATE TABLE payment_attempt (
 id UUID PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES app_user(id),
 quote_id UUID NOT NULL UNIQUE REFERENCES checkout_quote(id),
 idempotency_key VARCHAR(255) NOT NULL UNIQUE,
 method VARCHAR(20) NOT NULL CHECK(method IN ('UPI','CARD','NET_BANKING')),
 scenario VARCHAR(30) NOT NULL CHECK(scenario IN ('SUCCESS','FAILURE','DELAYED_SUCCESS')),
 status VARCHAR(20) NOT NULL CHECK(status IN ('PENDING','SUCCEEDED','FAILED','EXPIRED')),
 amount_minor BIGINT NOT NULL CHECK(amount_minor>=0),
 currency VARCHAR(3) NOT NULL,
 ready_at TIMESTAMPTZ NOT NULL,
 expires_at TIMESTAMPTZ NOT NULL,
 booking_id UUID UNIQUE REFERENCES booking(id),
 booking_response TEXT,
 result_code VARCHAR(40),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK ((status='SUCCEEDED') = (booking_id IS NOT NULL AND booking_response IS NOT NULL))
);
CREATE INDEX payment_pending_due ON payment_attempt(ready_at) WHERE status='PENDING';
CREATE INDEX payment_owner ON payment_attempt(user_id,created_at);
