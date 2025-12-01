-- Remove unique constraint on chassis column to allow duplicate chassis numbers
-- Note: MySQL doesn't support IF EXISTS for DROP INDEX, so this may fail if index doesn't exist
ALTER TABLE purchases DROP INDEX uk_chassis;

