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

-- Insert default tenant for tests
INSERT INTO tenants (id, name, domain, status, created_at, updated_at) 
VALUES (
    '550e8400-e29b-41d4-a716-446655440000'::uuid,
    'Test Tenant',
    'test.example.com',
    'ACTIVE',
    NOW(),
    NOW()
) ON CONFLICT (id) DO NOTHING;

-- Insert test user
INSERT INTO users (
    id, tenant_id, email, password_hash, first_name, last_name, 
    status, email_verified, mfa_enabled, created_at, updated_at
) VALUES (
    '550e8400-e29b-41d4-a716-446655440001'::uuid,
    '550e8400-e29b-41d4-a716-446655440000'::uuid,
    'test@example.com',
    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDi', -- password: "password"
    'Test',
    'User',
    'ACTIVE',
    true,
    false,
    NOW(),
    NOW()
) ON CONFLICT (tenant_id, email) DO NOTHING;
