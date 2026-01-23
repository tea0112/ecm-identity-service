package com.ecm.security.identity.user.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {
  @NotBlank
  private String username;

  @Email
  @NotBlank
  private String email;

  @NotBlank
  private String password;

  private String firstName;
  private String lastName;
}
