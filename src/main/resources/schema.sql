CREATE SCHEMA IF NOT EXISTS parking_operation_service;

ALTER TABLE IF EXISTS parking_operation_service.parking_ticket
  ADD COLUMN IF NOT EXISTS key_slot_code varchar(255);

ALTER TABLE IF EXISTS parking_operation_service.parking_ticket
  ADD COLUMN IF NOT EXISTS helmet_locker_code varchar(255);

ALTER TABLE IF EXISTS parking_operation_service.parking_ticket
  ADD COLUMN IF NOT EXISTS monthly_subscription boolean NOT NULL DEFAULT false;
