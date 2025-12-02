-- Schema for Hotel Booking System
-- Creates database `hotel_db` and tables `users`, `rooms`, `bookings`.

CREATE DATABASE IF NOT EXISTS hotel_db;
USE hotel_db;

-- Users: customers and owners
CREATE TABLE IF NOT EXISTS users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  username VARCHAR(50) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  email VARCHAR(150),
  phone VARCHAR(30),
  user_type ENUM('CUSTOMER','OWNER') NOT NULL DEFAULT 'CUSTOMER',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Rooms
CREATE TABLE IF NOT EXISTS rooms (
  id INT AUTO_INCREMENT PRIMARY KEY,
  room_number VARCHAR(20) NOT NULL UNIQUE,
  type VARCHAR(50) NOT NULL,
  price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  available BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Bookings
CREATE TABLE IF NOT EXISTS bookings (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  room_id INT NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'BOOKED',
  payment_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE
);

-- Sample data (optional)
INSERT IGNORE INTO users (name, username, password, email, phone, user_type)
VALUES
('Owner One','owner','ownerpass','owner@example.com','1234567890','OWNER'),
('Customer One','customer','customerpass','cust@example.com','0987654321','CUSTOMER');

INSERT IGNORE INTO rooms (room_number, type, price, available)
VALUES
('101','Single',50.00, TRUE),
('102','Double',80.00, TRUE),
('201','Suite',150.00, TRUE);

-- Reviews
CREATE TABLE IF NOT EXISTS reviews (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  room_id INT NOT NULL,
  review_text TEXT,
  rating INT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE
);

-- Payments
CREATE TABLE IF NOT EXISTS payments (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  booking_id INT NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  payment_method VARCHAR(50),
  googlepay_number VARCHAR(50),
  qr_code TEXT,
  pin VARCHAR(20),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE
);

-- Sample reviews & payments
INSERT IGNORE INTO reviews (user_id, room_id, review_text, rating)
VALUES (2, 1, 'Nice clean room', 5);

INSERT IGNORE INTO payments (user_id, booking_id, amount, status, payment_method, googlepay_number, qr_code, pin)
VALUES (2, 1, 50.00, 'PAID', 'GOOGLEPAY', '9999999999', 'sample-qr-data', '1234');
