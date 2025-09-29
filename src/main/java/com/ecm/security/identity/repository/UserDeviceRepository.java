package com.ecm.security.identity.repository;

import com.ecm.security.identity.domain.User;
import com.ecm.security.identity.domain.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for UserDevice entities.
 */
@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {
    
    /**
     * Finds a device by user and device fingerprint.
     */
    Optional<UserDevice> findByUserAndDeviceFingerprint(User user, String deviceFingerprint);
    
    /**
     * Finds all devices for a user.
     */
    List<UserDevice> findByUserOrderByLastSeenAtDesc(User user);
    
    /**
     * Finds trusted devices for a user.
     */
    List<UserDevice> findByUserAndIsTrustedTrueOrderByLastSeenAtDesc(User user);
    
    /**
     * Finds devices by status for a user.
     */
    List<UserDevice> findByUserAndStatusOrderByLastSeenAtDesc(User user, UserDevice.DeviceStatus status);
    
    /**
     * Finds devices that haven't been seen for a specified period.
     */
    @Query("SELECT d FROM UserDevice d WHERE d.lastSeenAt < :threshold " +
           "AND d.status != 'BLOCKED'")
    List<UserDevice> findInactiveDevices(@Param("threshold") Instant threshold);
    
    /**
     * Finds devices with security concerns.
     */
    @Query("SELECT d FROM UserDevice d WHERE d.jailbrokenRooted = true " +
           "OR d.emulatorDetected = true OR d.torDetected = true " +
           "OR d.status = 'COMPROMISED'")
    List<UserDevice> findDevicesWithSecurityConcerns();
    
    /**
     * Finds devices by IP address pattern.
     */
    @Query("SELECT d FROM UserDevice d WHERE d.ipAddress LIKE :ipPattern " +
           "ORDER BY d.lastSeenAt DESC")
    List<UserDevice> findDevicesByIpPattern(@Param("ipPattern") String ipPattern);
    
    /**
     * Finds devices by location (country).
     */
    List<UserDevice> findByGeolocationCountryOrderByLastSeenAtDesc(String country);
    
    /**
     * Finds devices with low trust scores.
     */
    @Query("SELECT d FROM UserDevice d WHERE d.trustScore < :threshold " +
           "AND d.status = 'VERIFIED' ORDER BY d.trustScore ASC")
    List<UserDevice> findLowTrustDevices(@Param("threshold") Double threshold);
    
    /**
     * Finds devices that support attestation.
     */
    List<UserDevice> findByAttestationSupportedTrueOrderByLastSeenAtDesc();
    
    /**
     * Finds blocked devices for a user.
     */
    List<UserDevice> findByUserAndStatusAndBlockedAtIsNotNullOrderByBlockedAtDesc(
        User user, UserDevice.DeviceStatus status);
    
    /**
     * Finds devices by platform.
     */
    List<UserDevice> findByPlatformOrderByLastSeenAtDesc(String platform);
    
    /**
     * Finds devices with VPN usage detected.
     */
    List<UserDevice> findByVpnDetectedTrueOrderByLastSeenAtDesc();
    
    /**
     * Finds devices that need trust score updates.
     */
    @Query("SELECT d FROM UserDevice d WHERE d.lastSeenAt >= :recentThreshold " +
           "AND d.totalSessions > 0 AND d.status != 'BLOCKED'")
    List<UserDevice> findDevicesNeedingTrustUpdate(@Param("recentThreshold") Instant recentThreshold);
    
    /**
     * Counts devices for a user by status.
     */
    long countByUserAndStatus(User user, UserDevice.DeviceStatus status);
    
    /**
     * Finds devices with push notification capability.
     */
    List<UserDevice> findByUserAndPushEnabledTrueOrderByLastSeenAtDesc(User user);
    
    /**
     * Finds similar devices by fingerprint pattern (for fraud detection).
     */
    @Query("SELECT d FROM UserDevice d WHERE d.deviceFingerprint LIKE :pattern " +
           "AND d.user != :excludeUser ORDER BY d.lastSeenAt DESC")
    List<UserDevice> findSimilarDevices(
        @Param("pattern") String pattern,
        @Param("excludeUser") User excludeUser
    );
}
