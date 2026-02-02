package com.screenai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import io.r2dbc.spi.ConnectionFactory;

@Configuration
public class DatabaseInitializer {
    
    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);
    
    @Bean
    @Order(1)
    public ConnectionFactoryInitializer initializer(ConnectionFactory connectionFactory) {
        log.info("Initializing database schema...");
        
        ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
        initializer.setConnectionFactory(connectionFactory);
        
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("schema.sql"));
        // Admin user is created by AdminBootstrapService
        populator.setContinueOnError(true);
        
        initializer.setDatabasePopulator(populator);
        
        log.info("Database schema initialization configured");
        return initializer;
    }
}
