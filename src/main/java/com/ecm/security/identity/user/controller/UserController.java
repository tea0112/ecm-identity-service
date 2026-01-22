package com.ecm.security.identity.user.controller;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.ecm.security.identity.user.controller.dto.ChangePasswordRequest;
import com.ecm.security.identity.user.controller.dto.CreateUserRequest;
import com.ecm.security.identity.user.controller.dto.RoleChangeRequest;
import com.ecm.security.identity.user.controller.dto.UpdateUserRequest;
import com.ecm.security.identity.user.controller.dto.UserResponse;
import com.ecm.security.identity.user.domain.User;
import com.ecm.security.identity.shared.RoleLookup;
import com.ecm.security.identity.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
  private final UserService userService;
  private final RoleLookup roleLookup;

  @GetMapping
  public List<UserResponse> listUsers() {
    return userService.getAllUsers().stream()
        .map(this::toResponse)
        .toList();
  }

  @GetMapping("/{id}")
  public UserResponse getUserById(@PathVariable UUID id) {
    User user = userService.getUserById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
    return toResponse(user);
  }

  @GetMapping("/username/{username}")
  public UserResponse getUserByUsername(@PathVariable String username) {
    User user = userService.getUserByUsername(username)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + username));
    return toResponse(user);
  }

  @GetMapping("/email/{email}")
  public UserResponse getUserByEmail(@PathVariable String email) {
    User user = userService.getUserByEmail(email)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + email));
    return toResponse(user);
  }

  @PostMapping
  public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
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

  @PatchMapping("/{id}")
  public UserResponse updateUser(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
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

  @PatchMapping("/{id}/password")
  public UserResponse changePassword(@PathVariable UUID id, @Valid @RequestBody ChangePasswordRequest request) {
    try {
      User updated = userService.changePassword(id, request.getNewPassword());
      return toResponse(updated);
    } catch (RuntimeException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    }
  }

  @PostMapping("/{id}/roles")
  public UserResponse addRole(@PathVariable UUID id, @Valid @RequestBody RoleChangeRequest request) {
    try {
      User updated = userService.addRoleToUser(id, request.getRoleName());
      return toResponse(updated);
    } catch (RuntimeException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    }
  }

  @DeleteMapping("/{id}/roles/{roleName}")
  public UserResponse removeRole(@PathVariable UUID id, @PathVariable String roleName) {
    try {
      User updated = userService.removeRoleFromUser(id, roleName);
      return toResponse(updated);
    } catch (RuntimeException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    }
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
    try {
      userService.deleteUser(id);
      return ResponseEntity.noContent().build();
    } catch (RuntimeException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    }
  }

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
