-- Approving a payment used to overwrite payment_reference (the customer's bank
-- transfer / receipt reference) with the admin's own transaction reference. Keep
-- them apart: the customer reference stays untouched, the admin reference lives here.
ALTER TABLE orders ADD COLUMN payment_verified_reference VARCHAR(200);
