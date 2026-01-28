-- Create databases for microservices
CREATE DATABASE IF NOT EXISTS auth_db;
CREATE DATABASE IF NOT EXISTS product_db;

-- Grant privileges to the user for both databases
GRANT ALL PRIVILEGES ON auth_db.* TO 'ecommerce'@'%';
GRANT ALL PRIVILEGES ON product_db.* TO 'ecommerce'@'%';

FLUSH PRIVILEGES;
