package com.ecm.security.identity.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.UUID;

/**
 * JWT configuration for token signing, verification, and customization.
 * Supports key rotation and custom claims injection.
 */
@Configuration
public class JwtConfig {
    
    @Value("${ecm.security.jwt.access-token-expiration:900000}")
    private long accessTokenExpiration;
    
    @Value("${ecm.security.jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpiration;
    
    /**
     * JWT encoder for creating signed tokens.
     * Uses RSA key pair for asymmetric signing.
     */
    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }
    
    /**
     * JWT decoder for verifying signed tokens.
     * Configured with the public key for signature verification.
     */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return NimbusJwtDecoder.withJwkSetUri("/.well-known/jwks.json").build();
    }
    
    /**
     * JWK (JSON Web Key) source containing the signing keys.
     * In production, this should be loaded from a secure key store.
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = generateRsaKey();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();
        
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }
    
    /**
     * OAuth2 token customizer for adding custom claims to JWT tokens.
     * Injects tenant information, user details, and security context.
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer() {
        return context -> {
            if (context.getTokenType().getValue().equals("access_token")) {
                // Add custom claims to access tokens
                context.getClaims()
                    .claim("token_type", "access_token")
                    .claim("iss", "https://identity.ecm.com")
                    .claim("version", "1.0");
                
                // Add tenant information if available
                if (context.getPrincipal().getName() != null) {
                    // This will be enhanced when we implement the authentication service
                    context.getClaims().claim("preferred_username", context.getPrincipal().getName());
                }
                
                // Set token expiration
                context.getClaims().expiresAt(
                    java.time.Instant.now().plus(Duration.ofMillis(accessTokenExpiration))
                );
                
                // Add audience claim for security
                context.getClaims().audience(java.util.List.of("ecm-api", "ecm-web"));
                
            } else if (context.getTokenType().getValue().equals("refresh_token")) {
                // Add custom claims to refresh tokens
                context.getClaims()
                    .claim("token_type", "refresh_token")
                    .claim("iss", "https://identity.ecm.com");
                
                // Set refresh token expiration
                context.getClaims().expiresAt(
                    java.time.Instant.now().plus(Duration.ofMillis(refreshTokenExpiration))
                );
                
            } else if (context.getTokenType().getValue().equals("id_token")) {
                // Add custom claims to ID tokens (OpenID Connect)
                context.getClaims()
                    .claim("iss", "https://identity.ecm.com")
                    .claim("aud", context.getRegisteredClient().getClientId());
                
                // Add standard OIDC claims
                if (context.getPrincipal().getName() != null) {
                    context.getClaims()
                        .claim("sub", context.getPrincipal().getName())
                        .claim("preferred_username", context.getPrincipal().getName())
                        .claim("auth_time", java.time.Instant.now().getEpochSecond());
                }
            }
            
            // Common claims for all token types
            context.getClaims()
                .claim("jti", UUID.randomUUID().toString()) // JWT ID for tracking
                .claim("client_id", context.getRegisteredClient().getClientId())
                .issuer("https://identity.ecm.com");
        };
    }
    
    /**
     * Generates an RSA key pair for JWT signing.
     * In production, keys should be loaded from a secure key management service.
     */
    private static KeyPair generateRsaKey() {
        KeyPair keyPair;
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            keyPair = keyPairGenerator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate RSA key pair", ex);
        }
        return keyPair;
    }
}
