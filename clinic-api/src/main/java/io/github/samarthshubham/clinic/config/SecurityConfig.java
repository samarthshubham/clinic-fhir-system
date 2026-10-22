package io.github.samarthshubham.clinic.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.samarthshubham.clinic.security.JsonSecurityErrorHandler;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@Import(JwtConfig.class)
public class SecurityConfig {

  @Bean
  JsonSecurityErrorHandler jsonSecurityErrorHandler(ObjectMapper objectMapper) {
    return new JsonSecurityErrorHandler(objectMapper);
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JsonSecurityErrorHandler errorHandler,
      JwtAuthenticationConverter jwtAuthenticationConverter)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers("/api/auth/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/alerts/bp-subscription")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            resourceServer ->
                resourceServer
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                    .authenticationEntryPoint(errorHandler))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(errorHandler)
                    .accessDeniedHandler(errorHandler));
    return http.build();
  }
}
