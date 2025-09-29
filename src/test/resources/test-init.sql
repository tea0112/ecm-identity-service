-- Test initialization script for Testcontainers
-- This script sets up any additional test-specific database configuration

-- Enable UUID extension if not already enabled
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create any test-specific indexes for performance
CREATE INDEX IF NOT EXISTS idx_test_user_email ON users(email) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_test_audit_timestamp ON audit_events(timestamp);
CREATE INDEX IF NOT EXISTS idx_test_session_user ON user_sessions(user_id) WHERE status = 'ACTIVE';

-- Insert test configuration data if needed
-- (Most test data is created programmatically in test setup methods)
