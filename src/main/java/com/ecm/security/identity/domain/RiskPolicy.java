package com.ecm.security.identity.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Risk assessment policy configuration for a tenant.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RiskPolicy {
    
    private boolean enableRiskAssessment;
    private boolean enableImpossibleTravelDetection;
    private boolean enableDeviceFingerprintingDetection;
    private boolean enableBehavioralAnalysis;
    private int riskScoreThreshold;
    private int highRiskActionThreshold;
    private boolean blockHighRiskActions;
    private boolean requireMfaForHighRisk;
    
    /**
     * Get default risk policy.
     */
    public static RiskPolicy getDefault() {
        return RiskPolicy.builder()
            .enableRiskAssessment(true)
            .enableImpossibleTravelDetection(true)
            .enableDeviceFingerprintingDetection(true)
            .enableBehavioralAnalysis(false)
            .riskScoreThreshold(70)
            .highRiskActionThreshold(85)
            .blockHighRiskActions(false)
            .requireMfaForHighRisk(true)
            .build();
    }
}
