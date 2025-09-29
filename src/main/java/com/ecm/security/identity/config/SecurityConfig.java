package com.ecm.security.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.http.MediaType;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Main security configuration for the ECM Identity Service.
 * Configures OAuth2 Authorization Server, JWT, WebAuthn, and general security policies.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
public class SecurityConfig {
    
    /**
     * OAuth2 Authorization Server security filter chain.
     * Handles OAuth2/OIDC endpoints with highest precedence.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        // Apply OAuth2 authorization server configuration
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        
        http
            // Redirect to the login page when not authenticated from the authorization endpoint
            .exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                )
            )
            // Accept access tokens for User Info and/or Client Registration
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        
        return http.build();
    }
    
    /**
     * Main application security filter chain.
     * Handles authentication, authorization, and security policies.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                // Public endpoints - no authentication required
                .requestMatchers(
                    "/",
                    "/login",
                    "/register",
                    "/forgot-password",
                    "/reset-password",
                    "/verify-email",
                    "/webauthn/**",
                    "/magic-link/**",
                    "/health",
                    "/actuator/health",
                    "/actuator/prometheus",
                    "/error",
                    "/webjars/**",
                    "/assets/**",
                    "/favicon.ico"
                ).permitAll()
                
                // SCIM endpoints - require specific authentication
                .requestMatchers("/scim/v2/**").hasRole("SCIM_CLIENT")
                
                // SAML endpoints - handle separately
                .requestMatchers("/saml2/**").permitAll()
                
                // Admin endpoints - require admin role
                .requestMatchers("/admin/**").hasRole("ADMIN")
                
                // API endpoints - require authentication
                .requestMatchers("/api/**").authenticated()
                
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            
            // Form-based login configuration
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            
            // Logout configuration
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .deleteCookies("JSESSIONID", "remember-me")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .permitAll()
            )
            
            // OAuth2 login for social/enterprise federation
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error=oauth2")
            )
            
            // SAML2 login for enterprise SSO
            .saml2Login(saml2 -> saml2
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error=saml2")
            )
            
            // JWT resource server for API authentication
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(Customizer.withDefaults())
            )
            
            // Session management
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(5) // Allow up to 5 concurrent sessions
                .maxSessionsPreventsLogin(false) // Don't prevent new logins
                .and()
                .sessionFixation().migrateSession()
                .invalidSessionUrl("/login?expired=true")
            )
            
            // Remember me functionality
            .rememberMe(remember -> remember
                .key("ecm-identity-remember-me-key")
                .tokenValiditySeconds(86400 * 30) // 30 days
                .userDetailsService(userDetailsService())
                .rememberMeParameter("remember-me")
                .rememberMeCookieName("remember-me")
            )
            
            // CORS configuration
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // CSRF protection
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(
                    "/api/**",
                    "/scim/v2/**",
                    "/webauthn/**",
                    "/actuator/**"
                )
            )
            
            // Security headers
            .headers(headers -> headers
                .frameOptions().deny()
                .contentTypeOptions().and()
                .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                    .maxAgeInSeconds(31536000)
                    .includeSubDomains(true)
                )
                .and()
            )
            
            // Exception handling
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(
                    new LoginUrlAuthenticationEntryPoint("/login")
                )
                .accessDeniedPage("/access-denied")
            );
        
        return http.build();
    }
    
    /**
     * Password encoder using Argon2 algorithm for secure password hashing.
     * Configured according to security best practices.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
    
    /**
     * Custom UserDetailsService for loading user information.
     * Will be implemented separately to integrate with our User domain model.
     */
    @Bean
    public org.springframework.security.core.userdetails.UserDetailsService userDetailsService() {
        // This will be implemented as a separate service
        return username -> {
            throw new UnsupportedOperationException("UserDetailsService not yet implemented");
        };
    }
    
    /**
     * CORS configuration to allow cross-origin requests from authorized domains.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allow specific origins in production
        configuration.setAllowedOriginPatterns(List.of(
            "http://localhost:*",
            "https://*.ecm.local",
            "https://*.ecm.com"
        ));
        
        configuration.setAllowedMethods(List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));
        
        configuration.setAllowedHeaders(List.of(
            "Authorization",
            "Content-Type",
            "X-Tenant-ID",
            "X-Requested-With",
            "X-CSRF-Token"
        ));
        
        configuration.setExposedHeaders(List.of(
            "X-Total-Count",
            "X-Rate-Limit-Remaining",
            "X-Rate-Limit-Reset"
        ));
        
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}
