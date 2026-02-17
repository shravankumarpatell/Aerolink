-- Clean up test data (preserves seed data structure but resets ride state)
DELETE FROM pricing_records;
DELETE FROM ride_requests;
DELETE FROM ride_pools;
UPDATE cabs SET status = 'AVAILABLE';
