package com.ecm.security.identity.bootstrap;

import com.ecm.security.identity.user.domain.User;
import com.ecm.security.identity.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SystemAdminInitializer implements CommandLineRunner {

  private final UserService userService;

  @Value("${app.security.admin.initial-password:}")
  private String initialAdminPassword;

  @Value("${app.security.admin.email:admin@company.com}")
  private String adminEmail;

  @Override
  public void run(String... args) throws Exception {
    if (userService.getUserByUsername("admin").isPresent()) {
      log.debug("System admin account already exists.");
      return;
    }

    if (initialAdminPassword == null || initialAdminPassword.isBlank()) {
      log.warn(
          "No initial admin password provided (app.security.admin.initial-password). Admin account creation skipped.");
      return;
    }

    try {
      log.info("Creating system admin account...");
      User admin = userService.createUser(
          "admin",
          adminEmail,
          initialAdminPassword,
          "System",
          "Administrator");

      // Assign role
      userService.addRoleToUser(admin.getId(), "ADMIN");

      log.info("System admin account created successfully.");
    } catch (Exception e) {
      log.error("Failed to initialize system admin account", e);
    }
  }
}
