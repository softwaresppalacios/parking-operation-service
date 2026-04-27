CREATE SCHEMA IF NOT EXISTS parking_operation_service;

ALTER TABLE IF EXISTS parking_operation_service.parking_ticket
  ADD COLUMN IF NOT EXISTS key_slot_code varchar(255);

ALTER TABLE IF EXISTS parking_operation_service.parking_ticket
  ADD COLUMN IF NOT EXISTS helmet_locker_code varchar(255);

ALTER TABLE IF EXISTS parking_operation_service.parking_ticket
  ADD COLUMN IF NOT EXISTS monthly_subscription boolean NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS parking_operation_service.monthly_subscription (
  id BIGSERIAL PRIMARY KEY,
  uuid UUID NOT NULL UNIQUE,
  tenant_id UUID NOT NULL,
  customer_name VARCHAR(255) NOT NULL,
  customer_document VARCHAR(255),
  customer_phone VARCHAR(255),
  plate VARCHAR(255) NOT NULL,
  vehicle_type VARCHAR(255),
  amount NUMERIC(19,2) NOT NULL,
  payment_method VARCHAR(255),
  starts_at DATE NOT NULL,
  expires_at DATE NOT NULL,
  paid_at TIMESTAMP WITH TIME ZONE,
  active BOOLEAN NOT NULL DEFAULT TRUE
);

ALTER TABLE IF EXISTS parking_operation_service.monthly_subscription
  ADD COLUMN IF NOT EXISTS payment_method VARCHAR(255);
