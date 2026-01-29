package com.ecm.security.identity.user.controller.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response payload containing user account details.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Schema(description = "User account details response")
public class UserResponse {

  @EqualsAndHashCode.Include
  @Schema(description = "Unique identifier for the user", example = "123e4567-e89b-12d3-a456-426614174000", format = "uuid")
  private UUID id;

  @Schema(description = "Unique username for the account", example = "john.doe")
  private String username;

  @Schema(description = "User's email address", example = "john.doe@example.com", format = "email")
  private String email;

  @Schema(description = "User's first name", example = "John")
  private String firstName;

  @Schema(description = "User's last name", example = "Doe")
  private String lastName;

  @Schema(description = "Whether the user account is enabled", example = "true")
  private Boolean enabled;

  @Schema(description = "Whether the user account is locked", example = "false")
  private Boolean accountLocked;

  @Schema(description = "Whether the user account has expired", example = "false")
  private Boolean accountExpired;

  @Schema(description = "Whether the user's credentials have expired", example = "false")
  private Boolean credentialsExpired;

  @Schema(description = "Set of role names assigned to the user", example = "[\"USER\", \"ADMIN\"]")
  private Set<String> roles;

  @Schema(description = "Timestamp when the user was created", example = "2024-01-15T10:30:00Z", format = "date-time")
  private Instant createdAt;

  @Schema(description = "Timestamp when the user was last updated", example = "2024-01-20T14:45:00Z", format = "date-time")
  private Instant updatedAt;

  @Schema(description = "Username of the user who created this account", example = "system")
  private String createdBy;

  @Schema(description = "Username of the user who last updated this account", example = "admin")
  private String updatedBy;
}
