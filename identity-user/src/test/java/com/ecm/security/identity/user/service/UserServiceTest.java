package com.ecm.security.identity.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ecm.security.identity.shared.RoleLookup;
import com.ecm.security.identity.user.domain.User;
import com.ecm.security.identity.user.entity.UserEntity;
import com.ecm.security.identity.user.mapper.UserMapper;
import com.ecm.security.identity.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private RoleLookup roleLookup;

  @Mock
  private UserMapper userMapper;

  @Mock
  private PasswordEncoder passwordEncoder;

  @InjectMocks
  private UserService userService;

  @Test
  void createUser_shouldSuccess_whenValidInput() {
    // Arrange
    String username = "testuser";
    String email = "test@example.com";
    String password = "password";
    String firstName = "Test";
    String lastName = "User";

    when(userRepository.existsByUsername(username)).thenReturn(false);
    when(userRepository.existsByEmail(email)).thenReturn(false);
    when(passwordEncoder.encode(password)).thenReturn("encodedPassword");

    UserEntity savedEntity = new UserEntity();
    savedEntity.setId(UUID.randomUUID());
    savedEntity.setUsername(username);

    // Mock the mapper's toEntity
    UserEntity mappedEntity = new UserEntity();
    when(userMapper.toEntity(any(User.class))).thenReturn(mappedEntity);

    when(userRepository.save(any(UserEntity.class))).thenReturn(savedEntity);

    User domainUser = User.builder().id(savedEntity.getId()).username(username).build();
    when(userMapper.toDomain(savedEntity)).thenReturn(domainUser);

    // Act
    User result = userService.createUser(username, email, password, firstName, lastName);

    // Assert
    assertNotNull(result);
    assertEquals(username, result.getUsername());
    verify(userRepository).save(any(UserEntity.class));
  }

  @Test
  void createUser_shouldThrow_whenUsernameExists() {
    // Arrange
    String username = "existing";
    when(userRepository.existsByUsername(username)).thenReturn(true);

    // Act & Assert
    RuntimeException exception = assertThrows(RuntimeException.class,
        () -> userService.createUser(username, "email", "pass", "first", "last"));
    assertTrue(exception.getMessage().contains("Username already exists"));
  }
}
