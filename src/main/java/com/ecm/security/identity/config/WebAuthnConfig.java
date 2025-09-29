package com.ecm.security.identity.config;

import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;
import java.util.Set;

/**
 * WebAuthn/FIDO2 configuration for passwordless authentication.
 * Configures the Relying Party settings for WebAuthn operations.
 */
@Configuration
public class WebAuthnConfig {
    
    @Value("${ecm.webauthn.rp-id:localhost}")
    private String rpId;
    
    @Value("${ecm.webauthn.rp-name:ECM Identity Service}")
    private String rpName;
    
    @Value("${ecm.webauthn.rp-origin:http://localhost:8080}")
    private String rpOrigin;
    
    /**
     * WebAuthn Relying Party configuration.
     * Defines the identity and settings for WebAuthn operations.
     */
    @Bean
    public RelyingParty relyingParty() {
        RelyingPartyIdentity rpIdentity = RelyingPartyIdentity.builder()
                .id(rpId)
                .name(rpName)
                .build();
        
        return RelyingParty.builder()
                .identity(rpIdentity)
                .credentialRepository(credentialRepository())
                .origins(Set.of(rpOrigin))
                .allowUnrequestedExtensions(false)
                .allowUntrustedAttestation(false)
                .validateSignatureCounter(true)
                .build();
    }
    
    /**
     * WebAuthn credential repository for storing and retrieving credentials.
     * This will be implemented to integrate with our UserCredential domain model.
     */
    @Bean
    public com.yubico.webauthn.CredentialRepository credentialRepository() {
        return new com.yubico.webauthn.CredentialRepository() {
            @Override
            public Set<com.yubico.webauthn.data.PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
                // This will be implemented to query UserCredential entities
                return Set.of();
            }
            
            @Override
            public Optional<com.yubico.webauthn.data.ByteArray> getUserHandleForUsername(String username) {
                // This will be implemented to return user handle from User entity
                return Optional.empty();
            }
            
            @Override
            public Optional<String> getUsernameForUserHandle(com.yubico.webauthn.data.ByteArray userHandle) {
                // This will be implemented to query User entity by handle
                return Optional.empty();
            }
            
            @Override
            public Optional<com.yubico.webauthn.RegisteredCredential> lookup(
                    com.yubico.webauthn.data.ByteArray credentialId,
                    com.yubico.webauthn.data.ByteArray userHandle) {
                // This will be implemented to query UserCredential entities
                return Optional.empty();
            }
            
            @Override
            public Set<com.yubico.webauthn.RegisteredCredential> lookupAll(
                    com.yubico.webauthn.data.ByteArray credentialId) {
                // This will be implemented to query UserCredential entities
                return Set.of();
            }
        };
    }
}
