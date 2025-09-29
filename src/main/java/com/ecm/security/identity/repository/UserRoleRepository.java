package com.ecm.security.identity.repository;

import com.ecm.security.identity.domain.User;
import com.ecm.security.identity.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for UserRole entities.
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {
    
    /**
     * Finds all active roles for a user.
     */
    List<UserRole> findByUserAndStatusAndDeletedAtIsNull(User user, UserRole.RoleStatus status);
    
    /**
     * Finds roles by user and role name.
     */
    List<UserRole> findByUserAndRoleNameAndDeletedAtIsNull(User user, String roleName);
    
    /**
     * Finds a specific role by user, name and status.
     */
    Optional<UserRole> findByUserAndRoleNameAndStatusAndDeletedAtIsNull(
        User user, String roleName, UserRole.RoleStatus status);
    
    /**
     * Finds roles by scope.
     */
    List<UserRole> findByScopeAndStatusAndDeletedAtIsNull(String scope, UserRole.RoleStatus status);
    
    /**
     * Finds delegated roles.
     */
    List<UserRole> findByAssignmentTypeAndStatusAndDeletedAtIsNull(
        UserRole.AssignmentType assignmentType, UserRole.RoleStatus status);
    
    /**
     * Finds roles delegated by a specific user.
     */
    List<UserRole> findByDelegatedFromUserIdAndDeletedAtIsNull(String delegatedFromUserId);
    
    /**
     * Finds break-glass roles.
     */
    List<UserRole> findByBreakGlassRoleTrueAndStatusAndDeletedAtIsNull(UserRole.RoleStatus status);
    
    /**
     * Finds expired roles.
     */
    @Query("SELECT r FROM UserRole r WHERE r.expiresAt < :now " +
           "AND r.status = 'ACTIVE' AND r.deletedAt IS NULL")
    List<UserRole> findExpiredRoles(@Param("now") Instant now);
    
    /**
     * Finds roles pending approval.
     */
    List<UserRole> findByStatusAndDeletedAtIsNull(UserRole.RoleStatus status);
    
    /**
     * Finds JIT (Just-In-Time) roles.
     */
    List<UserRole> findByAssignmentTypeAndExpiresAtAfterAndStatusAndDeletedAtIsNull(
        UserRole.AssignmentType assignmentType, Instant now, UserRole.RoleStatus status);
    
    /**
     * Finds emergency access roles.
     */
    List<UserRole> findByEmergencyAccessTrueAndStatusAndDeletedAtIsNull(UserRole.RoleStatus status);
    
    /**
     * Finds roles requiring approval.
     */
    List<UserRole> findByApprovalRequiredTrueAndStatusAndDeletedAtIsNull(UserRole.RoleStatus status);
    
    /**
     * Finds revoked roles.
     */
    List<UserRole> findByStatusAndRevokedAtAfterAndDeletedAtIsNull(
        UserRole.RoleStatus status, Instant since);
    
    /**
     * Counts active roles for a user.
     */
    long countByUserAndStatusAndDeletedAtIsNull(User user, UserRole.RoleStatus status);
    
    /**
     * Finds roles by delegation depth.
     */
    @Query("SELECT r FROM UserRole r WHERE r.delegationDepth <= :maxDepth " +
           "AND r.status = 'ACTIVE' AND r.deletedAt IS NULL")
    List<UserRole> findRolesByMaxDelegationDepth(@Param("maxDepth") Integer maxDepth);
    
    /**
     * Finds roles for cleanup (soft deleted).
     */
    @Query("SELECT r FROM UserRole r WHERE r.deletedAt IS NOT NULL " +
           "AND r.deletedAt < :threshold")
    List<UserRole> findRolesForCleanup(@Param("threshold") Instant threshold);
}
