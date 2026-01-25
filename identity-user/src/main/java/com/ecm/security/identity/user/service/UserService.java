package com.ecm.security.identity.user.service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecm.security.identity.shared.RoleLookup;
import com.ecm.security.identity.user.domain.User;
import com.ecm.security.identity.user.entity.UserEntity;
import com.ecm.security.identity.user.entity.UserRoleEntity;
import com.ecm.security.identity.user.entity.UserRoleId;
import com.ecm.security.identity.user.mapper.UserMapper;
import com.ecm.security.identity.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
@SuppressWarnings("null")
public class UserService {
  private final UserRepository userRepository;
  private final RoleLookup roleLookup;
  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;

  public List<User> getAllUsers() {
    return userRepository.findAll().stream()
        .map(userMapper::toDomain)
        .toList();
  }

  public Optional<User> getUserById(UUID id) {
    return userRepository.findById(id)
        .map(userMapper::toDomain);
  }

  public Optional<User> getUserByUsername(String username) {
    return userRepository.findByUsernameWithRoles(username)
        .map(userMapper::toDomain);
  }

  public Optional<User> getUserByEmail(String email) {
    return userRepository.findByEmail(email)
        .map(userMapper::toDomain);
  }

  public User createUser(String username, String email, String password, String firstName, String lastName) {
    if (userRepository.existsByUsername(username)) {
      throw new RuntimeException("Username already exists: " + username);
    }

    if (userRepository.existsByEmail(email)) {
      throw new RuntimeException("Email already exists: " + email);
    }

    String encodedPassword = passwordEncoder.encode(password);

    User user = User.builder()
        .username(username)
        .email(email)
        .firstName(firstName)
        .lastName(lastName)
        .enabled(true)
        .accountLocked(false)
        .accountExpired(false)
        .credentialsExpired(false)
        .createdBy("system")
        .updatedBy("system")
        .build();

    UserEntity userEntity = Objects.requireNonNull(userMapper.toEntity(user));
    userEntity.setPasswordHash(encodedPassword);

    UserEntity savedEntity = userRepository.save(userEntity);

    return userMapper.toDomain(savedEntity);
  }

  public User updateUser(UUID id, String firstName, String lastName, Boolean enabled, Boolean accountLocked) {
    UserEntity userEntity = userRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("User not found: " + id));

    if (firstName != null) {
      userEntity.setFirstName(firstName);
    }
    if (lastName != null) {
      userEntity.setLastName(lastName);
    }
    if (enabled != null) {
      userEntity.setEnabled(enabled);
    }
    if (accountLocked != null) {
      userEntity.setAccountLocked(accountLocked);
    }

    UserEntity updatedEntity = userRepository.save(userEntity);

    return userMapper.toDomain(updatedEntity);
  }

  public User changePassword(UUID id, String newPassword) {
    UserEntity userEntity = userRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("User not found: " + id));

    String encodedPassword = passwordEncoder.encode(newPassword);
    userEntity.setPasswordHash(encodedPassword);

    UserEntity updatedEntity = userRepository.save(userEntity);

    return userMapper.toDomain(updatedEntity);
  }

  public User addRoleToUser(UUID userId, String roleName) {
    UserEntity userEntity = userRepository.findById(userId)
        .orElseThrow(() -> new RuntimeException("User not found: " + userId));

    UUID roleId = roleLookup.findRoleIdByName(roleName)
        .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));

    addRoleAssignment(userEntity, roleId);

    UserEntity updatedEntity = userRepository.save(userEntity);

    return userMapper.toDomain(updatedEntity);
  }

  public User removeRoleFromUser(UUID userId, String roleName) {
    UserEntity userEntity = userRepository.findById(userId)
        .orElseThrow(() -> new RuntimeException("User not found: " + userId));

    UUID roleId = roleLookup.findRoleIdByName(roleName)
        .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));

    removeRoleAssignment(userEntity, roleId);

    UserEntity updatedEntity = userRepository.save(userEntity);

    return userMapper.toDomain(updatedEntity);
  }

  private void addRoleAssignment(UserEntity userEntity, UUID roleId) {
    Set<UserRoleEntity> roleAssignments = userEntity.getRoleAssignments();
    if (roleAssignments == null) {
      roleAssignments = new HashSet<>();
      userEntity.setRoleAssignments(roleAssignments);
    }

    UserRoleEntity assignment = UserRoleEntity.builder()
        .id(new UserRoleId(userEntity.getId(), roleId))
        .user(userEntity)
        .build();

    roleAssignments.add(assignment);
  }

  private void removeRoleAssignment(UserEntity userEntity, UUID roleId) {
    Set<UserRoleEntity> roleAssignments = userEntity.getRoleAssignments();
    if (roleAssignments != null) {
      roleAssignments.removeIf(
          assignment -> assignment.getId() != null && roleId.equals(assignment.getId().getRoleId()));
    }
  }

  public void deleteUser(UUID id) {
    if (!userRepository.existsById(id)) {
      throw new RuntimeException("User not found: " + id);
    }
    userRepository.deleteById(id);
  }
}
