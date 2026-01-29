package com.ecm.security.identity.user.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for creating a new user account.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for creating a new user account")
public class CreateUserRequest {

  @NotBlank(message = "Username is required")
  @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
  @Schema(description = "Unique username for the account", example = "john.doe", requiredMode = Schema.RequiredMode.REQUIRED, minLength = 3, maxLength = 50)
  private String username;

  @Email(message = "Invalid email format")
  @NotBlank(message = "Email is required")
  @Schema(description = "User's email address (must be unique)", example = "john.doe@example.com", requiredMode = Schema.RequiredMode.REQUIRED, format = "email")
  private String email;

  @NotBlank(message = "Password is required")
  @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
  @Schema(description = "Account password (will be hashed)", example = "SecureP@ssw0rd!", requiredMode = Schema.RequiredMode.REQUIRED, minLength = 8, maxLength = 100, format = "password")
  private String password;

  @Size(max = 100, message = "First name cannot exceed 100 characters")
  @Schema(description = "User's first name", example = "John", maxLength = 100)
  private String firstName;

  @Size(max = 100, message = "Last name cannot exceed 100 characters")
  @Schema(description = "User's last name", example = "Doe", maxLength = 100)
  private String lastName;
}
