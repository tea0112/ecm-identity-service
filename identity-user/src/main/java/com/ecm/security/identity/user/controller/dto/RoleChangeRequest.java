package com.ecm.security.identity.user.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for adding or removing a role from a user.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for adding a role to a user")
public class RoleChangeRequest {

  @NotBlank(message = "Role name is required")
  @Schema(description = "The name of the role to add to the user", example = "ADMIN", requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
      "ADMIN", "USER", "MANAGER", "AUDITOR" })
  private String roleName;
}
