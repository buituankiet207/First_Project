CREATE TABLE IF NOT EXISTS users (
  user_id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(100) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  full_name VARCHAR(150) NOT NULL,
  email VARCHAR(150) NOT NULL UNIQUE,
  phone VARCHAR(20),
  status ENUM('PENDING','ACTIVE','LOCKED') NOT NULL DEFAULT 'PENDING',
  failed_attempts INT NOT NULL DEFAULT 0,
  locked_until DATETIME NULL,
  created_at DATETIME NOT NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS members (
  user_id INT PRIMARY KEY,
  address VARCHAR(255),
  date_of_birth DATE,
  card_number VARCHAR(30) UNIQUE,
  card_expiry DATE,
  CONSTRAINT fk_members_user FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS librarians (
  user_id INT PRIMARY KEY,
  staff_code VARCHAR(50) NOT NULL UNIQUE,
  hire_date DATE,
  CONSTRAINT fk_librarians_user FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS admins (
  user_id INT PRIMARY KEY,
  admin_level INT NOT NULL DEFAULT 1,
  CONSTRAINT fk_admins_librarian FOREIGN KEY (user_id) REFERENCES librarians(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS categories (
  category_id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(150) NOT NULL,
  name_norm VARCHAR(150) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS publishers (
  publisher_id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(150) NOT NULL,
  name_norm VARCHAR(150) NOT NULL UNIQUE,
  address VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS authors (
  author_id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(150) NOT NULL,
  name_norm VARCHAR(150) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS books (
  book_id INT AUTO_INCREMENT PRIMARY KEY,
  isbn VARCHAR(30) NOT NULL UNIQUE,
  title VARCHAR(300) NOT NULL,
  title_norm VARCHAR(300) NOT NULL,
  publish_year INT NOT NULL,
  description TEXT,
  category_id INT NOT NULL,
  publisher_id INT NOT NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  INDEX idx_books_title (title_norm(100)),
  CONSTRAINT fk_books_category FOREIGN KEY (category_id) REFERENCES categories(category_id),
  CONSTRAINT fk_books_publisher FOREIGN KEY (publisher_id) REFERENCES publishers(publisher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS book_authors (
  book_id INT NOT NULL,
  author_id INT NOT NULL,
  author_order INT NOT NULL DEFAULT 0,
  PRIMARY KEY (book_id, author_id),
  CONSTRAINT fk_ba_book FOREIGN KEY (book_id) REFERENCES books(book_id),
  CONSTRAINT fk_ba_author FOREIGN KEY (author_id) REFERENCES authors(author_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS book_copies (
  copy_id INT AUTO_INCREMENT PRIMARY KEY,
  barcode VARCHAR(40) NOT NULL UNIQUE,
  status ENUM('AVAILABLE','BORROWED','RESERVED','DAMAGED','LOST') NOT NULL DEFAULT 'AVAILABLE',
  shelf_location VARCHAR(50),
  book_id INT NOT NULL,
  INDEX idx_copies_book (book_id),
  CONSTRAINT fk_copies_book FOREIGN KEY (book_id) REFERENCES books(book_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS loans (
  loan_id INT AUTO_INCREMENT PRIMARY KEY,
  member_id INT NOT NULL,
  copy_id INT NOT NULL,
  librarian_id INT NOT NULL,
  loan_date DATE NOT NULL,
  due_date DATE NOT NULL,
  return_date DATE NULL,
  renew_count INT NOT NULL DEFAULT 0,
  status ENUM('BORROWED','RETURNED','LOST') NOT NULL DEFAULT 'BORROWED',
  INDEX idx_loans_member (member_id),
  INDEX idx_loans_copy (copy_id, status),
  INDEX idx_loans_date (loan_date),
  CONSTRAINT fk_loans_member FOREIGN KEY (member_id) REFERENCES members(user_id),
  CONSTRAINT fk_loans_copy FOREIGN KEY (copy_id) REFERENCES book_copies(copy_id),
  CONSTRAINT fk_loans_librarian FOREIGN KEY (librarian_id) REFERENCES librarians(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fines (
  fine_id INT AUTO_INCREMENT PRIMARY KEY,
  loan_id INT NOT NULL,
  amount DECIMAL(12,0) NOT NULL,
  reason ENUM('OVERDUE','DAMAGED','LOST') NOT NULL,
  status ENUM('UNPAID','PAID') NOT NULL DEFAULT 'UNPAID',
  created_at DATETIME NOT NULL,
  paid_at DATETIME NULL,
  INDEX idx_fines_loan (loan_id),
  CONSTRAINT fk_fines_loan FOREIGN KEY (loan_id) REFERENCES loans(loan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS reservations (
  reservation_id INT AUTO_INCREMENT PRIMARY KEY,
  member_id INT NOT NULL,
  book_id INT NOT NULL,
  reserved_at DATETIME NOT NULL,
  expires_at DATETIME NULL,
  status ENUM('WAITING','READY','FULFILLED','CANCELLED','EXPIRED') NOT NULL DEFAULT 'WAITING',
  INDEX idx_res_book (book_id, status),
  INDEX idx_res_member (member_id, status),
  CONSTRAINT fk_res_member FOREIGN KEY (member_id) REFERENCES members(user_id),
  CONSTRAINT fk_res_book FOREIGN KEY (book_id) REFERENCES books(book_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS config (
  cfg_key VARCHAR(50) PRIMARY KEY,
  cfg_value VARCHAR(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
