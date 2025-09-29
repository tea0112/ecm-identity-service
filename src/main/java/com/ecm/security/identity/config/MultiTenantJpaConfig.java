package com.ecm.security.identity.config;

import com.ecm.security.identity.service.TenantContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/**
 * Configuration for multi-tenant JPA setup using Hibernate.
 * Implements schema-based multi-tenancy with proper data isolation.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class MultiTenantJpaConfig {
    
    /**
     * Customizes Hibernate properties for multi-tenancy.
     */
    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer(
            MultiTenantConnectionProvider connectionProvider,
            CurrentTenantIdentifierResolver tenantResolver) {
        
        return (Map<String, Object> hibernateProperties) -> {
            hibernateProperties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
        };
    }
    
    /**
     * Resolves the current tenant identifier for Hibernate.
     */
    @Component
    @RequiredArgsConstructor
    public static class TenantIdentifierResolver implements CurrentTenantIdentifierResolver {
        
        private final TenantContextService tenantContextService;
        private static final String DEFAULT_TENANT = "default";
        
        @Override
        public String resolveCurrentTenantIdentifier() {
            try {
                TenantContextService.TenantContext context = tenantContextService.getCurrentContext();
                if (context != null && context.getTenantCode() != null) {
                    return context.getTenantCode();
                }
            } catch (Exception e) {
                log.debug("Could not resolve tenant context, using default: {}", e.getMessage());
            }
            return DEFAULT_TENANT;
        }
        
        @Override
        public boolean validateExistingCurrentSessions() {
            // Allow existing sessions to continue with their original tenant context
            return true;
        }
    }
    
    /**
     * Provides database connections for different tenants.
     * In this implementation, we use schema-based separation within the same database.
     */
    @Component
    @RequiredArgsConstructor
    @Slf4j
    public static class SchemaBasedMultiTenantConnectionProvider implements MultiTenantConnectionProvider {
        
        private final DataSource dataSource;
        private static final String DEFAULT_SCHEMA = "public";
        
        @Override
        public Connection getAnyConnection() throws SQLException {
            return dataSource.getConnection();
        }
        
        @Override
        public void releaseAnyConnection(Connection connection) throws SQLException {
            connection.close();
        }
        
        @Override
        public Connection getConnection(Object tenantIdentifier) throws SQLException {
            Connection connection = getAnyConnection();
            
            try {
                // Set the schema for this tenant
                String schema = getTenantSchema(tenantIdentifier);
                
                // Create schema if it doesn't exist (for development)
                if (!DEFAULT_SCHEMA.equals(schema)) {
                    createSchemaIfNotExists(connection, schema);
                }
                
                // Switch to the tenant's schema
                connection.createStatement().execute("SET search_path TO " + schema);
                
                log.debug("Connected to tenant schema: {}", schema);
                
            } catch (SQLException e) {
                log.error("Failed to set schema for tenant: {}", tenantIdentifier, e);
                connection.close();
                throw e;
            }
            
            return connection;
        }
        
        @Override
        public void releaseConnection(Object tenantIdentifier, Connection connection) throws SQLException {
            try {
                // Reset to default schema before releasing
                connection.createStatement().execute("SET search_path TO " + DEFAULT_SCHEMA);
            } catch (SQLException e) {
                log.warn("Failed to reset schema for tenant: {}", tenantIdentifier, e);
            } finally {
                connection.close();
            }
        }
        
        @Override
        public boolean supportsAggressiveRelease() {
            return false;
        }
        
        @Override
        public boolean isUnwrappableAs(Class unwrapType) {
            return false;
        }
        
        @Override
        public <T> T unwrap(Class<T> unwrapType) {
            return null;
        }
        
        /**
         * Maps tenant identifier to database schema name.
         */
        private String getTenantSchema(Object tenantIdentifier) {
            if (tenantIdentifier == null || tenantIdentifier.toString().isEmpty() || "default".equals(tenantIdentifier)) {
                return DEFAULT_SCHEMA;
            }
            
            // Sanitize tenant identifier to create valid schema name
            return "tenant_" + tenantIdentifier.toString().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        }
        
        /**
         * Creates tenant schema if it doesn't exist.
         * In production, schemas should be pre-created through migrations.
         */
        private void createSchemaIfNotExists(Connection connection, String schema) throws SQLException {
            if (DEFAULT_SCHEMA.equals(schema)) {
                return; // Don't create public schema
            }
            
            try {
                String createSchemaSQL = "CREATE SCHEMA IF NOT EXISTS " + schema;
                connection.createStatement().execute(createSchemaSQL);
                log.info("Created schema for tenant: {}", schema);
                
                // Copy tables from public schema (in development only)
                if (isDevelopmentMode()) {
                    copyTablesFromPublicSchema(connection, schema);
                }
                
            } catch (SQLException e) {
                log.error("Failed to create schema: {}", schema, e);
                throw e;
            }
        }
        
        /**
         * Copies table structure from public schema to tenant schema.
         * This is for development convenience - in production, use proper migrations.
         */
        private void copyTablesFromPublicSchema(Connection connection, String tenantSchema) {
            try {
                // Get list of tables from public schema
                String getTablesSQL = """
                    SELECT tablename FROM pg_tables 
                    WHERE schemaname = 'public' 
                    AND tablename NOT LIKE 'flyway_%'
                    """;
                
                var tablesResult = connection.createStatement().executeQuery(getTablesSQL);
                
                while (tablesResult.next()) {
                    String tableName = tablesResult.getString("tablename");
                    
                    // Create table in tenant schema with same structure
                    String createTableSQL = String.format(
                        "CREATE TABLE %s.%s (LIKE public.%s INCLUDING ALL)",
                        tenantSchema, tableName, tableName
                    );
                    
                    connection.createStatement().execute(createTableSQL);
                    log.debug("Created table {} in schema {}", tableName, tenantSchema);
                }
                
            } catch (SQLException e) {
                log.warn("Failed to copy tables to tenant schema: {}", tenantSchema, e);
            }
        }
        
        /**
         * Checks if running in development mode.
         */
        private boolean isDevelopmentMode() {
            String profiles = System.getProperty("spring.profiles.active", "");
            return profiles.contains("dev") || profiles.contains("local");
        }
    }
}
