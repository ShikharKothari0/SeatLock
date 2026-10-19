ALTER TABLE app_user ADD COLUMN password_hash VARCHAR(100);
ALTER TABLE app_user ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER'
    CHECK (role IN ('CUSTOMER', 'ADMIN'));
-- Existing demo identities remain disabled for password login. Registration cannot claim them.
CREATE TABLE account_email (
    email VARCHAR(255) PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES app_user(id)
);
INSERT INTO account_email(email, user_id)
SELECT lower(trim(email)), min(id::text)::uuid FROM app_user GROUP BY lower(trim(email));
