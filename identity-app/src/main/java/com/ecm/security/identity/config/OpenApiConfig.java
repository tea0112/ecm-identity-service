package com.ecm.security.identity.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * OpenAPI/Swagger Configuration for ECM Identity Service.
 * 
 * Provides:
 * - API metadata (title, version, description)
 * - OAuth2 and Bearer JWT security schemes
 * - Module-based API grouping (users, roles, auth)
 * - Server URL configuration per environment
 */
@Configuration
public class OpenApiConfig {

  @Value("${server.port:8080}")
  private String serverPort;

  @Value("${springdoc.oauth2.authorization-url:http://localhost:8080/api/auth/authorize}")
  private String authorizationUrl;

  @Value("${springdoc.oauth2.token-url:http://localhost:8080/api/auth/token}")
  private String tokenUrl;

  @Value("${springdoc.oauth2.refresh-url:http://localhost:8080/api/auth/refresh}")
  private String refreshUrl;

  /**
   * Main OpenAPI configuration bean.
   */
  @Bean
  public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .info(apiInfo())
        .externalDocs(externalDocs())
        .addServersItem(new Server()
            .url("http://localhost:" + serverPort)
            .description("Local Development Server"))
        .addServersItem(new Server()
            .url("https://api.ecm.company.com")
            .description("Production Server"))
        .components(new Components()
            .addSecuritySchemes("bearer-jwt", bearerScheme())
            .addSecuritySchemes("oauth2", oauth2Scheme()))
        .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
  }

  /**
   * API metadata information.
   */
  private Info apiInfo() {
    return new Info()
        .title("ECM Identity Service API")
        .version("1.0.0")
        .description("""
            **Enterprise Content Management - Identity & Access Management Service**

            This API provides comprehensive identity management capabilities including:
            - **User Management**: CRUD operations for user accounts
            - **Role Management**: Role-based access control (RBAC)
            - **Authentication**: OAuth2/JWT-based authentication flows
            - **Session Management**: Secure session handling

            ## Authentication

            Most endpoints require authentication via Bearer JWT token.
            Use the Authorize button to authenticate with your credentials.

            ## Rate Limiting

            API requests are rate-limited to prevent abuse.
            Check response headers for rate limit information.
            """)
        .contact(new Contact()
            .name("ECM Platform Team")
            .email("platform@company.com")
            .url("https://ecm.company.com/support"))
        .license(new License()
            .name("Proprietary")
            .url("https://ecm.company.com/license"));
  }

  /**
   * External documentation link.
   */
  private ExternalDocumentation externalDocs() {
    return new ExternalDocumentation()
        .description("ECM Identity Service Documentation")
        .url("https://docs.ecm.company.com/identity");
  }

  /**
   * Bearer JWT security scheme for token-based authentication.
   */
  private SecurityScheme bearerScheme() {
    return new SecurityScheme()
        .type(SecurityScheme.Type.HTTP)
        .scheme("bearer")
        .bearerFormat("JWT")
        .description("JWT Bearer token authentication. Obtain token from /api/auth/sign-in endpoint.");
  }

  /**
   * OAuth2 security scheme with authorization code flow.
   */
  private SecurityScheme oauth2Scheme() {
    return new SecurityScheme()
        .type(SecurityScheme.Type.OAUTH2)
        .description("OAuth2 Authentication with Authorization Code flow (PKCE supported)")
        .flows(new OAuthFlows()
            .authorizationCode(new OAuthFlow()
                .authorizationUrl(authorizationUrl)
                .tokenUrl(tokenUrl)
                .refreshUrl(refreshUrl)
                .scopes(new Scopes()
                    .addString("read", "Read access to resources")
                    .addString("write", "Write access to resources")
                    .addString("admin", "Administrative access")
                    .addString("openid", "OpenID Connect scope")
                    .addString("profile", "User profile information")
                    .addString("email", "User email address"))));
  }

  // ============================================================
  // API GROUPS - Module-based organization
  // ============================================================

  /**
   * User Management API group.
   */
  @Bean
  public GroupedOpenApi usersApi() {
    return GroupedOpenApi.builder()
        .group("users")
        .displayName("👤 User Management")
        .pathsToMatch("/api/users/**")
        .addOpenApiCustomizer(openApi -> openApi.info(
            new Info()
                .title("User Management API")
                .version("1.0.0")
                .description("API for managing user accounts, profiles, and credentials.")))
        .build();
  }

  /**
   * Role Management API group.
   */
  @Bean
  public GroupedOpenApi rolesApi() {
    return GroupedOpenApi.builder()
        .group("roles")
        .displayName("🔐 Role Management")
        .pathsToMatch("/api/roles/**")
        .addOpenApiCustomizer(openApi -> openApi.info(
            new Info()
                .title("Role Management API")
                .version("1.0.0")
                .description("API for managing roles, permissions, and access control.")))
        .build();
  }

  /**
   * Authentication API group.
   */
  @Bean
  public GroupedOpenApi authApi() {
    return GroupedOpenApi.builder()
        .group("auth")
        .displayName("🔑 Authentication")
        .pathsToMatch("/api/auth/**")
        .addOpenApiCustomizer(openApi -> openApi.info(
            new Info()
                .title("Authentication API")
                .version("1.0.0")
                .description("API for authentication flows: sign-in, sign-up, token refresh, password reset.")))
        .build();
  }

  /**
   * System/Admin API group - includes actuator and admin endpoints.
   */
  @Bean
  public GroupedOpenApi systemApi() {
    return GroupedOpenApi.builder()
        .group("system")
        .displayName("⚙️ System & Admin")
        .pathsToMatch("/api/admin/**", "/actuator/**")
        .addOpenApiCustomizer(openApi -> openApi.info(
            new Info()
                .title("System Administration API")
                .version("1.0.0")
                .description("API for system administration, monitoring, and health checks.")))
        .build();
  }

  /**
   * Complete API - all endpoints in one view.
   */
  @Bean
  public GroupedOpenApi allApi() {
    return GroupedOpenApi.builder()
        .group("all")
        .displayName("📚 Complete API")
        .pathsToMatch("/api/**")
        .build();
  }
}
