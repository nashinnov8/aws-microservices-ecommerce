-- Create databases for microservices
CREATE DATABASE IF NOT EXISTS auth_db;
CREATE DATABASE IF NOT EXISTS product_db;
CREATE DATABASE IF NOT EXISTS inventory_db;
CREATE DATABASE IF NOT EXISTS order_db;

-- Grant privileges to 'auth' user (created by Docker MySQL entrypoint via MYSQL_USER env)
GRANT ALL PRIVILEGES ON auth_db.* TO 'auth'@'%';
GRANT ALL PRIVILEGES ON product_db.* TO 'auth'@'%';
GRANT ALL PRIVILEGES ON inventory_db.* TO 'auth'@'%';
GRANT ALL PRIVILEGES ON order_db.* TO 'auth'@'%';

FLUSH PRIVILEGES;
