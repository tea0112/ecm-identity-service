package com.ecm.security.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.util.UUID;

/**
 * OAuth2 Authorization Server configuration.
 * Defines registered clients, scopes, and authorization server settings.
 */
@Configuration
public class OAuth2AuthorizationServerConfig {
    
    /**
     * Repository of registered OAuth2/OIDC clients.
     * In production, this should be backed by a database.
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        // Default ECM Web Client - Public client for SPAs
        RegisteredClient ecmWebClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("ecm-web-client")
                .clientSecret("{noop}") // No secret for public clients
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:3000/auth/callback")
                .redirectUri("https://app.ecm.com/auth/callback")
                .postLogoutRedirectUri("http://localhost:3000/logout")
                .postLogoutRedirectUri("https://app.ecm.com/logout")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .scope("read")
                .scope("write")
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .requireProofKey(true) // Require PKCE for security
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(15))
                        .refreshTokenTimeToLive(Duration.ofDays(7))
                        .reuseRefreshTokens(false) // Rotate refresh tokens
                        .idTokenSignatureAlgorithm(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
                        .build())
                .build();
        
        // ECM API Client - Confidential client for server-to-server
        RegisteredClient ecmApiClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("ecm-api-client")
                .clientSecret("{bcrypt}$2a$10$X5wFBtLrL/kCwDLJnT8qReQ9VFKZtzr4.IZR6kJYaHvAAJY5GZGz.") // secret: api-client-secret
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:8081/auth/callback")
                .redirectUri("https://api.ecm.com/auth/callback")
                .scope("read")
                .scope("write")
                .scope("admin")
                .scope("scim")
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(true)
                        .requireProofKey(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(30))
                        .refreshTokenTimeToLive(Duration.ofDays(30))
                        .reuseRefreshTokens(false)
                        .build())
                .build();
        
        // Mobile App Client - Public client for mobile applications
        RegisteredClient ecmMobileClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("ecm-mobile-client")
                .clientSecret("{noop}")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.DEVICE_CODE) // For device flow
                .redirectUri("com.ecm.mobile://auth/callback")
                .redirectUri("https://app.ecm.com/mobile/callback")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .scope("offline_access")
                .scope("read")
                .scope("write")
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .requireProofKey(true)
                        .tokenEndpointAuthenticationSigningAlgorithm(
                            org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(15))
                        .refreshTokenTimeToLive(Duration.ofDays(90)) // Longer for mobile
                        .reuseRefreshTokens(false)
                        .deviceCodeTimeToLive(Duration.ofMinutes(10))
                        .build())
                .build();
        
        // CLI Tool Client - Public client for command-line tools
        RegisteredClient ecmCliClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("ecm-cli-client")
                .clientSecret("{noop}")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.DEVICE_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .scope("read")
                .scope("write")
                .scope("cli")
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(true)
                        .requireProofKey(false) // Device flow doesn't use PKCE
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(1))
                        .refreshTokenTimeToLive(Duration.ofDays(30))
                        .reuseRefreshTokens(false)
                        .deviceCodeTimeToLive(Duration.ofMinutes(10))
                        .build())
                .build();
        
        // Third-party Integration Client - For external integrations
        RegisteredClient thirdPartyClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("third-party-integration")
                .clientSecret("{bcrypt}$2a$10$h1k8g2vFEt7yJK3n8s5qfO9VLnKs2tR8sT4uV6wR7xY9zA1bC2dE.") // secret: integration-secret
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_JWT)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("https://partner.example.com/oauth/callback")
                .scope("read")
                .scope("integration")
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(true)
                        .requireProofKey(true)
                        .tokenEndpointAuthenticationSigningAlgorithm(
                            org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(30))
                        .refreshTokenTimeToLive(Duration.ofDays(7))
                        .reuseRefreshTokens(false)
                        .build())
                .build();
        
        return new InMemoryRegisteredClientRepository(
                ecmWebClient,
                ecmApiClient,
                ecmMobileClient,
                ecmCliClient,
                thirdPartyClient
        );
    }
    
    /**
     * Authorization server settings and endpoints configuration.
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer("http://localhost:8080") // In production: https://identity.ecm.com
                .authorizationEndpoint("/oauth2/authorize")
                .deviceAuthorizationEndpoint("/oauth2/device_authorization")
                .deviceVerificationEndpoint("/oauth2/device_verification")
                .tokenEndpoint("/oauth2/token")
                .tokenIntrospectionEndpoint("/oauth2/introspect")
                .tokenRevocationEndpoint("/oauth2/revoke")
                .jwkSetEndpoint("/oauth2/jwks")
                .oidcLogoutEndpoint("/connect/logout")
                .oidcUserInfoEndpoint("/userinfo")
                .oidcClientRegistrationEndpoint("/connect/register")
                .build();
    }
}
