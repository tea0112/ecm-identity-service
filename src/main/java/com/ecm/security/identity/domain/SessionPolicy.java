package com.ecm.security.identity.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Session policy configuration for a tenant.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SessionPolicy {
    
    private int maxSessionDurationMinutes;
    private int maxIdleTimeMinutes;
    private int maxConcurrentSessions;
    private boolean enableSessionTimeout;
    private boolean enableConcurrentSessionControl;
    private boolean enableRememberMe;
    private int rememberMeDurationDays;
    
    /**
     * Get default session policy.
     */
    public static SessionPolicy getDefault() {
        return SessionPolicy.builder()
            .maxSessionDurationMinutes(480) // 8 hours
            .maxIdleTimeMinutes(30)
            .maxConcurrentSessions(3)
            .enableSessionTimeout(true)
            .enableConcurrentSessionControl(true)
            .enableRememberMe(false)
            .rememberMeDurationDays(30)
            .build();
    }
}
