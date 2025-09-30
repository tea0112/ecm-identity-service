package com.ecm.security.identity.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@Slf4j
public class WellKnownController {
    
    /**
     * OIDC Discovery Endpoint - returns OpenID Connect configuration
     */
    @GetMapping("/.well-known/openid-configuration")
    public ResponseEntity<Map<String, Object>> openIdConfiguration(HttpServletRequest httpRequest) {
        try {
            log.info("OpenID Connect configuration request received");
            
            String baseUrl = getBaseUrl(httpRequest);
            
            Map<String, Object> config = new HashMap<>();
            config.put("issuer", baseUrl);
            config.put("authorization_endpoint", baseUrl + "/oauth2/authorize");
            config.put("token_endpoint", baseUrl + "/oauth2/token");
            config.put("userinfo_endpoint", baseUrl + "/oauth2/userinfo");
            config.put("jwks_uri", baseUrl + "/oauth2/jwks");
            config.put("response_types_supported", Arrays.asList("code", "id_token", "code id_token"));
            config.put("subject_types_supported", Arrays.asList("public"));
            config.put("id_token_signing_alg_values_supported", Arrays.asList("RS256"));
            config.put("scopes_supported", Arrays.asList("openid", "profile", "email"));
            config.put("claims_supported", Arrays.asList("sub", "email", "email_verified", "name", "given_name", "family_name", "preferred_username"));
            config.put("grant_types_supported", Arrays.asList("authorization_code", "refresh_token"));
            config.put("code_challenge_methods_supported", Arrays.asList("S256"));
            
            return ResponseEntity.ok(config);
            
        } catch (Exception e) {
            log.error("Error during OpenID Connect configuration request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "server_error"));
        }
    }
    
    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String contextPath = request.getContextPath();
        
        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(serverName);
        
        if ((scheme.equals("http") && serverPort != 80) || (scheme.equals("https") && serverPort != 443)) {
            url.append(":").append(serverPort);
        }
        
        url.append(contextPath);
        
        return url.toString();
    }
}
