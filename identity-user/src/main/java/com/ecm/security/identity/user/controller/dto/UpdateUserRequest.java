package com.ecm.security.identity.user.controller.dto;

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
public class UpdateUserRequest {
  private String firstName;
  private String lastName;
  private Boolean enabled;
  private Boolean accountLocked;
}
