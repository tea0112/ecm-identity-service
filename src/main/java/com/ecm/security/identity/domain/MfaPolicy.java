package com.ecm.security.identity.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Multi-Factor Authentication policy configuration for a tenant.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MfaPolicy {
    
    private boolean requireMfaForAllUsers;
    private boolean requireMfaForAdmins;
    private boolean enableTotpMfa;
    private boolean enableWebAuthnMfa;
    private boolean enableSmsMfa;
    private boolean enableEmailMfa;
    private int mfaGracePeriodDays;
    private int backupCodesCount;
    
    /**
     * Get default MFA policy.
     */
    public static MfaPolicy getDefault() {
        return MfaPolicy.builder()
            .requireMfaForAllUsers(false)
            .requireMfaForAdmins(true)
            .enableTotpMfa(true)
            .enableWebAuthnMfa(true)
            .enableSmsMfa(false)
            .enableEmailMfa(false)
            .mfaGracePeriodDays(7)
            .backupCodesCount(10)
            .build();
    }
}
