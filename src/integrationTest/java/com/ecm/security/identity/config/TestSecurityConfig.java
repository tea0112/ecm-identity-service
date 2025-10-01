package com.ecm.security.identity.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.UUID;

/**
 * Test-specific security configuration to avoid bean definition conflicts.
 * Provides mock implementations for OAuth2 and JWT components.
 */
@TestConfiguration
@org.springframework.context.annotation.Profile("nfr1-test")
public class TestSecurityConfig {

    /**
     * Mock JWT encoder for testing.
     */
    @Bean
    @Primary
    public JwtEncoder testJwtEncoder() {
        return new JwtEncoder() {
            @Override
            public org.springframework.security.oauth2.jwt.Jwt encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters parameters) {
                // Simple mock implementation
                return org.springframework.security.oauth2.jwt.Jwt.withTokenValue("mock-token")
                    .header("alg", "RS256")
                    .header("typ", "JWT")
                    .claim("iss", "test-issuer")
                    .claim("sub", "test-subject")
                    .claim("aud", "test-audience")
                    .claim("jti", UUID.randomUUID().toString())
                    .issuedAt(java.time.Instant.now())
                    .expiresAt(java.time.Instant.now().plus(Duration.ofHours(1)))
                    .build();
            }
        };
    }

    /**
     * Mock JWT decoder for testing.
     */
    @Bean
    @Primary
    public JwtDecoder testJwtDecoder() {
        return new JwtDecoder() {
            @Override
            public org.springframework.security.oauth2.jwt.Jwt decode(String token) {
                // For testing, just return a basic JWT
                return org.springframework.security.oauth2.jwt.Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .header("typ", "JWT")
                    .claim("iss", "test-issuer")
                    .claim("sub", "test-subject")
                    .claim("aud", "test-audience")
                    .claim("jti", UUID.randomUUID().toString())
                    .issuedAt(java.time.Instant.now())
                    .expiresAt(java.time.Instant.now().plus(Duration.ofHours(1)))
                    .build();
            }
        };
    }

    /**
     * Mock JWK source for testing.
     */
    @Bean
    @Primary
    public JWKSource<SecurityContext> testJwkSource() {
        KeyPair keyPair = generateRsaKey();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID("test-key-id")
                .build();
        
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    /**
     * Mock registered client repository for testing.
     */
    @Bean
    @Primary
    public RegisteredClientRepository testRegisteredClientRepository() {
        return new RegisteredClientRepository() {
            @Override
            public void save(org.springframework.security.oauth2.server.authorization.client.RegisteredClient registeredClient) {
                // Mock implementation - do nothing
            }

            @Override
            public org.springframework.security.oauth2.server.authorization.client.RegisteredClient findById(String id) {
                return null;
            }

            @Override
            public org.springframework.security.oauth2.server.authorization.client.RegisteredClient findByClientId(String clientId) {
                return null;
            }
        };
    }

    /**
     * Mock authorization server settings for testing.
     */
    @Bean
    @Primary
    public AuthorizationServerSettings testAuthorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer("http://localhost:8080")
                .build();
    }

    /**
     * Mock OAuth2 token customizer for testing.
     */
    @Bean
    @Primary
    public OAuth2TokenCustomizer<JwtEncodingContext> testJwtTokenCustomizer() {
        return context -> {
            // Mock implementation - do nothing
        };
    }

    /**
     * Password encoder for testing.
     */
    @Bean
    @Primary
    public PasswordEncoder securityTestPasswordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 65536, 3);
    }

    /**
     * Generates an RSA key pair for testing.
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
