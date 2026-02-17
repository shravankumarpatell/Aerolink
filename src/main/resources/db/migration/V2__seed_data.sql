-- =============================================================
-- V2: Seed Data for Development & Testing
-- Sample passengers and cabs around Mumbai Airport (BOM)
-- =============================================================

-- Passengers
INSERT INTO passengers (id, name, email, phone) VALUES
    ('a1b2c3d4-1111-1111-1111-000000000001', 'Rahul Sharma',    'rahul@example.com',    '+91-9876543210'),
    ('a1b2c3d4-1111-1111-1111-000000000002', 'Priya Patel',     'priya@example.com',    '+91-9876543211'),
    ('a1b2c3d4-1111-1111-1111-000000000003', 'Amit Kumar',      'amit@example.com',     '+91-9876543212'),
    ('a1b2c3d4-1111-1111-1111-000000000004', 'Sneha Reddy',     'sneha@example.com',    '+91-9876543213'),
    ('a1b2c3d4-1111-1111-1111-000000000005', 'Vikram Singh',    'vikram@example.com',   '+91-9876543214'),
    ('a1b2c3d4-1111-1111-1111-000000000006', 'Anjali Desai',    'anjali@example.com',   '+91-9876543215'),
    ('a1b2c3d4-1111-1111-1111-000000000007', 'Karan Mehta',     'karan@example.com',    '+91-9876543216'),
    ('a1b2c3d4-1111-1111-1111-000000000008', 'Divya Iyer',      'divya@example.com',    '+91-9876543217');

-- Cabs near Mumbai Airport (19.0896, 72.8656)
INSERT INTO cabs (id, license_plate, driver_name, total_seats, luggage_capacity, current_lat, current_lng, status) VALUES
    ('b2c3d4e5-2222-2222-2222-000000000001', 'MH-01-AB-1234', 'Rajesh Driver',    4, 4, 19.0900, 72.8660, 'AVAILABLE'),
    ('b2c3d4e5-2222-2222-2222-000000000002', 'MH-01-CD-5678', 'Suresh Driver',    4, 3, 19.0880, 72.8640, 'AVAILABLE'),
    ('b2c3d4e5-2222-2222-2222-000000000003', 'MH-01-EF-9012', 'Manoj Driver',     6, 6, 19.0910, 72.8670, 'AVAILABLE'),
    ('b2c3d4e5-2222-2222-2222-000000000004', 'MH-01-GH-3456', 'Anil Driver',      4, 4, 19.0870, 72.8650, 'AVAILABLE'),
    ('b2c3d4e5-2222-2222-2222-000000000005', 'MH-01-IJ-7890', 'Deepak Driver',    4, 4, 19.0920, 72.8680, 'AVAILABLE'),
    ('b2c3d4e5-2222-2222-2222-000000000006', 'MH-01-KL-1357', 'Ramesh Driver',    6, 5, 19.0850, 72.8630, 'AVAILABLE'),
    ('b2c3d4e5-2222-2222-2222-000000000007', 'MH-01-MN-2468', 'Prakash Driver',   4, 4, 19.0930, 72.8690, 'OFFLINE'),
    ('b2c3d4e5-2222-2222-2222-000000000008', 'MH-01-OP-3579', 'Vivek Driver',     4, 3, 19.0860, 72.8620, 'AVAILABLE');
