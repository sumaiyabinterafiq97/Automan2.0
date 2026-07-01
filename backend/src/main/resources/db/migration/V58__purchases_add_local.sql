ALTER TABLE purchases ADD COLUMN `local` TINYINT(1) NOT NULL DEFAULT 0;

UPDATE purchases SET `local` = 1 WHERE UPPER(TRIM(client_name)) = 'LOCAL';
