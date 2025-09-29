package com.ecm.security.identity.repository;

import com.ecm.security.identity.domain.User;
import com.ecm.security.identity.domain.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for UserCredential entities.
 */
@Repository
public interface UserCredentialRepository extends JpaRepository<UserCredential, UUID> {
    
    /**
     * Finds all credentials for a user by type.
     */
    List<UserCredential> findByUserAndCredentialTypeAndDeletedAtIsNull(
        User user, UserCredential.CredentialType credentialType);
    
    /**
     * Finds active credentials for a user.
     */
    List<UserCredential> findByUserAndStatusAndDeletedAtIsNull(
        User user, UserCredential.CredentialStatus status);
    
    /**
     * Finds a specific credential by user and type.
     */
    Optional<UserCredential> findByUserAndCredentialTypeAndStatusAndDeletedAtIsNull(
        User user, UserCredential.CredentialType credentialType, UserCredential.CredentialStatus status);
    
    /**
     * Finds credentials by verification code.
     */
    Optional<UserCredential> findByVerificationCodeAndStatusAndDeletedAtIsNull(
        String verificationCode, UserCredential.CredentialStatus status);
    
    /**
     * Finds expired credentials.
     */
    @Query("SELECT c FROM UserCredential c WHERE c.expiresAt < :now " +
           "AND c.status = 'ACTIVE' AND c.deletedAt IS NULL")
    List<UserCredential> findExpiredCredentials(@Param("now") Instant now);
    
    /**
     * Finds credentials with exceeded verification attempts.
     */
    @Query("SELECT c FROM UserCredential c WHERE c.verificationAttempts >= c.maxVerificationAttempts " +
           "AND c.status = 'ACTIVE' AND c.deletedAt IS NULL")
    List<UserCredential> findBlockedCredentials();
    
    /**
     * Finds WebAuthn credentials by credential ID.
     */
    Optional<UserCredential> findByWebauthnCredentialIdAndStatusAndDeletedAtIsNull(
        String credentialId, UserCredential.CredentialStatus status);
    
    /**
     * Finds TOTP credentials for a user.
     */
    Optional<UserCredential> findByUserAndCredentialTypeAndStatusAndDeletedAtIsNull(
        User user, UserCredential.CredentialType credentialType, UserCredential.CredentialStatus status);
    
    /**
     * Finds backup codes for a user.
     */
    List<UserCredential> findByUserAndCredentialTypeAndBackupEligibleTrueAndDeletedAtIsNull(
        User user, UserCredential.CredentialType credentialType);
    
    /**
     * Finds recovery codes for a user.
     */
    List<UserCredential> findByUserAndCredentialTypeAndRecoveryCodeUsedFalseAndDeletedAtIsNull(
        User user, UserCredential.CredentialType credentialType);
    
    /**
     * Counts active credentials for a user by type.
     */
    long countByUserAndCredentialTypeAndStatusAndDeletedAtIsNull(
        User user, UserCredential.CredentialType credentialType, UserCredential.CredentialStatus status);
    
    /**
     * Finds credentials that need cleanup.
     */
    @Query("SELECT c FROM UserCredential c WHERE c.deletedAt IS NOT NULL " +
           "AND c.deletedAt < :threshold")
    List<UserCredential> findCredentialsForCleanup(@Param("threshold") Instant threshold);
}
