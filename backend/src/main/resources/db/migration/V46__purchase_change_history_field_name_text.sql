-- Repair: Flyway baseline-at-V37 skipped V35 (field_name TEXT for batched multi-field audit rows).
-- Batched edits join many kotlin property names with " ; " — exceeds VARCHAR(128).
-- Idempotent: safe if V35 already applied.

ALTER TABLE purchase_change_history MODIFY COLUMN field_name TEXT NOT NULL;
