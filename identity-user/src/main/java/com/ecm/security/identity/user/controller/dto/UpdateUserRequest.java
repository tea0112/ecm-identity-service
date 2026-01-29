package com.ecm.security.identity.user.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for updating an existing user's profile.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for updating an existing user's profile. All fields are optional.")
public class UpdateUserRequest {

  @Schema(description = "User's first name", example = "John", maxLength = 100)
  private String firstName;

  @Schema(description = "User's last name", example = "Doe", maxLength = 100)
  private String lastName;

  @Schema(description = "Whether the user account is enabled. Disabled accounts cannot log in.", example = "true")
  private Boolean enabled;

  @Schema(description = "Whether the user account is locked. Locked accounts cannot log in.", example = "false")
  private Boolean accountLocked;
}
