package com.ecm.security.identity.user.mapper;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ecm.security.identity.user.domain.User;
import com.ecm.security.identity.user.entity.UserEntity;
import com.ecm.security.identity.user.entity.UserRoleEntity;
import com.ecm.security.identity.user.entity.UserRoleId;

@Component
public class UserMapper {

  public User toDomain(UserEntity entity) {
    if (entity == null) {
      return null;
    }

    Set<UUID> roleIds = null;
    if (entity.getRoleAssignments() != null) {
      roleIds = entity.getRoleAssignments().stream()
          .map(UserRoleEntity::getId)
          .map(UserRoleId::getRoleId)
          .collect(Collectors.toSet());
    }

    return User.builder()
        .id(entity.getId())
        .username(entity.getUsername())
        .email(entity.getEmail())
        // passwordHash intentionally NOT exposed to domain here
        .firstName(entity.getFirstName())
        .lastName(entity.getLastName())
        .enabled(entity.getEnabled())
        .accountLocked(entity.getAccountLocked())
        .accountExpired(entity.getAccountExpired())
        .credentialsExpired(entity.getCredentialsExpired())
        .roleIds(roleIds)
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .createdBy(entity.getCreatedBy())
        .updatedBy(entity.getUpdatedBy())
        .build();
  }

  public UserEntity toEntity(User domain) {
    if (domain == null) {
      return null;
    }

    UserEntity userEntity = UserEntity.builder()
        .id(domain.getId())
        .username(domain.getUsername())
        .email(domain.getEmail())
        // passwordHash should be set in the service layer after encoding the raw
        // password
        .firstName(domain.getFirstName())
        .lastName(domain.getLastName())
        .enabled(domain.getEnabled())
        .accountLocked(domain.getAccountLocked())
        .accountExpired(domain.getAccountExpired())
        .credentialsExpired(domain.getCredentialsExpired())
        .createdAt(domain.getCreatedAt())
        .updatedAt(domain.getUpdatedAt())
        .createdBy(domain.getCreatedBy())
        .updatedBy(domain.getUpdatedBy())
        .build();

    if (domain.getRoleIds() != null && domain.getId() != null) {
      Set<UserRoleEntity> roleAssignments = domain.getRoleIds().stream()
          .map(roleId -> UserRoleEntity.builder()
              .id(new UserRoleId(domain.getId(), roleId))
              .user(userEntity)
              .build())
          .collect(Collectors.toSet());
      userEntity.setRoleAssignments(roleAssignments);
    }

    return userEntity;
  }
}
