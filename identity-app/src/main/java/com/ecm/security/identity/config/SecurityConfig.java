package com.ecm.security.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth
            // API Documentation endpoints (Swagger, Scalar, OpenAPI spec)
            .requestMatchers(
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/v3/api-docs",
                "/v3/api-docs/**",
                "/v3/api-docs.yaml",
                "/scalar.html",
                "/docs",
                "/api-docs",
                "/webjars/**")
            .permitAll()
            .requestMatchers("/api/auth/**").permitAll() // Public auth endpoints
            .requestMatchers("/actuator/**").permitAll() // Monitoring
            .anyRequest().authenticated())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(headers -> headers
            // Relaxed CSP for Swagger UI and Scalar (they need inline scripts/styles)
            .contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'self'; " +
                    "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdn.jsdelivr.net; " +
                    "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
                    "img-src 'self' data: https:; " +
                    "font-src 'self' https://cdn.jsdelivr.net; " +
                    "connect-src 'self'"))
            .frameOptions(frame -> frame.deny())
            .httpStrictTransportSecurity(hsts -> hsts
                .includeSubDomains(true)
                .maxAgeInSeconds(31536000)));
    // Note: We need to add a JWT filter here later.
    // For now, we are relying on default Basic Auth for testing or internal comms
    // until the full auth mechanism is implemented.

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
