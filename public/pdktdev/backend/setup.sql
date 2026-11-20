-- Marriage Hall Database Schema for XAMPP phpMyAdmin
-- Create database: marriage_hall_db

CREATE DATABASE IF NOT EXISTS `marriage_hall_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `marriage_hall_db`;

CREATE TABLE IF NOT EXISTS `inquiries` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(150) NOT NULL,
  `phone` VARCHAR(25) NOT NULL,
  `event_date` DATE NOT NULL,
  `occasion` VARCHAR(100) DEFAULT NULL,
  `guest_count` VARCHAR(50) DEFAULT NULL,
  `design_theme` VARCHAR(50) DEFAULT NULL,
  `status` ENUM('new', 'contacted', 'booked', 'cancelled') DEFAULT 'new',
  `notes` TEXT DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
