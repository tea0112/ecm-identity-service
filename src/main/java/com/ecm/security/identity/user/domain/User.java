package com.ecm.security.identity.user.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class User {

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

  private Set<UUID> roleIds;

  private Instant createdAt;
  private Instant updatedAt;
  private String createdBy;
  private String updatedBy;
}
