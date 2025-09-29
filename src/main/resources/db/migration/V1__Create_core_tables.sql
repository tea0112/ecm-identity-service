-- ECM Identity Service - Core Tables Migration
-- Version 1.0 - Initial schema creation

-- Enable UUID extension for PostgreSQL
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create tenants table
CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    domain VARCHAR(255) UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    subscription_tier VARCHAR(50),
    max_users INTEGER,
    settings JSONB,
    crypto_key_id VARCHAR(255),
    backup_rpo_minutes INTEGER DEFAULT 5,
    backup_rto_minutes INTEGER DEFAULT 60,
    suspended_at TIMESTAMP WITH TIME ZONE,
    suspension_reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- Create indexes for tenants
CREATE INDEX idx_tenants_status ON tenants(status);
CREATE INDEX idx_tenants_domain ON tenants(domain);

-- Create users table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    username VARCHAR(100),
    email VARCHAR(255) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    phone_number VARCHAR(20),
    phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
    password_hash VARCHAR(255),
    password_salt VARCHAR(255),
    password_algorithm VARCHAR(50) DEFAULT 'ARGON2',
    password_changed_at TIMESTAMP WITH TIME ZONE,
    password_expires_at TIMESTAMP WITH TIME ZONE,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    display_name VARCHAR(200),
    date_of_birth DATE,
    profile_picture_url VARCHAR(500),
    locale VARCHAR(10) DEFAULT 'en_US',
    timezone VARCHAR(50) DEFAULT 'UTC',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_backup_codes TEXT[],
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    last_login_at TIMESTAMP WITH TIME ZONE,
    last_login_ip VARCHAR(45),
    last_activity_at TIMESTAMP WITH TIME ZONE,
    terms_accepted_at TIMESTAMP WITH TIME ZONE,
    terms_version VARCHAR(50),
    privacy_policy_accepted_at TIMESTAMP WITH TIME ZONE,
    privacy_policy_version VARCHAR(50),
    marketing_consent BOOLEAN NOT NULL DEFAULT FALSE,
    is_minor BOOLEAN NOT NULL DEFAULT FALSE,
    parental_consent_at TIMESTAMP WITH TIME ZONE,
    parent_email VARCHAR(255),
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(tenant_id, email),
    UNIQUE(tenant_id, username)
);

-- Create indexes for users
CREATE INDEX idx_users_tenant_email ON users(tenant_id, email);
CREATE INDEX idx_users_tenant_username ON users(tenant_id, username);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_last_login ON users(last_login_at);

-- Create user_devices table
CREATE TABLE user_devices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_fingerprint VARCHAR(500) NOT NULL,
    device_name VARCHAR(200),
    device_type VARCHAR(50),
    operating_system VARCHAR(100),
    browser VARCHAR(100),
    browser_version VARCHAR(50),
    platform VARCHAR(50),
    screen_resolution VARCHAR(20),
    timezone VARCHAR(50),
    language VARCHAR(10),
    user_agent VARCHAR(1000),
    ip_address VARCHAR(45),
    is_trusted BOOLEAN NOT NULL DEFAULT FALSE,
    trust_score DECIMAL(5,2) DEFAULT 0.0,
    first_seen_at TIMESTAMP WITH TIME ZONE,
    last_seen_at TIMESTAMP WITH TIME ZONE,
    total_sessions INTEGER NOT NULL DEFAULT 0,
    successful_authentications INTEGER NOT NULL DEFAULT 0,
    failed_authentications INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
    attestation_supported BOOLEAN NOT NULL DEFAULT FALSE,
    attestation_verified BOOLEAN NOT NULL DEFAULT FALSE,
    attestation_data TEXT,
    tpm_present BOOLEAN NOT NULL DEFAULT FALSE,
    secure_element_present BOOLEAN NOT NULL DEFAULT FALSE,
    jailbroken_rooted BOOLEAN NOT NULL DEFAULT FALSE,
    emulator_detected BOOLEAN NOT NULL DEFAULT FALSE,
    vpn_detected BOOLEAN NOT NULL DEFAULT FALSE,
    tor_detected BOOLEAN NOT NULL DEFAULT FALSE,
    geolocation_country VARCHAR(2),
    geolocation_city VARCHAR(100),
    push_token VARCHAR(500),
    push_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    blocked_at TIMESTAMP WITH TIME ZONE,
    blocked_reason VARCHAR(500),
    device_metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- Create indexes for user_devices
CREATE INDEX idx_devices_user_trusted ON user_devices(user_id, is_trusted);
CREATE INDEX idx_devices_fingerprint ON user_devices(device_fingerprint);
CREATE INDEX idx_devices_last_seen ON user_devices(last_seen_at);

-- Create user_sessions table
CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id UUID REFERENCES user_devices(id) ON DELETE SET NULL,
    session_id VARCHAR(255) NOT NULL UNIQUE,
    refresh_token_hash VARCHAR(255) NOT NULL,
    refresh_token_family VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_activity_at TIMESTAMP WITH TIME ZONE,
    ip_address VARCHAR(45),
    user_agent VARCHAR(1000),
    location_country VARCHAR(2),
    location_city VARCHAR(100),
    location_latitude DECIMAL(10,8),
    location_longitude DECIMAL(11,8),
    authentication_method VARCHAR(30) NOT NULL,
    mfa_completed BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_methods_used TEXT[],
    step_up_completed BOOLEAN NOT NULL DEFAULT FALSE,
    step_up_required_for TEXT[],
    risk_level VARCHAR(20) NOT NULL DEFAULT 'LOW',
    risk_score DECIMAL(5,2) DEFAULT 0.0,
    risk_factors TEXT[],
    impossible_travel_detected BOOLEAN NOT NULL DEFAULT FALSE,
    device_fingerprint_changed BOOLEAN NOT NULL DEFAULT FALSE,
    terminated_at TIMESTAMP WITH TIME ZONE,
    termination_reason VARCHAR(500),
    client_app_id VARCHAR(100),
    scopes TEXT[],
    consent_given BOOLEAN NOT NULL DEFAULT FALSE,
    consent_scopes TEXT[],
    session_metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- Create indexes for user_sessions
CREATE INDEX idx_sessions_user_active ON user_sessions(user_id, status);
CREATE INDEX idx_sessions_device ON user_sessions(device_id);
CREATE INDEX idx_sessions_expires_at ON user_sessions(expires_at);
CREATE INDEX idx_sessions_last_activity ON user_sessions(last_activity_at);

-- Create user_credentials table
CREATE TABLE user_credentials (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    credential_type VARCHAR(30) NOT NULL,
    credential_identifier VARCHAR(500) NOT NULL,
    credential_data TEXT,
    credential_name VARCHAR(200),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    last_used_at TIMESTAMP WITH TIME ZONE,
    usage_count INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_by_device_id VARCHAR(255),
    backup_eligible BOOLEAN NOT NULL DEFAULT FALSE,
    backup_state VARCHAR(50),
    webauthn_credential_id VARCHAR(500),
    webauthn_public_key TEXT,
    webauthn_signature_count BIGINT DEFAULT 0,
    webauthn_aaguid VARCHAR(100),
    webauthn_attestation_type VARCHAR(50),
    webauthn_transports TEXT[],
    totp_secret VARCHAR(500),
    totp_algorithm VARCHAR(20) DEFAULT 'SHA1',
    totp_digits INTEGER DEFAULT 6,
    totp_period INTEGER DEFAULT 30,
    totp_qr_code_url VARCHAR(1000),
    recovery_code_hash VARCHAR(255),
    recovery_code_used BOOLEAN NOT NULL DEFAULT FALSE,
    delivery_target VARCHAR(255),
    verification_code VARCHAR(20),
    verification_attempts INTEGER NOT NULL DEFAULT 0,
    max_verification_attempts INTEGER NOT NULL DEFAULT 3,
    blocked_until TIMESTAMP WITH TIME ZONE,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- Create indexes for user_credentials
CREATE INDEX idx_credentials_user_type ON user_credentials(user_id, credential_type);
CREATE INDEX idx_credentials_identifier ON user_credentials(credential_identifier);
CREATE INDEX idx_credentials_status ON user_credentials(status);

-- Create tenant_policies table
CREATE TABLE tenant_policies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    policy_type VARCHAR(30) NOT NULL,
    effect VARCHAR(20) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 1000,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    policy_document JSONB,
    rego_policy TEXT,
    subjects TEXT[],
    resources TEXT[],
    actions TEXT[],
    conditions JSONB,
    time_restrictions JSONB,
    ip_restrictions TEXT[],
    device_restrictions JSONB,
    risk_level_max VARCHAR(20),
    mfa_required BOOLEAN NOT NULL DEFAULT FALSE,
    step_up_required BOOLEAN NOT NULL DEFAULT FALSE,
    consent_required BOOLEAN NOT NULL DEFAULT FALSE,
    audit_level VARCHAR(20) DEFAULT 'STANDARD',
    cache_ttl_seconds INTEGER DEFAULT 300,
    break_glass_eligible BOOLEAN NOT NULL DEFAULT FALSE,
    emergency_override BOOLEAN NOT NULL DEFAULT FALSE,
    tags TEXT[],
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- Create indexes for tenant_policies
CREATE INDEX idx_policies_tenant_type ON tenant_policies(tenant_id, policy_type);
CREATE INDEX idx_policies_status ON tenant_policies(status);
CREATE INDEX idx_policies_priority ON tenant_policies(priority);

-- Create user_roles table
CREATE TABLE user_roles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_name VARCHAR(100) NOT NULL,
    scope VARCHAR(255),
    assignment_type VARCHAR(20) NOT NULL DEFAULT 'PERMANENT',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    granted_by_user_id VARCHAR(255),
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoked_by_user_id VARCHAR(255),
    revocation_reason VARCHAR(500),
    justification VARCHAR(1000),
    approval_required BOOLEAN NOT NULL DEFAULT FALSE,
    approved_by_user_id VARCHAR(255),
    approved_at TIMESTAMP WITH TIME ZONE,
    approval_workflow_id VARCHAR(255),
    delegated_from_user_id VARCHAR(255),
    delegation_depth INTEGER NOT NULL DEFAULT 0,
    max_delegation_depth INTEGER NOT NULL DEFAULT 0,
    delegation_restrictions JSONB,
    conditions JSONB,
    time_restrictions JSONB,
    ip_restrictions TEXT[],
    device_restrictions JSONB,
    mfa_required BOOLEAN NOT NULL DEFAULT FALSE,
    step_up_required BOOLEAN NOT NULL DEFAULT FALSE,
    break_glass_role BOOLEAN NOT NULL DEFAULT FALSE,
    emergency_access BOOLEAN NOT NULL DEFAULT FALSE,
    emergency_justification VARCHAR(1000),
    emergency_approved_by VARCHAR(255),
    usage_count INTEGER NOT NULL DEFAULT 0,
    max_usage_count INTEGER,
    cooldown_period_hours INTEGER,
    last_cooldown_started TIMESTAMP WITH TIME ZONE,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(user_id, role_name, scope)
);

-- Create indexes for user_roles
CREATE INDEX idx_user_roles_user ON user_roles(user_id);
CREATE INDEX idx_user_roles_role ON user_roles(role_name);
CREATE INDEX idx_user_roles_expires ON user_roles(expires_at);
CREATE INDEX idx_user_roles_status ON user_roles(status);

-- Create linked_identities table
CREATE TABLE linked_identities (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(100) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    external_username VARCHAR(255),
    external_email VARCHAR(255),
    display_name VARCHAR(200),
    profile_url VARCHAR(500),
    avatar_url VARCHAR(500),
    identity_type VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    linked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP WITH TIME ZONE,
    login_count INTEGER NOT NULL DEFAULT 0,
    access_token_hash VARCHAR(255),
    refresh_token_hash VARCHAR(255),
    token_expires_at TIMESTAMP WITH TIME ZONE,
    id_token_hash VARCHAR(255),
    scopes TEXT[],
    claims JSONB,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    verification_method VARCHAR(50),
    verification_at TIMESTAMP WITH TIME ZONE,
    merge_eligible BOOLEAN NOT NULL DEFAULT TRUE,
    merged_from_user_id VARCHAR(255),
    merge_operation_id VARCHAR(255),
    merge_reversible BOOLEAN NOT NULL DEFAULT TRUE,
    merge_expires_at TIMESTAMP WITH TIME ZONE,
    unlinked_at TIMESTAMP WITH TIME ZONE,
    unlinked_by_user_id VARCHAR(255),
    unlink_reason VARCHAR(500),
    can_relink BOOLEAN NOT NULL DEFAULT TRUE,
    data_sharing_consent BOOLEAN NOT NULL DEFAULT FALSE,
    consent_given_at TIMESTAMP WITH TIME ZONE,
    consent_withdrawn_at TIMESTAMP WITH TIME ZONE,
    profile_sync_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_profile_sync TIMESTAMP WITH TIME ZONE,
    risk_score DECIMAL(5,2) DEFAULT 0.0,
    suspicious_activity BOOLEAN NOT NULL DEFAULT FALSE,
    fraud_indicators TEXT[],
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(provider, external_id)
);

-- Create indexes for linked_identities
CREATE INDEX idx_linked_identities_user ON linked_identities(user_id);
CREATE INDEX idx_linked_identities_provider ON linked_identities(provider);
CREATE INDEX idx_linked_identities_status ON linked_identities(status);

-- Create session_activities table
CREATE TABLE session_activities (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id UUID NOT NULL REFERENCES user_sessions(id) ON DELETE CASCADE,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activity_type VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    resource VARCHAR(255),
    action VARCHAR(100),
    ip_address VARCHAR(45),
    user_agent VARCHAR(1000),
    location_changed BOOLEAN NOT NULL DEFAULT FALSE,
    device_fingerprint_changed BOOLEAN NOT NULL DEFAULT FALSE,
    risk_score DECIMAL(5,2) DEFAULT 0.0,
    risk_factors TEXT[],
    anomaly_detected BOOLEAN NOT NULL DEFAULT FALSE,
    anomaly_type VARCHAR(100),
    response_time_ms BIGINT,
    bytes_transferred BIGINT,
    status_code INTEGER,
    error_message VARCHAR(500),
    additional_data JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- Create indexes for session_activities
CREATE INDEX idx_session_activities_session ON session_activities(session_id);
CREATE INDEX idx_session_activities_timestamp ON session_activities(timestamp);
CREATE INDEX idx_session_activities_activity_type ON session_activities(activity_type);
CREATE INDEX idx_session_activities_risk ON session_activities(risk_score);

-- Create audit_events table
CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tenant_id UUID,
    user_id UUID,
    session_id VARCHAR(255),
    event_type VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    actor_id UUID,
    actor_type VARCHAR(50),
    target_id UUID,
    target_type VARCHAR(50),
    resource VARCHAR(255),
    action VARCHAR(100),
    outcome VARCHAR(20),
    ip_address VARCHAR(45),
    user_agent VARCHAR(1000),
    location_country VARCHAR(2),
    location_city VARCHAR(100),
    description VARCHAR(1000),
    details JSONB,
    risk_score DECIMAL(5,2),
    risk_factors TEXT[],
    compliance_flags TEXT[],
    retention_until TIMESTAMP WITH TIME ZONE,
    legal_hold BOOLEAN NOT NULL DEFAULT FALSE,
    pii_redacted BOOLEAN NOT NULL DEFAULT FALSE,
    redacted_fields TEXT[],
    event_hash VARCHAR(64),
    previous_event_hash VARCHAR(64),
    chain_sequence BIGINT,
    signature VARCHAR(500),
    signing_key_id VARCHAR(100),
    correlation_id VARCHAR(255),
    trace_id VARCHAR(255),
    span_id VARCHAR(255),
    application_version VARCHAR(50),
    environment VARCHAR(20)
);

-- Create indexes for audit_events
CREATE INDEX idx_audit_timestamp ON audit_events(timestamp);
CREATE INDEX idx_audit_tenant_user ON audit_events(tenant_id, user_id);
CREATE INDEX idx_audit_event_type ON audit_events(event_type);
CREATE INDEX idx_audit_session ON audit_events(session_id);
CREATE INDEX idx_audit_severity ON audit_events(severity);
CREATE INDEX idx_audit_chain ON audit_events(previous_event_hash);

-- Create trigger functions for updated_at timestamps
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers for updated_at on all tables
CREATE TRIGGER update_tenants_updated_at BEFORE UPDATE ON tenants 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_devices_updated_at BEFORE UPDATE ON user_devices 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_sessions_updated_at BEFORE UPDATE ON user_sessions 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_credentials_updated_at BEFORE UPDATE ON user_credentials 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_tenant_policies_updated_at BEFORE UPDATE ON tenant_policies 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_roles_updated_at BEFORE UPDATE ON user_roles 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_linked_identities_updated_at BEFORE UPDATE ON linked_identities 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_session_activities_updated_at BEFORE UPDATE ON session_activities 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
