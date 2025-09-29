-- ECM Identity Service - Default Data Migration
-- Version 2.0 - Insert default tenant and admin user

-- Insert default tenant
INSERT INTO tenants (
    tenant_code,
    name,
    description,
    domain,
    status,
    subscription_tier,
    max_users,
    settings
) VALUES (
    'default',
    'Default Tenant',
    'Default tenant for the ECM Identity Service',
    'localhost',
    'ACTIVE',
    'ENTERPRISE',
    10000,
    '{
        "passwordPolicy": {
            "minLength": 8,
            "requireUppercase": true,
            "requireLowercase": true,
            "requireNumbers": true,
            "requireSpecialChars": true,
            "maxAge": 90,
            "preventReuse": 5
        },
        "sessionPolicy": {
            "maxSessions": 5,
            "sessionTimeout": 900,
            "extendedSessionTimeout": 28800
        },
        "mfaPolicy": {
            "required": false,
            "allowedMethods": ["TOTP", "WEBAUTHN", "SMS", "EMAIL"],
            "gracePeriod": 7
        },
        "riskPolicy": {
            "enabled": true,
            "maxRiskScore": 70.0,
            "impossibleTravelThreshold": 500
        }
    }'
);

-- Get the default tenant ID for foreign key references
DO $$ 
DECLARE 
    default_tenant_id UUID;
    admin_user_id UUID;
BEGIN
    -- Get default tenant ID
    SELECT id INTO default_tenant_id FROM tenants WHERE tenant_code = 'default';
    
    -- Insert default admin user
    INSERT INTO users (
        tenant_id,
        username,
        email,
        email_verified,
        first_name,
        last_name,
        display_name,
        status,
        password_hash,
        password_algorithm,
        password_changed_at,
        terms_accepted_at,
        terms_version,
        privacy_policy_accepted_at,
        privacy_policy_version
    ) VALUES (
        default_tenant_id,
        'admin',
        'admin@ecm.local',
        true,
        'System',
        'Administrator',
        'System Administrator',
        'ACTIVE',
        '$argon2id$v=19$m=65536,t=3,p=4$sF8Q8z7n3P+v9Q7n3P+v9Q$K7n3P+v9Q7n3P+v9Q7n3P+v9Q7n3P+v9Q7n3P+v9Q', -- Password: admin123!
        'ARGON2',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        '1.0',
        CURRENT_TIMESTAMP,
        '1.0'
    ) RETURNING id INTO admin_user_id;
    
    -- Insert admin role
    INSERT INTO user_roles (
        user_id,
        role_name,
        scope,
        assignment_type,
        status,
        granted_by_user_id,
        granted_at
    ) VALUES (
        admin_user_id,
        'SYSTEM_ADMIN',
        'global',
        'PERMANENT',
        'ACTIVE',
        admin_user_id::VARCHAR,
        CURRENT_TIMESTAMP
    );
    
    -- Insert default security policies
    INSERT INTO tenant_policies (
        tenant_id,
        name,
        description,
        policy_type,
        effect,
        priority,
        status,
        policy_document,
        subjects,
        resources,
        actions
    ) VALUES 
    (
        default_tenant_id,
        'Default Authentication Policy',
        'Basic authentication requirements for all users',
        'AUTHENTICATION',
        'ALLOW',
        1000,
        'ACTIVE',
        '{
            "rules": [
                {
                    "condition": "user.status == ''ACTIVE''",
                    "effect": "ALLOW"
                },
                {
                    "condition": "user.locked_until > now()",
                    "effect": "DENY"
                }
            ]
        }',
        ARRAY['*'],
        ARRAY['*'],
        ARRAY['authenticate']
    ),
    (
        default_tenant_id,
        'Admin Access Policy',
        'Restrict admin access to authorized users',
        'AUTHORIZATION',
        'ALLOW',
        100,
        'ACTIVE',
        '{
            "rules": [
                {
                    "condition": "''SYSTEM_ADMIN'' in user.roles or ''ADMIN'' in user.roles",
                    "effect": "ALLOW"
                }
            ]
        }',
        ARRAY['role:SYSTEM_ADMIN', 'role:ADMIN'],
        ARRAY['admin:*'],
        ARRAY['*']
    ),
    (
        default_tenant_id,
        'High Risk Activity Policy',
        'Require step-up authentication for high-risk activities',
        'SECURITY_BASELINE',
        'ALLOW',
        500,
        'ACTIVE',
        '{
            "rules": [
                {
                    "condition": "session.risk_score > 70.0",
                    "effect": "DENY",
                    "requirements": ["step_up_auth"]
                }
            ]
        }',
        ARRAY['*'],
        ARRAY['sensitive:*', 'admin:*'],
        ARRAY['delete', 'update', 'export']
    ),
    (
        default_tenant_id,
        'Deny Policy Override',
        'Explicit deny takes precedence over allow',
        'AUTHORIZATION',
        'DENY',
        1,
        'ACTIVE',
        '{
            "rules": [
                {
                    "condition": "user.status == ''SUSPENDED'' or user.status == ''DEACTIVATED''",
                    "effect": "DENY"
                }
            ]
        }',
        ARRAY['*'],
        ARRAY['*'],
        ARRAY['*']
    );
    
END $$;

-- Insert audit event for initial setup
INSERT INTO audit_events (
    tenant_id,
    event_type,
    severity,
    actor_type,
    description,
    outcome,
    details
) VALUES (
    (SELECT id FROM tenants WHERE tenant_code = 'default'),
    'system.initialization',
    'INFO',
    'SYSTEM',
    'ECM Identity Service initialized with default tenant and admin user',
    'SUCCESS',
    '{
        "version": "1.0.0",
        "defaultTenant": "default",
        "adminUser": "admin@ecm.local"
    }'
);

-- Create database views for common queries
CREATE VIEW active_users AS
SELECT 
    u.id,
    u.tenant_id,
    u.username,
    u.email,
    u.first_name,
    u.last_name,
    u.display_name,
    u.status,
    u.last_login_at,
    u.mfa_enabled,
    t.tenant_code,
    t.name as tenant_name
FROM users u
JOIN tenants t ON u.tenant_id = t.id
WHERE u.status = 'ACTIVE' 
  AND u.deleted_at IS NULL
  AND t.status = 'ACTIVE'
  AND t.deleted_at IS NULL;

CREATE VIEW user_session_summary AS
SELECT 
    u.id as user_id,
    u.email,
    u.tenant_id,
    COUNT(s.id) as active_sessions,
    MAX(s.last_activity_at) as last_activity,
    ARRAY_AGG(DISTINCT s.authentication_method) as auth_methods_used,
    AVG(s.risk_score) as avg_risk_score
FROM users u
LEFT JOIN user_sessions s ON u.id = s.user_id 
    AND s.status = 'ACTIVE' 
    AND s.expires_at > CURRENT_TIMESTAMP
WHERE u.deleted_at IS NULL
GROUP BY u.id, u.email, u.tenant_id;

CREATE VIEW security_events_summary AS
SELECT 
    DATE_TRUNC('hour', timestamp) as event_hour,
    tenant_id,
    event_type,
    severity,
    COUNT(*) as event_count,
    COUNT(DISTINCT user_id) as affected_users
FROM audit_events
WHERE timestamp >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
  AND severity IN ('ERROR', 'CRITICAL', 'SECURITY_INCIDENT')
GROUP BY DATE_TRUNC('hour', timestamp), tenant_id, event_type, severity
ORDER BY event_hour DESC;
