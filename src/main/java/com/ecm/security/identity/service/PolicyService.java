package com.ecm.security.identity.service;

import com.ecm.security.identity.domain.TenantPolicy;
import com.ecm.security.identity.repository.TenantPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing tenant policies and policy evaluation.
 * Handles policy CRUD operations, caching, and retrieval optimization.
 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class PolicyService {
    
    private final TenantPolicyRepository policyRepository;
    private final AuditService auditService;
    
    public PolicyService(TenantPolicyRepository policyRepository, @Lazy AuditService auditService) {
        this.policyRepository = policyRepository;
        this.auditService = auditService;
    }
    
    /**
     * Gets all applicable policies for authorization evaluation.
     * Results are cached for performance.
     */
    @Cacheable(value = "tenant-policies", key = "#tenantId + ':' + #subject + ':' + #resource + ':' + #action")
    public List<TenantPolicy> getApplicablePolicies(UUID tenantId, String subject, String resource, String action) {
        log.debug("Retrieving applicable policies for tenant: {}, subject: {}, resource: {}, action: {}", 
                  tenantId, subject, resource, action);
        
        return policyRepository.findApplicablePolicies(tenantId, subject, resource, action);
    }
    
    /**
     * Gets all active policies for a tenant.
     */
    @Cacheable(value = "tenant-policies", key = "#tenantId + ':all'")
    public List<TenantPolicy> getAllActivePolicies(UUID tenantId) {
        return policyRepository.findByTenantIdAndStatusOrderByPriority(
            tenantId, TenantPolicy.PolicyStatus.ACTIVE);
    }
    
    /**
     * Creates a new tenant policy.
     */
    @Transactional
    public TenantPolicy createPolicy(TenantPolicy policy) {
        log.info("Creating new policy: {} for tenant: {}", policy.getName(), policy.getTenant().getId());
        
        // Validate policy before saving
        validatePolicy(policy);
        
        TenantPolicy savedPolicy = policyRepository.save(policy);
        
        // Clear cache for affected tenant
        clearPolicyCache(policy.getTenant().getId());
        
        // Audit the policy creation
        auditService.logPolicyEvent("POLICY_CREATED", savedPolicy, null);
        
        return savedPolicy;
    }
    
    /**
     * Updates an existing tenant policy.
     */
    @Transactional
    public TenantPolicy updatePolicy(UUID policyId, TenantPolicy updatedPolicy) {
        TenantPolicy existingPolicy = policyRepository.findById(policyId)
            .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));
        
        log.info("Updating policy: {} for tenant: {}", existingPolicy.getName(), existingPolicy.getTenant().getId());
        
        // Store original for audit
        TenantPolicy originalPolicy = createPolicyCopy(existingPolicy);
        
        // Update fields
        existingPolicy.setName(updatedPolicy.getName());
        existingPolicy.setDescription(updatedPolicy.getDescription());
        existingPolicy.setPolicyType(updatedPolicy.getPolicyType());
        existingPolicy.setEffect(updatedPolicy.getEffect());
        existingPolicy.setPriority(updatedPolicy.getPriority());
        existingPolicy.setStatus(updatedPolicy.getStatus());
        existingPolicy.setPolicyDocument(updatedPolicy.getPolicyDocument());
        existingPolicy.setRegoPolicy(updatedPolicy.getRegoPolicy());
        existingPolicy.setSubjects(updatedPolicy.getSubjects());
        existingPolicy.setResources(updatedPolicy.getResources());
        existingPolicy.setActions(updatedPolicy.getActions());
        existingPolicy.setConditions(updatedPolicy.getConditions());
        existingPolicy.setTimeRestrictions(updatedPolicy.getTimeRestrictions());
        existingPolicy.setIpRestrictions(updatedPolicy.getIpRestrictions());
        existingPolicy.setDeviceRestrictions(updatedPolicy.getDeviceRestrictions());
        existingPolicy.setRiskLevelMax(updatedPolicy.getRiskLevelMax());
        existingPolicy.setMfaRequired(updatedPolicy.getMfaRequired());
        existingPolicy.setStepUpRequired(updatedPolicy.getStepUpRequired());
        existingPolicy.setConsentRequired(updatedPolicy.getConsentRequired());
        existingPolicy.setAuditLevel(updatedPolicy.getAuditLevel());
        existingPolicy.setCacheTtlSeconds(updatedPolicy.getCacheTtlSeconds());
        existingPolicy.setBreakGlassEligible(updatedPolicy.getBreakGlassEligible());
        existingPolicy.setEmergencyOverride(updatedPolicy.getEmergencyOverride());
        existingPolicy.setTags(updatedPolicy.getTags());
        existingPolicy.setMetadata(updatedPolicy.getMetadata());
        
        // Validate the updated policy
        validatePolicy(existingPolicy);
        
        TenantPolicy savedPolicy = policyRepository.save(existingPolicy);
        
        // Clear cache for affected tenant
        clearPolicyCache(existingPolicy.getTenant().getId());
        
        // Audit the policy update
        auditService.logPolicyEvent("POLICY_UPDATED", savedPolicy, originalPolicy);
        
        return savedPolicy;
    }
    
    /**
     * Deletes a tenant policy (soft delete).
     */
    @Transactional
    public void deletePolicy(UUID policyId) {
        TenantPolicy policy = policyRepository.findById(policyId)
            .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));
        
        log.info("Deleting policy: {} for tenant: {}", policy.getName(), policy.getTenant().getId());
        
        // Soft delete
        policy.markAsDeleted();
        policyRepository.save(policy);
        
        // Clear cache for affected tenant
        clearPolicyCache(policy.getTenant().getId());
        
        // Audit the policy deletion
        auditService.logPolicyEvent("POLICY_DELETED", policy, null);
    }
    
    /**
     * Activates a policy.
     */
    @Transactional
    public void activatePolicy(UUID policyId) {
        TenantPolicy policy = policyRepository.findById(policyId)
            .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));
        
        log.info("Activating policy: {} for tenant: {}", policy.getName(), policy.getTenant().getId());
        
        policy.setStatus(TenantPolicy.PolicyStatus.ACTIVE);
        policyRepository.save(policy);
        
        // Clear cache for affected tenant
        clearPolicyCache(policy.getTenant().getId());
        
        // Audit the policy activation
        auditService.logPolicyEvent("POLICY_ACTIVATED", policy, null);
    }
    
    /**
     * Deactivates a policy.
     */
    @Transactional
    public void deactivatePolicy(UUID policyId) {
        TenantPolicy policy = policyRepository.findById(policyId)
            .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));
        
        log.info("Deactivating policy: {} for tenant: {}", policy.getName(), policy.getTenant().getId());
        
        policy.setStatus(TenantPolicy.PolicyStatus.INACTIVE);
        policyRepository.save(policy);
        
        // Clear cache for affected tenant
        clearPolicyCache(policy.getTenant().getId());
        
        // Audit the policy deactivation
        auditService.logPolicyEvent("POLICY_DEACTIVATED", policy, null);
    }
    
    /**
     * Tests a policy against sample data for validation.
     */
    public PolicyTestResult testPolicy(TenantPolicy policy, List<PolicyTestCase> testCases) {
        log.debug("Testing policy: {} with {} test cases", policy.getName(), testCases.size());
        
        PolicyTestResult result = new PolicyTestResult();
        result.setPolicyId(policy.getId());
        result.setPolicyName(policy.getName());
        result.setTestCases(new java.util.ArrayList<>());
        
        for (PolicyTestCase testCase : testCases) {
            PolicyTestCaseResult caseResult = new PolicyTestCaseResult();
            caseResult.setTestCase(testCase);
            
            try {
                // Simulate policy evaluation
                boolean matches = policy.appliesToSubject(testCase.getSubject()) &&
                                 policy.appliesToResource(testCase.getResource()) &&
                                 policy.appliesToAction(testCase.getAction());
                
                if (matches) {
                    caseResult.setActualDecision(policy.getEffect() == TenantPolicy.Effect.ALLOW ? "ALLOW" : "DENY");
                } else {
                    caseResult.setActualDecision("NOT_APPLICABLE");
                }
                
                caseResult.setPassed(caseResult.getActualDecision().equals(testCase.getExpectedDecision()));
                
            } catch (Exception e) {
                caseResult.setActualDecision("ERROR");
                caseResult.setErrorMessage(e.getMessage());
                caseResult.setPassed(false);
            }
            
            result.getTestCases().add(caseResult);
        }
        
        // Calculate overall result
        long passedCount = result.getTestCases().stream().mapToLong(tc -> tc.isPassed() ? 1 : 0).sum();
        result.setTotalTests(testCases.size());
        result.setPassedTests((int) passedCount);
        result.setOverallPassed(passedCount == testCases.size());
        
        return result;
    }
    
    /**
     * Validates a policy for correctness and security.
     */
    private void validatePolicy(TenantPolicy policy) {
        if (policy.getName() == null || policy.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Policy name cannot be empty");
        }
        
        if (policy.getPolicyType() == null) {
            throw new IllegalArgumentException("Policy type is required");
        }
        
        if (policy.getEffect() == null) {
            throw new IllegalArgumentException("Policy effect is required");
        }
        
        if (policy.getPriority() == null || policy.getPriority() < 0) {
            throw new IllegalArgumentException("Policy priority must be a non-negative number");
        }
        
        // Validate break-glass policies have high priority
        if (policy.getBreakGlassEligible() && policy.getPriority() > 100) {
            throw new IllegalArgumentException("Break-glass policies must have high priority (≤100)");
        }
        
        // Validate that deny policies for critical actions have high priority
        if (policy.getEffect() == TenantPolicy.Effect.DENY && 
            policy.getActions() != null &&
            java.util.Arrays.stream(policy.getActions()).anyMatch(action -> 
                action.contains("delete") || action.contains("admin"))) {
            if (policy.getPriority() > 500) {
                log.warn("Deny policy for critical actions should have higher priority: {}", policy.getName());
            }
        }
    }
    
    /**
     * Creates a copy of a policy for audit purposes.
     */
    private TenantPolicy createPolicyCopy(TenantPolicy original) {
        TenantPolicy copy = new TenantPolicy();
        copy.setId(original.getId());
        copy.setTenant(original.getTenant());
        copy.setName(original.getName());
        copy.setDescription(original.getDescription());
        copy.setPolicyType(original.getPolicyType());
        copy.setEffect(original.getEffect());
        copy.setPriority(original.getPriority());
        copy.setStatus(original.getStatus());
        copy.setPolicyDocument(original.getPolicyDocument());
        copy.setRegoPolicy(original.getRegoPolicy());
        copy.setSubjects(original.getSubjects());
        copy.setResources(original.getResources());
        copy.setActions(original.getActions());
        copy.setConditions(original.getConditions());
        copy.setTimeRestrictions(original.getTimeRestrictions());
        copy.setIpRestrictions(original.getIpRestrictions());
        copy.setDeviceRestrictions(original.getDeviceRestrictions());
        copy.setRiskLevelMax(original.getRiskLevelMax());
        copy.setMfaRequired(original.getMfaRequired());
        copy.setStepUpRequired(original.getStepUpRequired());
        copy.setConsentRequired(original.getConsentRequired());
        copy.setAuditLevel(original.getAuditLevel());
        copy.setCacheTtlSeconds(original.getCacheTtlSeconds());
        copy.setBreakGlassEligible(original.getBreakGlassEligible());
        copy.setEmergencyOverride(original.getEmergencyOverride());
        copy.setTags(original.getTags());
        copy.setMetadata(original.getMetadata());
        return copy;
    }
    
    /**
     * Clears policy cache for a specific tenant.
     */
    private void clearPolicyCache(UUID tenantId) {
        // Implementation would clear Redis cache entries for the tenant
        log.debug("Clearing policy cache for tenant: {}", tenantId);
    }
    
    // Supporting classes for policy testing
    
    @lombok.Data
    public static class PolicyTestCase {
        private String name;
        private String subject;
        private String resource;
        private String action;
        private String expectedDecision;
        private java.util.Map<String, Object> context;
    }
    
    @lombok.Data
    public static class PolicyTestCaseResult {
        private PolicyTestCase testCase;
        private String actualDecision;
        private String errorMessage;
        private boolean passed;
    }
    
    @lombok.Data
    public static class PolicyTestResult {
        private UUID policyId;
        private String policyName;
        private int totalTests;
        private int passedTests;
        private boolean overallPassed;
        private java.util.List<PolicyTestCaseResult> testCases;
    }
}
