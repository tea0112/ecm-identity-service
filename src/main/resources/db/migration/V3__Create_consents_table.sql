-- ECM Identity Service - Consents Table Migration
-- Version 3.0 - Add consents table for granular consent management

-- Create consents table
CREATE TABLE consents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    application_id VARCHAR(100) NOT NULL,
    consent_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revocation_reason VARCHAR(500),
    permissions JSONB,
    consent_metadata JSONB,
    granted_permissions_count INTEGER NOT NULL DEFAULT 0,
    denied_permissions_count INTEGER NOT NULL DEFAULT 0,
    consent_version VARCHAR(20),
    privacy_policy_version VARCHAR(20),
    user_agent VARCHAR(500),
    ip_address VARCHAR(45),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- Create indexes for consents
CREATE INDEX idx_consents_user_id ON consents(user_id);
CREATE INDEX idx_consents_tenant_id ON consents(tenant_id);
CREATE INDEX idx_consents_application_id ON consents(application_id);
CREATE INDEX idx_consents_status ON consents(status);
CREATE INDEX idx_consents_granted_at ON consents(granted_at);
CREATE INDEX idx_consents_expires_at ON consents(expires_at);
CREATE INDEX idx_consents_user_application ON consents(user_id, application_id);

-- Add constraint to ensure unique active consent per user-application pair
CREATE UNIQUE INDEX idx_consents_unique_active 
ON consents(user_id, application_id) 
WHERE status = 'ACTIVE' AND deleted_at IS NULL;
