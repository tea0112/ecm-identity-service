package com.ecm.security.identity.user.controller.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UserResponse {
  @EqualsAndHashCode.Include
  private UUID id;

  private String username;
  private String email;
  private String firstName;
  private String lastName;

  private Boolean enabled;
  private Boolean accountLocked;
  private Boolean accountExpired;
  private Boolean credentialsExpired;

  private Set<String> roles;

  private Instant createdAt;
  private Instant updatedAt;
  private String createdBy;
  private String updatedBy;
}
