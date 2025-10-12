***

## **Final IAM Service Specification**

### **Functional Requirements (FR)**

#### **1. Authentication & Sessions**
* **FR1.1 – Credential Login & Passwordless:** Support authentication via password, WebAuthn/passkeys, and magic links.
* **FR1.2 – Token Issuance & Refresh:** Generate and manage short-lived access tokens and long-lived, rotating refresh tokens.
* **FR1.3 – Multi-Factor & Step-Up Authentication:** Support MFA (TOTP, WebAuthn) and trigger policy-driven step-up authentication for high-risk operations.
* **FR1.4 – Device & Session Management:** Allow users to view and revoke their active sessions. Session data must be bound to device signals (IP, platform, etc.) and support optional binding to attested client integrity signals (e.g., TPM, mobile device attestation).
* **FR1.5 – Secure Account Recovery & Fallbacks:** Provide secure, rate-limited account recovery flows and passwordless fallbacks. The system must also support tenant-configurable, high-assurance out-of-band recovery channels (e.g., hardware tokens, notarized approval).
* **FR1.6 – Account Linking & Merging:** Allow a single user to link multiple identities (e.g., social, enterprise, email) with a full audit trail and reversible merges.
* **FR1.7 – Age Gates & Parental Consent:** Implement workflows for handling child accounts, including age verification and parental consent mechanisms as required by regulations like COPPA.
* **FR1.8 – Proactive Session Invalidation based on Risk Signals:** The system must automatically invalidate sessions and require re-authentication upon detection of high-risk signals, such as impossible travel, anomalous device fingerprint changes, or credential leak exposure.

#### **2. Identity, Federation & Protocols**
* **FR2.1 – User & Identity Lifecycle:** Manage user registration, profile updates, and secure password/credential storage (Argon2). This must include:
    * **Instantaneous De-provisioning:** Offboarding a user must immediately terminate all active sessions and tokens, propagating revocation across distributed caches to prevent race conditions.
    * **Account Resurrection Prevention:** Re-registration with a previously deleted user's identifier must not restore any old permissions, ownerships, or roles.
* **FR2.2 – OAuth2/OIDC Provider:** Function as a compliant authorization server, enforcing strict Redirect URI allowlists and PKCE for all public clients.
* **FR2.3 – Enterprise Federation (SSO):** Support SAML 2.0 and OIDC for SSO, including Just-In-Time (JIT) provisioning.
* **FR2.4 – Directory Sync (SCIM):** Support the SCIM protocol for automated user provisioning and lifecycle synchronization.
* **FR2.5 – Device & CLI Authentication:** Support the `device_code` flow for headless clients and command-line applications.
* **FR2.6 – Service & Non-Human Principals:** Manage the full lifecycle (approval workflows, rotation, expiration) of non-human identities (service accounts) and support for identity-bound mTLS/SPIFFE.

#### **3. Authorization & Access Control**
* **FR3.1 – Policy Engine (ABAC/ReBAC):** Utilize a central policy engine for fine-grained authorization based on attributes (ABAC) and relationships (ReBAC). The engine must have clear precedence rules (e.g., an explicit deny always overrides an allow).
* **FR3.2 – Contextual Authorization API:** Provide a secure API to answer complex authorization questions, handling batch decisions and protecting against Time-of-Check to Time-of-Use (TOCTOU) race conditions.
* **FR3.3 – Continuous Authorization for Long-Lived Connections:** Support the revocation of permissions for any long-lived connection (e.g., WebSockets, gRPC streams, background jobs), requiring clients to re-validate mid-session.
* **FR3.4 – Time-Bound, JIT & Emergency Access:** Support temporary access grants, approval workflows, and formally defined **break-glass accounts**. Break-glass access must require special procedures like dual-approval and generate high-priority, detailed audit events.
* **FR3.5 – Advanced Delegation & Scoped Administration:** Allow a user to securely delegate a subset of their permissions to another user. This must support the creation of **scoped administrators** (e.g., a "Project Admin" who cannot escalate privileges globally) and complex workflows like approval chains and partial policy delegation.
* **FR3.6 – Granular Consent Management:** Allow users to grant granular consent to applications on a per-resource or per-action basis (e.g., "Allow app to read contacts"), with a clear interface for tracking and revoking consent.

#### **4. Multi-Tenancy**
* **FR4.1 – Tenant Isolation & Configuration:** Enforce strong data isolation between tenants and allow for per-tenant security policies. This includes optional tenant-scoped cryptographic keys, which must adhere to tenant-specific RPO/RTO guarantees for backup and recovery.
* **FR4.2 – Tenant Lifecycle Management:** Support complex operations like splitting and merging organizations, ensuring all user permissions and relationships are correctly remapped without data leakage.
* **FR4.3 – Cross-Tenant Collaboration:** Securely support guest users, sharing, and third-party application marketplaces with explicit consent and scoped access.

#### **5. Admin & User Experience**
* **FR5.1 – Admin Console:** A UI for managing users, policies, clients, and viewing audit logs.
* **FR5.2 – Admin Impersonation ("Login as User"):** Provide a "login as user" feature that requires justification, is time-boxed, heavily audited, and clearly indicated in the user's UI.
* **FR5.3 – Secure User Self-Service:** Allow users to manage their credentials, sessions, linked accounts, and request data exports or account deletion. Self-service data exports must:
    * Produce cryptographically signed archives to ensure data integrity.
    * Support optional selective field exclusion for PII minimization, which can be enforced as a default policy at the tenant level.
* **FR5.4 – User Security Timeline:** Provide a user-facing, easy-to-understand timeline of their own security-sensitive account activity (e.g., logins from new devices, password changes, MFA enrollments).

***

### **Non-Functional Requirements (NFR)**

#### **1. Security Hardening**
* **NFR1.1 – Threat Mitigation:** Actively defend against common attacks including credential stuffing, MFA fatigue, and replay attacks on all single-use tokens (e.g., recovery links, magic links, device codes) via strict, short-lived nonces.
* **NFR1.2 – Advanced Token & Session Hygiene:** Enforce strict JWT security (rotated keys in HSM/KMS), rotating refresh tokens with family revocation on reuse, and secure cookie policies.
* **NFR1.3 – Rate Limiting & Lockout Protection:** Implement intelligent throttling and account lockouts to mitigate brute-force attacks, while preventing malicious actors from intentionally locking out legitimate users.
* **NFR1.4 – Secrets & Supply-Chain Security:** Utilize a central vault for all secrets and maintain a secure software supply chain (SBOM, image signing, etc.).
* **NFR1.5 – Backup & Restore Token Safety:** Ensure that upon a database restore, previously issued refresh tokens ("zombie tokens") are invalidated.
* **NFR1.6 – Key Compromise Readiness:** Maintain a documented runbook and automated drills for handling a compromised signing key, including rapid rotation and global token invalidation.
* **NFR1.7 – Token Audience & Scope Enforcement:** Access tokens must be bound to a specific **audience (`aud`) claim** and validated by recipient services to prevent "confused deputy" attacks. Scopes granted must be strictly enforced.
* **NFR1.8 – Cryptographic Key Lifecycle Policy:** All cryptographic signing keys must be rotated on a defined cadence (e.g., quarterly or on-demand). Rotations must employ a dual-signing overlap period to ensure zero downtime.
* **NFR1.9 – Cryptographic Agility:** The system architecture must support upgrading cryptographic algorithms (e.g., for hashing, signing) to newer standards, including post-quantum readiness, without incurring system downtime.

#### **2. Observability & Compliance**
* **NFR2.1 – Audit Log Immutability & Integrity:** Generate cryptographically chained audit logs that are tamper-evident, with support for field-level redaction of PII. The system must provide an **SLA** guaranteeing logs are write-once, replicated across regions, and backed by a cryptographic proof-of-retention.
* **NFR2.2 – Consent & ToS Versioning:** Persist the specific version of Terms of Service or privacy policies that each user has consented to and trigger re-consent flows when required.
* **NFR2.3 – Privacy & Data Rights Management:** Implement procedures for "Right to Erasure" that coexist with "Legal Hold" requirements.
* **NFR2.4 – Jurisdiction-Aware Policies:** The system must be capable of automatically enforcing different policy sets based on the user's jurisdiction to comply with regulations like GDPR, CCPA, and PDPA.
* **NFR2.5 – Forensic Timelines:** Audit logs must be structured and indexed to be efficiently queryable for incident response, allowing security teams to construct a forensic timeline of events for any user or entity.

#### **3. Reliability & Scalability**
* **NFR3.1 – High Availability & Performance:** Achieve **99.95%** uptime with sub-100ms 95th percentile latency for core authorization checks.
* **NFR3.2 – Zero-Downtime Operations:** Support zero-downtime deployments, migrations, and cryptographic key rotations.
* **NFR3.3 – Resiliency in Degraded States:** Handle regional partitions and outages gracefully via mechanisms like cached validations and pessimistic denies.
* **NFR3.4 – Policy Evaluation, Dry-Runs & Explainability:** Support the ability to simulate policy changes against production traffic ("shadow" mode) to validate their impact. The authorization engine must be able to provide an explanation for any decision.
* **NFR3.5 – Disaster Recovery (RPO/RTO):** The service must meet a **Recovery Point Objective (RPO) of 5 minutes** and a **Recovery Time Objective (RTO) of 1 hour** for the primary identity database.
* **NFR3.6 – Revocation Propagation SLA:** Any permission, role, or session revocation must be enforced globally across the distributed system within a defined timeframe of **under 1 second**.

***

### **Key Acceptance & Test Scenarios**
* **Instant De-provisioning:** When a user is deleted, an automated test must immediately attempt to use their previously active session/token against a separate service and verify it is rejected within 1 second.
* **Admin Impersonation Flow:** An admin initiating a "login as user" session must be prompted for a justification, and the resulting session must display a persistent banner to the admin and send a notification email to the user.
* **Break-Glass Account Access:** A test where an operator attempts to use a break-glass account must trigger a multi-person approval workflow and generate a high-severity alert in the SIEM upon successful activation.
* **Key Compromise Drill:** A test scenario where a signing key is marked as compromised must trigger an automated workflow that rotates the key, issues tokens with a new key ID (`kid`), and ensures services begin rejecting tokens signed with the old key.
* **Emergency Policy Rollback:** An automated test must verify that an accidentally deployed, overly-permissive authorization policy can be instantly rolled back to the last known-good version within 5 minutes, with logs showing the rollback event.