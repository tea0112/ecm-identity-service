package com.ecm.security.identity.role.mapper;

import org.springframework.stereotype.Component;

import com.ecm.security.identity.role.domain.Role;
import com.ecm.security.identity.role.entity.RoleEntity;

@Component
public class RoleMapper {
  public Role toDomain(RoleEntity entity) {
    if (entity == null) {
      return null;
    }

    return Role.builder()
        .id(entity.getId())
        .name(entity.getName())
        .description(entity.getDescription())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .createdBy(entity.getCreatedBy())
        .updatedBy(entity.getUpdatedBy())
        .build();
  }

  public RoleEntity toEntity(Role domain) {
    if (domain == null) {
      throw new IllegalArgumentException("domain role must not be null");
    }

    return RoleEntity.builder()
        .id(domain.getId())
        .name(domain.getName())
        .description(domain.getDescription())
        .createdAt(domain.getCreatedAt())
        .updatedAt(domain.getUpdatedAt())
        .createdBy(domain.getCreatedBy())
        .updatedBy(domain.getUpdatedBy())
        .build();
  }
}
