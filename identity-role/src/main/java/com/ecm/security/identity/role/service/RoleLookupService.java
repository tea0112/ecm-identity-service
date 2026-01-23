package com.ecm.security.identity.role.service;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Service;

import com.ecm.security.identity.role.repository.RoleRepository;
import com.ecm.security.identity.shared.RoleLookup;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoleLookupService implements RoleLookup {
  private final RoleRepository roleRepository;

  @Override
  public Set<String> findRoleNamesByIds(Set<UUID> roleIds) {
    if (roleIds == null || roleIds.isEmpty()) {
      return Collections.emptySet();
    }

    return StreamSupport.stream(roleRepository.findAllById(roleIds).spliterator(), false)
        .map(role -> role.getName())
        .collect(Collectors.toSet());
  }

  @Override
  public Optional<UUID> findRoleIdByName(String roleName) {
    return roleRepository.findByName(roleName)
        .map(role -> role.getId());
  }
}
