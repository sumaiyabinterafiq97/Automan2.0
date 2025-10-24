-- Add totalCnfPrice column to purchases table
ALTER TABLE purchases ADD COLUMN total_cnf_price DECIMAL(15,2) DEFAULT NULL;
