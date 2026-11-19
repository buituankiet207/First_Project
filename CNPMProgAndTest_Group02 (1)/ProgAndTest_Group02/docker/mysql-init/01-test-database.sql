-- Separate database for the automated tests (they empty their tables). Runs once, when the MySQL volume is created.
CREATE DATABASE IF NOT EXISTS library_test CHARACTER SET utf8mb4;
GRANT ALL PRIVILEGES ON library_test.* TO 'library'@'%';
FLUSH PRIVILEGES;
