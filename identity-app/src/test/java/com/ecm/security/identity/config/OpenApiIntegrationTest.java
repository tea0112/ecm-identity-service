package com.ecm.security.identity.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for OpenAPI/Swagger documentation endpoints.
 * 
 * Verifies that:
 * - OpenAPI spec is generated correctly
 * - Swagger UI is accessible
 * - API groups are configured properly
 * - Security schemes are documented
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("OpenAPI Documentation Integration Tests")
class OpenApiIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  @DisplayName("Should return OpenAPI spec at /v3/api-docs")
  void shouldReturnOpenApiSpec() throws Exception {
    mockMvc.perform(get("/v3/api-docs")
        .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.openapi", startsWith("3.")))
        .andExpect(jsonPath("$.info.title", is("ECM Identity Service API")))
        .andExpect(jsonPath("$.info.version", is("1.0.0")));
  }

  @Test
  @DisplayName("Should return OpenAPI spec for users group")
  void shouldReturnUsersGroupSpec() throws Exception {
    mockMvc.perform(get("/v3/api-docs/users")
        .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title", is("User Management API")))
        .andExpect(jsonPath("$.paths", hasKey(containsString("/api/users"))));
  }

  @Test
  @DisplayName("Should return OpenAPI spec for auth group")
  void shouldReturnAuthGroupSpec() throws Exception {
    mockMvc.perform(get("/v3/api-docs/auth")
        .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title", is("Authentication API")));
  }

  @Test
  @DisplayName("Should return OpenAPI spec for roles group")
  void shouldReturnRolesGroupSpec() throws Exception {
    mockMvc.perform(get("/v3/api-docs/roles")
        .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title", is("Role Management API")));
  }

  @Test
  @DisplayName("Should include security schemes in OpenAPI spec")
  void shouldIncludeSecuritySchemes() throws Exception {
    mockMvc.perform(get("/v3/api-docs")
        .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components.securitySchemes.bearer-jwt.type", is("http")))
        .andExpect(jsonPath("$.components.securitySchemes.bearer-jwt.scheme", is("bearer")))
        .andExpect(jsonPath("$.components.securitySchemes.oauth2.type", is("oauth2")));
  }

  @Test
  @DisplayName("Should include user endpoints in spec")
  void shouldIncludeUserEndpoints() throws Exception {
    mockMvc.perform(get("/v3/api-docs/users")
        .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        // GET /api/users
        .andExpect(jsonPath("$.paths['/api/users'].get.summary", notNullValue()))
        // GET /api/users/{id}
        .andExpect(jsonPath("$.paths['/api/users/{id}'].get.summary", notNullValue()))
        // POST /api/users
        .andExpect(jsonPath("$.paths['/api/users'].post.summary", notNullValue()))
        // PATCH /api/users/{id}
        .andExpect(jsonPath("$.paths['/api/users/{id}'].patch.summary", notNullValue()))
        // DELETE /api/users/{id}
        .andExpect(jsonPath("$.paths['/api/users/{id}'].delete.summary", notNullValue()));
  }

  @Test
  @DisplayName("Swagger UI should be accessible")
  void swaggerUiShouldBeAccessible() throws Exception {
    mockMvc.perform(get("/swagger-ui/index.html"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Scalar UI should be accessible")
  void scalarUiShouldBeAccessible() throws Exception {
    mockMvc.perform(get("/scalar.html"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(containsString("ECM Identity Service")));
  }

  @Test
  @DisplayName("/docs should redirect to Scalar UI")
  void docsShouldRedirectToScalar() throws Exception {
    mockMvc.perform(get("/docs"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/scalar.html"));
  }

  @Test
  @DisplayName("/api-docs should redirect to Swagger UI")
  void apiDocsShouldRedirectToSwagger() throws Exception {
    mockMvc.perform(get("/api-docs"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/swagger-ui.html"));
  }

  @Test
  @DisplayName("OpenAPI spec should include request/response schemas")
  void shouldIncludeSchemas() throws Exception {
    mockMvc.perform(get("/v3/api-docs")
        .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components.schemas.CreateUserRequest", notNullValue()))
        .andExpect(jsonPath("$.components.schemas.UserResponse", notNullValue()))
        .andExpect(jsonPath("$.components.schemas.UpdateUserRequest", notNullValue()))
        .andExpect(jsonPath("$.components.schemas.ChangePasswordRequest", notNullValue()));
  }
}
