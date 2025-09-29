package com.ecm.security.identity.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Password policy configuration for a tenant.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PasswordPolicy {
    
    private int minLength;
    private int maxLength;
    private boolean requireUppercase;
    private boolean requireLowercase;
    private boolean requireNumbers;
    private boolean requireSpecialChars;
    private int maxAttempts;
    private int lockoutDurationMinutes;
    private int passwordHistorySize;
    private int passwordExpirationDays;
    
    /**
     * Get default password policy.
     */
    public static PasswordPolicy getDefault() {
        return PasswordPolicy.builder()
            .minLength(8)
            .maxLength(128)
            .requireUppercase(true)
            .requireLowercase(true)
            .requireNumbers(true)
            .requireSpecialChars(true)
            .maxAttempts(5)
            .lockoutDurationMinutes(15)
            .passwordHistorySize(5)
            .passwordExpirationDays(90)
            .build();
    }
}
