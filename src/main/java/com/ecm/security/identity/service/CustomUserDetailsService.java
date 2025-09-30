package com.ecm.security.identity.service;

import com.ecm.security.identity.domain.Tenant;
import com.ecm.security.identity.domain.User;
import com.ecm.security.identity.repository.TenantRepository;
import com.ecm.security.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Custom UserDetailsService implementation for the ECM Identity Service.
 * Loads user information from the database and converts it to Spring Security UserDetails.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantContextService tenantContextService;
    
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Loading user details for username: {}", username);
        
        try {
            // Extract tenant and email from username (format: tenant:email)
            String[] parts = username.split(":", 2);
            if (parts.length != 2) {
                throw new UsernameNotFoundException("Invalid username format. Expected: tenant:email");
            }
            
            String tenantCode = parts[0];
            String email = parts[1];
            
            // Get tenant
            Optional<Tenant> tenantOpt = tenantRepository.findByTenantCodeAndStatusNot(
                tenantCode, Tenant.TenantStatus.ARCHIVED);
            if (tenantOpt.isEmpty()) {
                throw new UsernameNotFoundException("Tenant not found: " + tenantCode);
            }
            
            Tenant tenant = tenantOpt.get();
            
            // Get user
            Optional<User> userOpt = userRepository.findByTenantAndEmailAndDeletedAtIsNull(tenant, email);
            if (userOpt.isEmpty()) {
                throw new UsernameNotFoundException("User not found: " + email + " in tenant: " + tenantCode);
            }
            
            User user = userOpt.get();
            
            // Check if user is active
            if (user.getStatus() != User.UserStatus.ACTIVE) {
                throw new UsernameNotFoundException("User is not active: " + email);
            }
            
            // Build authorities
            Collection<GrantedAuthority> authorities = buildAuthorities(user);
            
            // Create UserDetails
            return new CustomUserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getStatus() == User.UserStatus.ACTIVE,
                user.getStatus() == User.UserStatus.ACTIVE,
                user.getStatus() == User.UserStatus.ACTIVE,
                user.getStatus() == User.UserStatus.ACTIVE,
                authorities,
                user.getTenant().getId(),
                user.getFirstName(),
                user.getLastName()
            );
            
        } catch (UsernameNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error loading user details for username: {}", username, e);
            throw new UsernameNotFoundException("Error loading user: " + username, e);
        }
    }
    
    /**
     * Builds authorities for a user based on their roles and permissions.
     */
    private Collection<GrantedAuthority> buildAuthorities(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        
        // Add basic user role
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        
        // Add tenant-specific role
        authorities.add(new SimpleGrantedAuthority("ROLE_TENANT_" + user.getTenant().getTenantCode().toUpperCase()));
        
        // Add admin role if user has admin privileges
        // TODO: Implement proper admin role checking based on UserRole entities
        // For now, we'll add basic roles based on user status
        if (user.getStatus() == User.UserStatus.ACTIVE) {
            // Add additional roles based on user attributes
            // This will be expanded when role-based access control is implemented
        }
        
        // TODO: Add role-based authorities from UserRole entities
        // This would require implementing role-based access control
        
        return authorities;
    }
    
    /**
     * Custom UserDetails implementation that includes additional user information.
     */
    public static class CustomUserPrincipal implements UserDetails {
        private final java.util.UUID userId;
        private final String email;
        private final String password;
        private final boolean enabled;
        private final boolean accountNonExpired;
        private final boolean credentialsNonExpired;
        private final boolean accountNonLocked;
        private final Collection<GrantedAuthority> authorities;
        private final java.util.UUID tenantId;
        private final String firstName;
        private final String lastName;
        
        public CustomUserPrincipal(java.util.UUID userId, String email, String password,
                                 boolean enabled, boolean accountNonExpired, boolean credentialsNonExpired,
                                 boolean accountNonLocked, Collection<GrantedAuthority> authorities,
                                 java.util.UUID tenantId, String firstName, String lastName) {
            this.userId = userId;
            this.email = email;
            this.password = password;
            this.enabled = enabled;
            this.accountNonExpired = accountNonExpired;
            this.credentialsNonExpired = credentialsNonExpired;
            this.accountNonLocked = accountNonLocked;
            this.authorities = authorities;
            this.tenantId = tenantId;
            this.firstName = firstName;
            this.lastName = lastName;
        }
        
        @Override
        public Collection<GrantedAuthority> getAuthorities() {
            return authorities;
        }
        
        @Override
        public String getPassword() {
            return password;
        }
        
        @Override
        public String getUsername() {
            return email;
        }
        
        @Override
        public boolean isAccountNonExpired() {
            return accountNonExpired;
        }
        
        @Override
        public boolean isAccountNonLocked() {
            return accountNonLocked;
        }
        
        @Override
        public boolean isCredentialsNonExpired() {
            return credentialsNonExpired;
        }
        
        @Override
        public boolean isEnabled() {
            return enabled;
        }
        
        // Additional getters for custom information
        public java.util.UUID getUserId() {
            return userId;
        }
        
        public java.util.UUID getTenantId() {
            return tenantId;
        }
        
        public String getFirstName() {
            return firstName;
        }
        
        public String getLastName() {
            return lastName;
        }
    }
}
