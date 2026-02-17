-- =============================================================
-- V3: Pool Window Architecture Refactor
-- Wipe old data, add pool window columns, new realistic dataset
-- All cabs: 4 seats / 4 luggage
-- =============================================================

-- 1. Wipe existing data (order matters for FK constraints)
DELETE FROM pricing_records;
DELETE FROM ride_requests;
DELETE FROM ride_pools;
DELETE FROM cabs;
DELETE FROM passengers;

-- 2. Update ride_pools schema for pool window model
ALTER TABLE ride_pools ADD COLUMN IF NOT EXISTS window_expires_at TIMESTAMP;
ALTER TABLE ride_pools ADD COLUMN IF NOT EXISTS dispatched_at TIMESTAMP;
ALTER TABLE ride_pools ADD COLUMN IF NOT EXISTS pickup_lat DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE ride_pools ADD COLUMN IF NOT EXISTS pickup_lng DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE ride_pools ADD COLUMN IF NOT EXISTS drop_lat DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE ride_pools ADD COLUMN IF NOT EXISTS drop_lng DOUBLE PRECISION NOT NULL DEFAULT 0.0;

-- 3. Update pool status constraint to include DISPATCHING
ALTER TABLE ride_pools DROP CONSTRAINT IF EXISTS chk_pool_status;
ALTER TABLE ride_pools ADD CONSTRAINT chk_pool_status
    CHECK (status IN ('FORMING', 'DISPATCHING', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'DISSOLVED'));

-- 4. Index for scheduler: find expired/full pools quickly
CREATE INDEX IF NOT EXISTS idx_ride_pools_window ON ride_pools(status, window_expires_at);

-- 5. Index for one-ride-per-user check
CREATE INDEX IF NOT EXISTS idx_ride_requests_passenger_status ON ride_requests(passenger_id, status);

-- =============================================================
-- SEED DATA: 30 Passengers
-- =============================================================
INSERT INTO passengers (id, name, email, phone) VALUES
    -- Frequent flyers
    ('a0000000-0000-0000-0000-000000000001', 'Rahul Sharma',     'rahul.sharma@email.com',     '+91-9876543201'),
    ('a0000000-0000-0000-0000-000000000002', 'Priya Patel',      'priya.patel@email.com',      '+91-9876543202'),
    ('a0000000-0000-0000-0000-000000000003', 'Amit Kumar',       'amit.kumar@email.com',       '+91-9876543203'),
    ('a0000000-0000-0000-0000-000000000004', 'Sneha Reddy',      'sneha.reddy@email.com',      '+91-9876543204'),
    ('a0000000-0000-0000-0000-000000000005', 'Vikram Singh',     'vikram.singh@email.com',     '+91-9876543205'),
    ('a0000000-0000-0000-0000-000000000006', 'Anjali Desai',     'anjali.desai@email.com',     '+91-9876543206'),
    ('a0000000-0000-0000-0000-000000000007', 'Karan Mehta',      'karan.mehta@email.com',      '+91-9876543207'),
    ('a0000000-0000-0000-0000-000000000008', 'Divya Iyer',       'divya.iyer@email.com',       '+91-9876543208'),
    ('a0000000-0000-0000-0000-000000000009', 'Arjun Nair',       'arjun.nair@email.com',       '+91-9876543209'),
    ('a0000000-0000-0000-0000-000000000010', 'Meera Joshi',      'meera.joshi@email.com',      '+91-9876543210'),
    -- Business travellers
    ('a0000000-0000-0000-0000-000000000011', 'Rohan Gupta',      'rohan.gupta@email.com',      '+91-9876543211'),
    ('a0000000-0000-0000-0000-000000000012', 'Neha Kapoor',      'neha.kapoor@email.com',      '+91-9876543212'),
    ('a0000000-0000-0000-0000-000000000013', 'Siddharth Rao',    'siddharth.rao@email.com',    '+91-9876543213'),
    ('a0000000-0000-0000-0000-000000000014', 'Pooja Malhotra',   'pooja.malhotra@email.com',   '+91-9876543214'),
    ('a0000000-0000-0000-0000-000000000015', 'Aditya Verma',     'aditya.verma@email.com',     '+91-9876543215'),
    ('a0000000-0000-0000-0000-000000000016', 'Kavita Sharma',    'kavita.sharma@email.com',    '+91-9876543216'),
    ('a0000000-0000-0000-0000-000000000017', 'Rajesh Pillai',    'rajesh.pillai@email.com',    '+91-9876543217'),
    ('a0000000-0000-0000-0000-000000000018', 'Ananya Bose',      'ananya.bose@email.com',      '+91-9876543218'),
    ('a0000000-0000-0000-0000-000000000019', 'Manish Tiwari',    'manish.tiwari@email.com',    '+91-9876543219'),
    ('a0000000-0000-0000-0000-000000000020', 'Shruti Agarwal',   'shruti.agarwal@email.com',   '+91-9876543220'),
    -- Occasional travellers
    ('a0000000-0000-0000-0000-000000000021', 'Deepak Saxena',    'deepak.saxena@email.com',    '+91-9876543221'),
    ('a0000000-0000-0000-0000-000000000022', 'Ritu Banerjee',    'ritu.banerjee@email.com',    '+91-9876543222'),
    ('a0000000-0000-0000-0000-000000000023', 'Vivek Chauhan',    'vivek.chauhan@email.com',    '+91-9876543223'),
    ('a0000000-0000-0000-0000-000000000024', 'Swati Kulkarni',   'swati.kulkarni@email.com',   '+91-9876543224'),
    ('a0000000-0000-0000-0000-000000000025', 'Nikhil Pandey',    'nikhil.pandey@email.com',    '+91-9876543225'),
    ('a0000000-0000-0000-0000-000000000026', 'Pallavi Sinha',    'pallavi.sinha@email.com',    '+91-9876543226'),
    ('a0000000-0000-0000-0000-000000000027', 'Gaurav Mishra',    'gaurav.mishra@email.com',    '+91-9876543227'),
    ('a0000000-0000-0000-0000-000000000028', 'Tanvi Deshpande',  'tanvi.deshpande@email.com',  '+91-9876543228'),
    ('a0000000-0000-0000-0000-000000000029', 'Harsh Vardhan',    'harsh.vardhan@email.com',    '+91-9876543229'),
    ('a0000000-0000-0000-0000-000000000030', 'Isha Chatterjee',  'isha.chatterjee@email.com',  '+91-9876543230');

-- =============================================================
-- SEED DATA: 15 Cabs — All 4 seats / 4 luggage
-- Positioned around Mumbai Airport T1/T2 and Andheri
-- =============================================================
INSERT INTO cabs (id, license_plate, driver_name, total_seats, luggage_capacity, current_lat, current_lng, status) VALUES
    -- Near Airport Terminal 2 (19.0896, 72.8656)
    ('c0000000-0000-0000-0000-000000000001', 'MH-01-AA-1001', 'Rajesh Kumar',     4, 4, 19.0900, 72.8660, 'AVAILABLE'),
    ('c0000000-0000-0000-0000-000000000002', 'MH-01-AB-1002', 'Suresh Yadav',     4, 4, 19.0880, 72.8640, 'AVAILABLE'),
    ('c0000000-0000-0000-0000-000000000003', 'MH-01-AC-1003', 'Manoj Pawar',      4, 4, 19.0910, 72.8670, 'AVAILABLE'),
    ('c0000000-0000-0000-0000-000000000004', 'MH-01-AD-1004', 'Anil Jadhav',      4, 4, 19.0870, 72.8650, 'AVAILABLE'),
    ('c0000000-0000-0000-0000-000000000005', 'MH-01-AE-1005', 'Deepak Patil',     4, 4, 19.0920, 72.8680, 'AVAILABLE'),
    ('c0000000-0000-0000-0000-000000000006', 'MH-01-AF-1006', 'Ramesh Shirke',    4, 4, 19.0850, 72.8630, 'AVAILABLE'),
    ('c0000000-0000-0000-0000-000000000007', 'MH-01-AG-1007', 'Prakash Gaikwad',  4, 4, 19.0930, 72.8690, 'AVAILABLE'),
    ('c0000000-0000-0000-0000-000000000008', 'MH-01-AH-1008', 'Vinod More',       4, 4, 19.0860, 72.8620, 'AVAILABLE'),
    -- Near Airport Terminal 1 (19.0990, 72.8740)
    ('c0000000-0000-0000-0000-000000000009', 'MH-01-BA-2001', 'Santosh Bhosle',   4, 4, 19.0995, 72.8745, 'AVAILABLE'),
    ('c0000000-0000-0000-0000-000000000010', 'MH-01-BB-2002', 'Ganesh Deshmukh',  4, 4, 19.0985, 72.8735, 'AVAILABLE'),
    ('c0000000-0000-0000-0000-000000000011', 'MH-01-BC-2003', 'Kishor Shinde',    4, 4, 19.1000, 72.8750, 'AVAILABLE'),
    ('c0000000-0000-0000-0000-000000000012', 'MH-01-BD-2004', 'Sanjay Kamble',    4, 4, 19.0975, 72.8725, 'AVAILABLE'),
    -- Near Andheri (19.1197, 72.8464)
    ('c0000000-0000-0000-0000-000000000013', 'MH-01-CA-3001', 'Nilesh Sawant',    4, 4, 19.1200, 72.8470, 'AVAILABLE'),
    ('c0000000-0000-0000-0000-000000000014', 'MH-01-CB-3002', 'Tushar Chavan',    4, 4, 19.1190, 72.8460, 'AVAILABLE'),
    ('c0000000-0000-0000-0000-000000000015', 'MH-01-CC-3003', 'Ashok Mane',       4, 4, 19.1210, 72.8480, 'AVAILABLE');
