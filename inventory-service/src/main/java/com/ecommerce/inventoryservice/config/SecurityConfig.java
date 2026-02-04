package com.ecommerce.inventoryservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the inventory-service.
 *
 * Since authentication is handled by the API Gateway, this service
 * trusts the X-User-Id and X-User-Role headers set by the gateway
 * after JWT validation.
 *
 * This configuration:
 * - Disables CSRF (stateless API)
 * - Uses stateless session management
 * - Allows actuator endpoints for health checks
 * - Permits all requests since API Gateway handles authentication
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Allow actuator endpoints for health checks
                        .requestMatchers("/actuator/**").permitAll()
                        // All other requests are permitted since API Gateway handles auth
                        .anyRequest().permitAll()
                );

        return http.build();
    }
}
