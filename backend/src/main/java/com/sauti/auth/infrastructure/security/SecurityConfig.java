package com.sauti.auth.infrastructure.security;

import com.sauti.auth.application.port.AccessTokens;
import com.sauti.tenant.application.ApiKeyService;
import com.sauti.tenant.application.WorkspaceAccessService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Request security. The filters are created here rather than as components so Spring Boot does
 * not also register them as plain servlet filters.
 *
 * <p>Order: {@link WorkspacePathFilter} (servlet filter, before Spring Security) reads the
 * workspace from the URL; then operator key, bearer token, and {@link WorkspaceAccessFilter}
 * run inside the security chain; finally the route rules below.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccessTokens accessTokens,
                                            WorkspaceAccessService workspaceAccess, WorkspaceRoutePolicy routePolicy,
                                            ApiKeyService apiKeys,
                                            @Value("${sauti.operator.api-key:}") String operatorApiKey) throws Exception {
        var jwtFilter = new JwtAuthenticationFilter(accessTokens);
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                SecurityErrors.write(response, 401, "unauthorized", "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) ->
                                SecurityErrors.write(response, 403, "forbidden", "Access denied")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/auth/password").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout-all").authenticated()
                        .requestMatchers("/api/v1/auth/appearance").authenticated()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/invitations/preview").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/invitations/accept-new").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/integrations/google-calendar/callback").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/integrations/*/callback").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/agent-templates/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/voices/**").permitAll()
                        .requestMatchers("/api/v1/public/**").permitAll()
                        .requestMatchers("/api/v1/operator/**").hasRole("OPERATOR")
                        .requestMatchers("/api/v1/admin/**").hasRole("PLATFORM_ADMIN")
                        .requestMatchers("/webhooks/whatsapp", "/webhooks/whatsapp/**").permitAll()
                        .requestMatchers("/webhooks/telnyx", "/webhooks/telnyx/**").permitAll()
                        .requestMatchers("/webhooks/mpesa/**").permitAll()
                        .requestMatchers("/webhooks/whop").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new OperatorApiKeyFilter(operatorApiKey), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new ApiKeyAuthenticationFilter(apiKeys), JwtAuthenticationFilter.class)
                .addFilterAfter(new WorkspaceAccessFilter(workspaceAccess, routePolicy), ApiKeyAuthenticationFilter.class)
                .build();
    }

    /** Must run before the Spring Security filter chain so route rules see the feature path. */
    @Bean
    FilterRegistrationBean<WorkspacePathFilter> workspacePathFilter() {
        var registration = new FilterRegistrationBean<>(new WorkspacePathFilter());
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER - 10);
        registration.addUrlPatterns("/api/v1/workspaces/*");
        return registration;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
