-- Rotate default admin password (was legacy seed: password / admin123 in docs).
-- Plaintext is not stored here; operators: see README default login credentials.
UPDATE users
SET password_hash = '$2a$10$LTYH39OYh/foPh8KAylzQubnsXx7Y9J6SwNMS5mqi80DYhsFOzGgG'
WHERE email = 'admin@automan.com';
