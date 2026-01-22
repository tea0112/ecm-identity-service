package com.ecm.security.identity.shared;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface RoleLookup {
  Set<String> findRoleNamesByIds(Set<UUID> roleIds);

  Optional<UUID> findRoleIdByName(String roleName);
}
