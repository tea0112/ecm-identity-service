package com.ecm.security.identity.user.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for changing a user's password.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for changing a user's password")
public class ChangePasswordRequest {

  @NotBlank(message = "New password is required")
  @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
  @Schema(description = "The new password for the user account", example = "NewSecureP@ss123!", requiredMode = Schema.RequiredMode.REQUIRED, minLength = 8, maxLength = 100, format = "password")
  private String newPassword;
}
