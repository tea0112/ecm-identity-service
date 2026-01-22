package com.ecm.security.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "spring.profiles.active", matches = ".*testcontainers.*")
class EcmIdentityServiceApplicationTests {

	@Container
	@ServiceConnection
	// Suppress resource warning: Testcontainers automatically manages container lifecycle
	@SuppressWarnings("resource")
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
			.withDatabaseName("testdb")
			.withUsername("testuser")
			.withPassword("testpass");

	@Test
	void contextLoads() {
	}

}
