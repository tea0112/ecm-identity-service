package com.ecm.security.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Main application class for the ECM Identity Service.
 * 
 * This service provides comprehensive Identity and Access Management (IAM) capabilities including:
 * - Multi-tenant user authentication and authorization
 * - OAuth2/OIDC authorization server
 * - WebAuthn/FIDO2 passwordless authentication
 * - Multi-factor authentication (MFA)
 * - SAML 2.0 and enterprise federation
 * - SCIM protocol support for user provisioning
 * - Risk-based authentication and session management
 * - Comprehensive audit logging with cryptographic chaining
 * - Policy-based access control (ABAC/ReBAC)
 * - Admin console and user self-service capabilities
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
@EnableTransactionManagement
public class EcmIdentityServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(EcmIdentityServiceApplication.class, args);
	}

}
