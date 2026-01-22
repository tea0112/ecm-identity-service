package com.ecm.security.identity.role.domain;

import java.time.Instant;
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
public class Role {

  @EqualsAndHashCode.Include
  private UUID id;

  private String name;

  private String description;

  private Instant createdAt;
  private Instant updatedAt;
  private String createdBy;
  private String updatedBy;
}
