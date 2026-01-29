package com.ecm.security.identity.user.controller;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.ecm.security.identity.shared.RoleLookup;
import com.ecm.security.identity.user.controller.dto.ChangePasswordRequest;
import com.ecm.security.identity.user.controller.dto.CreateUserRequest;
import com.ecm.security.identity.user.controller.dto.RoleChangeRequest;
import com.ecm.security.identity.user.controller.dto.UpdateUserRequest;
import com.ecm.security.identity.user.controller.dto.UserResponse;
import com.ecm.security.identity.user.domain.User;
import com.ecm.security.identity.user.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST Controller for managing user accounts.
 * 
 * <p>
 * Provides CRUD operations for user management including:
 * <ul>
 * <li>User creation, retrieval, update, and deletion</li>
 * <li>Password management</li>
 * <li>Role assignment and removal</li>
 * </ul>
 * 
 * <p>
 * All endpoints require authentication. Administrative operations
 * require the ADMIN role.
 */
@Tag(name = "Users", description = "User account management operations")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearer-jwt")
@SuppressWarnings("null")
public class UserController {

  private final UserService userService;
  private final RoleLookup roleLookup;

  // ========================================================================
  // READ Operations
  // ========================================================================

  @Operation(summary = "List all users", description = "Retrieves a list of all user accounts in the system. " +
      "Returns all users without pagination. For large systems, " +
      "consider using pagination parameters (future enhancement).")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successfully retrieved user list", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse.class))),
      @ApiResponse(responseCode = "401", description = "Unauthorized - authentication required"),
      @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions")
  })
  @GetMapping
  public List<UserResponse> listUsers() {
    return userService.getAllUsers().stream()
        .map(this::toResponse)
        .toList();
  }

  @Operation(summary = "Get user by ID", description = "Retrieves a specific user account by their unique identifier (UUID).")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "User found and returned", content = @Content(schema = @Schema(implementation = UserResponse.class))),
      @ApiResponse(responseCode = "404", description = "User not found"),
      @ApiResponse(responseCode = "401", description = "Unauthorized")
  })
  @GetMapping("/{id}")
  public UserResponse getUserById(
      @Parameter(description = "Unique user identifier", example = "123e4567-e89b-12d3-a456-426614174000") @PathVariable UUID id) {
    User user = userService.getUserById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
    return toResponse(user);
  }

  @Operation(summary = "Get user by username", description = "Retrieves a user account by their unique username.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "User found"),
      @ApiResponse(responseCode = "404", description = "User not found")
  })
  @GetMapping("/username/{username}")
  public UserResponse getUserByUsername(
      @Parameter(description = "Username to search for", example = "john.doe") @PathVariable String username) {
    User user = userService.getUserByUsername(username)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + username));
    return toResponse(user);
  }

  @Operation(summary = "Get user by email", description = "Retrieves a user account by their email address.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "User found"),
      @ApiResponse(responseCode = "404", description = "User not found")
  })
  @GetMapping("/email/{email}")
  public UserResponse getUserByEmail(
      @Parameter(description = "Email address to search for", example = "john@example.com") @PathVariable String email) {
    User user = userService.getUserByEmail(email)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + email));
    return toResponse(user);
  }

  // ========================================================================
  // WRITE Operations (Admin Only)
  // ========================================================================

  @Operation(summary = "Create new user", description = "Creates a new user account. Requires ADMIN role. " +
      "The username and email must be unique across the system.")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "User created successfully", headers = @Header(name = "Location", description = "URL of the created user resource"), content = @Content(schema = @Schema(implementation = UserResponse.class))),
      @ApiResponse(responseCode = "400", description = "Invalid request body"),
      @ApiResponse(responseCode = "409", description = "Conflict - username or email already exists"),
      @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role")
  })
  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<UserResponse> createUser(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "User account details", required = true, content = @Content(schema = @Schema(implementation = CreateUserRequest.class))) @Valid @RequestBody CreateUserRequest request) {
    try {
      User created = userService.createUser(
          request.getUsername(),
          request.getEmail(),
          request.getPassword(),
          request.getFirstName(),
          request.getLastName());

      URI location = URI.create(String.format("/api/users/%s", created.getId()));
      return ResponseEntity.created(location)
          .body(toResponse(created));
    } catch (RuntimeException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
    }
  }

  @Operation(summary = "Update user profile", description = "Updates an existing user's profile information. Requires ADMIN role. "
      +
      "Only provided fields will be updated (partial update).")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "User updated successfully"),
      @ApiResponse(responseCode = "404", description = "User not found"),
      @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role")
  })
  @PatchMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public UserResponse updateUser(
      @Parameter(description = "User ID to update") @PathVariable UUID id,
      @Valid @RequestBody UpdateUserRequest request) {
    try {
      User updated = userService.updateUser(
          id,
          request.getFirstName(),
          request.getLastName(),
          request.getEnabled(),
          request.getAccountLocked());
      return toResponse(updated);
    } catch (RuntimeException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    }
  }

  @Operation(summary = "Change user password", description = "Changes the password for a specific user account. Requires ADMIN role. "
      +
      "The new password will be securely hashed before storage.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Password changed successfully"),
      @ApiResponse(responseCode = "404", description = "User not found"),
      @ApiResponse(responseCode = "400", description = "Invalid password format"),
      @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role")
  })
  @PatchMapping("/{id}/password")
  @PreAuthorize("hasRole('ADMIN')")
  public UserResponse changePassword(
      @Parameter(description = "User ID whose password to change") @PathVariable UUID id,
      @Valid @RequestBody ChangePasswordRequest request) {
    try {
      User updated = userService.changePassword(id, request.getNewPassword());
      return toResponse(updated);
    } catch (RuntimeException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    }
  }

  // ========================================================================
  // Role Management (Admin Only)
  // ========================================================================

  @Operation(summary = "Add role to user", description = "Assigns a role to a user account. Requires ADMIN role. " +
      "The role must exist in the system.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Role added successfully"),
      @ApiResponse(responseCode = "404", description = "User or role not found"),
      @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role")
  })
  @PostMapping("/{id}/roles")
  @PreAuthorize("hasRole('ADMIN')")
  public UserResponse addRole(
      @Parameter(description = "User ID to add role to") @PathVariable UUID id,
      @Valid @RequestBody RoleChangeRequest request) {
    try {
      User updated = userService.addRoleToUser(id, request.getRoleName());
      return toResponse(updated);
    } catch (RuntimeException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    }
  }

  @Operation(summary = "Remove role from user", description = "Removes a role from a user account. Requires ADMIN role.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Role removed successfully"),
      @ApiResponse(responseCode = "404", description = "User or role not found"),
      @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role")
  })
  @DeleteMapping("/{id}/roles/{roleName}")
  @PreAuthorize("hasRole('ADMIN')")
  public UserResponse removeRole(
      @Parameter(description = "User ID to remove role from") @PathVariable UUID id,
      @Parameter(description = "Name of the role to remove", example = "ADMIN") @PathVariable String roleName) {
    try {
      User updated = userService.removeRoleFromUser(id, roleName);
      return toResponse(updated);
    } catch (RuntimeException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    }
  }

  // ========================================================================
  // DELETE Operations (Admin Only)
  // ========================================================================

  @Operation(summary = "Delete user", description = "Permanently deletes a user account. Requires ADMIN role. " +
      "This action cannot be undone.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "User deleted successfully"),
      @ApiResponse(responseCode = "404", description = "User not found"),
      @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role")
  })
  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> deleteUser(
      @Parameter(description = "User ID to delete") @PathVariable UUID id) {
    try {
      userService.deleteUser(id);
      return ResponseEntity.noContent().build();
    } catch (RuntimeException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    }
  }

  // ========================================================================
  // Helper Methods
  // ========================================================================

  private UserResponse toResponse(User user) {
    Set<String> roles = user.getRoleIds() == null || user.getRoleIds().isEmpty()
        ? Collections.emptySet()
        : roleLookup.findRoleNamesByIds(user.getRoleIds());

    return UserResponse.builder()
        .id(user.getId())
        .username(user.getUsername())
        .email(user.getEmail())
        .firstName(user.getFirstName())
        .lastName(user.getLastName())
        .enabled(user.getEnabled())
        .accountLocked(user.getAccountLocked())
        .accountExpired(user.getAccountExpired())
        .credentialsExpired(user.getCredentialsExpired())
        .roles(roles)
        .createdAt(user.getCreatedAt())
        .updatedAt(user.getUpdatedAt())
        .createdBy(user.getCreatedBy())
        .updatedBy(user.getUpdatedBy())
        .build();
  }
}
