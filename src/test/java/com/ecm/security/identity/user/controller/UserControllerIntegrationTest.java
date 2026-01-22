package com.ecm.security.identity.user.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.ecm.security.identity.shared.RoleLookup;
import com.ecm.security.identity.user.domain.User;
import com.ecm.security.identity.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private UserService userService;

  @MockBean
  private RoleLookup roleLookup;

  @Test
  void listUsersReturnsUsers() throws Exception {
    UUID roleId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID userWithRoleId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    UUID userWithoutRoleId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    User userWithRole = buildUser(userWithRoleId, "user-a", "user-a@example.com", Set.of(roleId));
    User userWithoutRole = buildUser(userWithoutRoleId, "user-b", "user-b@example.com", Collections.emptySet());
    Instant createdAt = Instant.parse("2024-01-02T03:04:05Z");

    when(userService.getAllUsers()).thenReturn(List.of(userWithRole, userWithoutRole));
    when(roleLookup.findRoleNamesByIds(eq(Set.of(roleId)))).thenReturn(Set.of("ADMIN"));

    mockMvc.perform(get("/api/users"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(userWithRoleId.toString()))
        .andExpect(jsonPath("$[0].username").value("user-a"))
        .andExpect(jsonPath("$[0].roles[0]").value("ADMIN"))
        .andExpect(jsonPath("$[0].createdAt").value(createdAt.toString()))
        .andExpect(jsonPath("$[1].id").value(userWithoutRoleId.toString()))
        .andExpect(jsonPath("$[1].roles.length()").value(0));
  }

  @Test
  void getUserByIdReturnsUser() throws Exception {
    UUID userId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    UUID roleId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    User user = buildUser(userId, "user-c", "user-c@example.com", Set.of(roleId));

    when(userService.getUserById(userId)).thenReturn(java.util.Optional.of(user));
    when(roleLookup.findRoleNamesByIds(eq(Set.of(roleId)))).thenReturn(Set.of("USER"));

    mockMvc.perform(get("/api/users/{id}", userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId.toString()))
        .andExpect(jsonPath("$.email").value("user-c@example.com"))
        .andExpect(jsonPath("$.roles[0]").value("USER"));
  }

  @Test
  void getUserByIdNotFound() throws Exception {
    UUID userId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    when(userService.getUserById(userId)).thenReturn(java.util.Optional.empty());

    mockMvc.perform(get("/api/users/{id}", userId))
        .andExpect(status().isNotFound());
  }

  @Test
  void getUserByUsernameReturnsUser() throws Exception {
    UUID userId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    User user = buildUser(userId, "user-d", "user-d@example.com", Collections.emptySet());

    when(userService.getUserByUsername("alice")).thenReturn(java.util.Optional.of(user));

    mockMvc.perform(get("/api/users/username/{username}", "alice"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId.toString()))
        .andExpect(jsonPath("$.roles.length()").value(0));
  }

  @Test
  void getUserByUsernameNotFound() throws Exception {
    when(userService.getUserByUsername("missing")).thenReturn(java.util.Optional.empty());

    mockMvc.perform(get("/api/users/username/{username}", "missing"))
        .andExpect(status().isNotFound());
  }

  @Test
  void getUserByEmailReturnsUser() throws Exception {
    UUID userId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    User user = buildUser(userId, "user-e", "user-e@example.com", Collections.emptySet());

    when(userService.getUserByEmail("user@example.com")).thenReturn(java.util.Optional.of(user));

    mockMvc.perform(get("/api/users/email/{email}", "user@example.com"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId.toString()))
        .andExpect(jsonPath("$.email").value("user-e@example.com"));
  }

  @Test
  void getUserByEmailNotFound() throws Exception {
    when(userService.getUserByEmail("missing@example.com")).thenReturn(java.util.Optional.empty());

    mockMvc.perform(get("/api/users/email/{email}", "missing@example.com"))
        .andExpect(status().isNotFound());
  }

  @Test
  void createUserReturnsCreated() throws Exception {
    UUID userId = UUID.fromString("11111111-2222-3333-4444-555555555555");
    User created = buildUser(userId, "jdoe", "jdoe@example.com", Collections.emptySet());
    ObjectNode request = objectMapper.createObjectNode();
    request.put("username", "jdoe");
    request.put("email", "jdoe@example.com");
    request.put("password", "password");
    request.put("firstName", "John");
    request.put("lastName", "Doe");

    when(userService.createUser("jdoe", "jdoe@example.com", "password", "John", "Doe"))
        .thenReturn(created);

    mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/users/" + userId))
        .andExpect(jsonPath("$.id").value(userId.toString()))
        .andExpect(jsonPath("$.username").value("jdoe"));
  }

  @Test
  void createUserConflict() throws Exception {
    ObjectNode request = objectMapper.createObjectNode();
    request.put("username", "jdoe");
    request.put("email", "jdoe@example.com");
    request.put("password", "password");

    when(userService.createUser("jdoe", "jdoe@example.com", "password", null, null))
        .thenThrow(new RuntimeException("Username already exists: jdoe"));

    mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict());
  }

  @Test
  void createUserValidationError() throws Exception {
    ObjectNode request = objectMapper.createObjectNode();
    request.put("username", "");
    request.put("email", "not-an-email");
    request.put("password", "");

    mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateUserReturnsUpdated() throws Exception {
    UUID userId = UUID.fromString("66666666-6666-6666-6666-666666666666");
    User updated = buildUser(userId, "user-f", "user-f@example.com", Collections.emptySet());
    ObjectNode request = objectMapper.createObjectNode();
    request.put("firstName", "Updated");
    request.put("lastName", "Name");
    request.put("enabled", true);
    request.put("accountLocked", false);

    when(userService.updateUser(userId, "Updated", "Name", true, false)).thenReturn(updated);

    mockMvc.perform(patch("/api/users/{id}", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId.toString()));
  }

  @Test
  void updateUserNotFound() throws Exception {
    UUID userId = UUID.fromString("77777777-7777-7777-7777-777777777777");
    ObjectNode request = objectMapper.createObjectNode();
    request.put("firstName", "Updated");

    when(userService.updateUser(userId, "Updated", null, null, null))
        .thenThrow(new RuntimeException("User not found: " + userId));

    mockMvc.perform(patch("/api/users/{id}", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound());
  }

  @Test
  void changePasswordReturnsUpdated() throws Exception {
    UUID userId = UUID.fromString("88888888-8888-8888-8888-888888888888");
    User updated = buildUser(userId, "user-g", "user-g@example.com", Collections.emptySet());
    ObjectNode request = objectMapper.createObjectNode();
    request.put("newPassword", "new-password");

    when(userService.changePassword(userId, "new-password")).thenReturn(updated);

    mockMvc.perform(patch("/api/users/{id}/password", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId.toString()));
  }

  @Test
  void changePasswordNotFound() throws Exception {
    UUID userId = UUID.fromString("99999999-9999-9999-9999-999999999999");
    ObjectNode request = objectMapper.createObjectNode();
    request.put("newPassword", "new-password");

    when(userService.changePassword(userId, "new-password"))
        .thenThrow(new RuntimeException("User not found: " + userId));

    mockMvc.perform(patch("/api/users/{id}/password", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound());
  }

  @Test
  void changePasswordValidationError() throws Exception {
    ObjectNode request = objectMapper.createObjectNode();
    request.put("newPassword", "");

    mockMvc.perform(patch("/api/users/{id}/password", UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void addRoleReturnsUpdated() throws Exception {
    UUID userId = UUID.fromString("aaaaaaaa-1111-2222-3333-444444444444");
    UUID roleId = UUID.fromString("bbbbbbbb-1111-2222-3333-444444444444");
    User updated = buildUser(userId, "user-h", "user-h@example.com", Set.of(roleId));
    ObjectNode request = objectMapper.createObjectNode();
    request.put("roleName", "ADMIN");

    when(userService.addRoleToUser(userId, "ADMIN")).thenReturn(updated);
    when(roleLookup.findRoleNamesByIds(eq(Set.of(roleId)))).thenReturn(Set.of("ADMIN"));

    mockMvc.perform(post("/api/users/{id}/roles", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId.toString()))
        .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
  }

  @Test
  void addRoleNotFound() throws Exception {
    UUID userId = UUID.fromString("aaaaaaaa-5555-6666-7777-888888888888");
    ObjectNode request = objectMapper.createObjectNode();
    request.put("roleName", "ADMIN");

    when(userService.addRoleToUser(userId, "ADMIN"))
        .thenThrow(new RuntimeException("User not found: " + userId));

    mockMvc.perform(post("/api/users/{id}/roles", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound());
  }

  @Test
  void addRoleValidationError() throws Exception {
    ObjectNode request = objectMapper.createObjectNode();
    request.put("roleName", "");

    mockMvc.perform(post("/api/users/{id}/roles", UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void removeRoleReturnsUpdated() throws Exception {
    UUID userId = UUID.fromString("aaaaaaaa-9999-9999-9999-999999999999");
    User updated = buildUser(userId, "user-i", "user-i@example.com", Collections.emptySet());

    when(userService.removeRoleFromUser(userId, "ADMIN")).thenReturn(updated);

    mockMvc.perform(delete("/api/users/{id}/roles/{roleName}", userId, "ADMIN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId.toString()))
        .andExpect(jsonPath("$.roles.length()").value(0));
  }

  @Test
  void removeRoleNotFound() throws Exception {
    UUID userId = UUID.fromString("bbbbbbbb-9999-9999-9999-999999999999");

    when(userService.removeRoleFromUser(userId, "ADMIN"))
        .thenThrow(new RuntimeException("User not found: " + userId));

    mockMvc.perform(delete("/api/users/{id}/roles/{roleName}", userId, "ADMIN"))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteUserNoContent() throws Exception {
    UUID userId = UUID.fromString("cccccccc-9999-9999-9999-999999999999");

    mockMvc.perform(delete("/api/users/{id}", userId))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteUserNotFound() throws Exception {
    UUID userId = UUID.fromString("dddddddd-9999-9999-9999-999999999999");

    doThrow(new RuntimeException("User not found: " + userId))
        .when(userService).deleteUser(userId);

    mockMvc.perform(delete("/api/users/{id}", userId))
        .andExpect(status().isNotFound());
  }

  private User buildUser(UUID id, String username, String email, Set<UUID> roleIds) {
    Instant createdAt = Instant.parse("2024-01-02T03:04:05Z");
    Instant updatedAt = Instant.parse("2024-01-02T04:05:06Z");
    User user = new User();

    ReflectionTestUtils.setField(user, "id", id);
    ReflectionTestUtils.setField(user, "username", username);
    ReflectionTestUtils.setField(user, "email", email);
    ReflectionTestUtils.setField(user, "firstName", "First");
    ReflectionTestUtils.setField(user, "lastName", "Last");
    ReflectionTestUtils.setField(user, "enabled", true);
    ReflectionTestUtils.setField(user, "accountLocked", false);
    ReflectionTestUtils.setField(user, "accountExpired", false);
    ReflectionTestUtils.setField(user, "credentialsExpired", false);
    ReflectionTestUtils.setField(user, "roleIds", roleIds);
    ReflectionTestUtils.setField(user, "createdAt", createdAt);
    ReflectionTestUtils.setField(user, "updatedAt", updatedAt);
    ReflectionTestUtils.setField(user, "createdBy", "system");
    ReflectionTestUtils.setField(user, "updatedBy", "system");

    return user;
  }
}
