package io.github.samarthshubham.payer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.samarthshubham.payer.interop.FhirSecurityErrorHandler;
import io.github.samarthshubham.payer.security.JsonSecurityErrorHandler;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Import(JwtConfig.class)
public class SecurityConfig {

  @Bean
  JsonSecurityErrorHandler jsonSecurityErrorHandler(ObjectMapper objectMapper) {
    return new JsonSecurityErrorHandler(objectMapper);
  }

  @Bean
  FhirSecurityErrorHandler fhirSecurityErrorHandler() {
    return new FhirSecurityErrorHandler();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public UserDetailsService userDetailsService(AuthProperties authProperties) {
    return username ->
        authProperties
            .findUser(username)
            .map(
                user ->
                    User.withUsername(user.username())
                        .password(user.passwordHash())
                        .roles(user.role().name())
                        .build())
            .orElseThrow(() -> new UsernameNotFoundException(username));
  }

  @Bean
  public AuthenticationManager authenticationManager(
      UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
  }

  @Bean
  @Order(1)
  SecurityFilterChain fhirSecurityFilterChain(
      HttpSecurity http,
      FhirSecurityErrorHandler errorHandler,
      JwtAuthenticationConverter jwtAuthenticationConverter)
      throws Exception {
    statelessBearer(
            http.securityMatcher(PathPatternRequestMatcher.withDefaults().matcher("/fhir/**")),
            jwtAuthenticationConverter,
            errorHandler)
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }

  @Bean
  @Order(2)
  SecurityFilterChain apiSecurityFilterChain(
      HttpSecurity http,
      JsonSecurityErrorHandler errorHandler,
      JwtAuthenticationConverter jwtAuthenticationConverter)
      throws Exception {
    statelessBearer(http, jwtAuthenticationConverter, errorHandler)
        .authorizeHttpRequests(
            auth ->
                auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers("/api/auth/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/oauth/token")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(errorHandler)
                    .accessDeniedHandler(errorHandler));
    return http.build();
  }

  private static HttpSecurity statelessBearer(
      HttpSecurity http,
      JwtAuthenticationConverter jwtAuthenticationConverter,
      AuthenticationEntryPoint entryPoint)
      throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .oauth2ResourceServer(
            resourceServer ->
                resourceServer
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                    .authenticationEntryPoint(entryPoint));
  }
}
