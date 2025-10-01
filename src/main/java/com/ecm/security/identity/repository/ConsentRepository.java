package com.ecm.security.identity.repository;

import com.ecm.security.identity.domain.Consent;
import com.ecm.security.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing Consent entities.
 */
@Repository
public interface ConsentRepository extends JpaRepository<Consent, UUID> {

    /**
     * Find all active consents for a user.
     */
    @Query("SELECT c FROM Consent c WHERE c.user = :user AND c.status = 'ACTIVE'")
    List<Consent> findActiveConsentsByUser(@Param("user") User user);

    /**
     * Find all consents for a user (including revoked/expired).
     */
    List<Consent> findByUserOrderByGrantedAtDesc(User user);

    /**
     * Find consent by user and application ID.
     */
    Optional<Consent> findByUserAndApplicationId(User user, String applicationId);

    /**
     * Find active consent by user and application ID.
     */
    @Query("SELECT c FROM Consent c WHERE c.user = :user AND c.applicationId = :applicationId AND c.status = 'ACTIVE'")
    Optional<Consent> findActiveConsentByUserAndApplication(@Param("user") User user, @Param("applicationId") String applicationId);

    /**
     * Count active consents for a user.
     */
    @Query("SELECT COUNT(c) FROM Consent c WHERE c.user = :user AND c.status = 'ACTIVE'")
    long countActiveConsentsByUser(@Param("user") User user);

    /**
     * Find consents that are expiring soon.
     */
    @Query("SELECT c FROM Consent c WHERE c.expiresAt IS NOT NULL AND c.expiresAt <= :expiryThreshold AND c.status = 'ACTIVE'")
    List<Consent> findConsentsExpiringSoon(@Param("expiryThreshold") java.time.Instant expiryThreshold);
}
