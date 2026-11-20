<?php
header('Content-Type: application/json');

// Check request method
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    echo json_encode(['status' => 'error', 'message' => 'Invalid request method.']);
    exit;
}

// Collect and sanitize form inputs
$name        = filter_input(INPUT_POST, 'name', FILTER_SANITIZE_SPECIAL_CHARS) ?? 'Guest';
$phone       = filter_input(INPUT_POST, 'phone', FILTER_SANITIZE_SPECIAL_CHARS) ?? '';
$event_date  = filter_input(INPUT_POST, 'event_date', FILTER_SANITIZE_SPECIAL_CHARS) ?? '';
$occasion    = filter_input(INPUT_POST, 'occasion', FILTER_SANITIZE_SPECIAL_CHARS) ?? 'Wedding Ceremony';
$guests      = filter_input(INPUT_POST, 'guests', FILTER_SANITIZE_SPECIAL_CHARS) ?? '500';
$design_opt  = filter_input(INPUT_POST, 'design_theme', FILTER_SANITIZE_SPECIAL_CHARS) ?? 'Design 1';
$created_at  = date('Y-m-d H:i:s');

if (empty($phone) || empty($event_date)) {
    echo json_encode(['status' => 'error', 'message' => 'Phone and Event Date are required.']);
    exit;
}

// 1. Fallback logging to inquiries.csv (ensures leads are never lost even without MySQL configured)
$csvFile = __DIR__ . '/inquiries.csv';
$csvExists = file_exists($csvFile);
$fp = fopen($csvFile, 'a');
if (!$csvExists) {
    fputcsv($fp, ['Date_Submitted', 'Full_Name', 'Phone', 'Event_Date', 'Occasion', 'Guests', 'Design_Theme']);
}
fputcsv($fp, [$created_at, $name, $phone, $event_date, $occasion, $guests, $design_opt]);
fclose($fp);

// 2. Optional MySQL Insertion (if XAMPP MySQL database 'marriage_hall_db' is created)
$dbHost = 'localhost';
$dbUser = 'root';
$dbPass = '';
$dbName = 'marriage_hall_db';

$dbConnected = false;
try {
    $mysqli = @new mysqli($dbHost, $dbUser, $dbPass, $dbName);
    if (!$mysqli->connect_error) {
        $stmt = $mysqli->prepare("INSERT INTO inquiries (name, phone, event_date, occasion, guest_count, design_theme, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)");
        if ($stmt) {
            $stmt->bind_param("sssssss", $name, $phone, $event_date, $occasion, $guests, $design_opt, $created_at);
            $stmt->execute();
            $stmt->close();
            $dbConnected = true;
        }
        $mysqli->close();
    }
} catch (Exception $e) {
    // Fail silently to file fallback
}

echo json_encode([
    'status' => 'success',
    'message' => 'Thank you! Your wedding date enquiry has been logged successfully.',
    'db_saved' => $dbConnected,
    'backup_saved' => true,
    'data' => [
        'name' => $name,
        'event_date' => $event_date
    ]
]);
