package com.ecm.security.identity.role.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecm.security.identity.role.domain.Role;
import com.ecm.security.identity.role.entity.RoleEntity;
import com.ecm.security.identity.role.mapper.RoleMapper;
import com.ecm.security.identity.role.repository.RoleRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class RoleService {
  private final RoleRepository roleRepository;
  private final RoleMapper roleMapper;

  // @PreAuthorize("hasRole('ADMIN')")
  public List<Role> getAllRoles() {
    return roleRepository.findAll().stream()
        .map(roleMapper::toDomain)
        .toList();
  }

  // @PreAuthorize("hasRole('ADMIN')")
  public Role createRole(String name, String description) {
    if (roleRepository.existsByName(name)) {
      throw new RuntimeException("Role already exists: " + name);
    }

    Role role = Role.builder()
      .name(name)
      .description(description)
      .createdBy("system")
      .updatedBy("system")
      .build();

    RoleEntity roleEntity = Objects.requireNonNull(roleMapper.toEntity(role));
    RoleEntity savedEntity = roleRepository.save(roleEntity);

    return roleMapper.toDomain(savedEntity);
  }
}
