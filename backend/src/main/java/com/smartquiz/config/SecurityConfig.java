package com.smartquiz.config;

import com.smartquiz.observability.RequestIdFilter;
import com.smartquiz.security.JwtAuthFilter;
import com.smartquiz.security.MetricsScrapeTokenFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Value("${app.metrics.scrape-token:}")
    private String metricsScrapeToken;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    /**
     * BCrypt for new passwords. Stored values carry an encoder prefix ({bcrypt}, {noop}),
     * so legacy plain-text passwords still verify and are re-hashed on the next login.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                    writeProblem(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                        "Authentication is required", request.getRequestURI()))
                .accessDeniedHandler((request, response, deniedException) ->
                    writeProblem(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                        "You do not have permission to perform this action", request.getRequestURI()))
            )
            .authorizeHttpRequests(auth -> auth
                // Re-dispatches (SSE completion, error pages) were already authorized on the original request.
                .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers(MetricsScrapeTokenFilter.PATH).hasAnyRole("ADMIN", "METRICS")
                // The SSE stream authenticates with a one-time ticket; every other chat call needs a JWT.
                .requestMatchers("/api/public/quiz/participant/chat/stream").permitAll()
                .requestMatchers("/api/public/quiz/participant/chat/**").authenticated()
                .requestMatchers("/api/public/quiz/**").permitAll()
                .requestMatchers("/api/auth/**").authenticated()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/mcq/**", "/api/ai/**", "/api/reference/**").hasAnyRole("SME", "ADMIN")
                .anyRequest().authenticated()
            )
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(new MetricsScrapeTokenFilter(metricsScrapeToken), JwtAuthFilter.class);

        return http.build();
    }

    private static void writeProblem(HttpServletResponse response, int status, String title,
                                     String detail, String instance) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        // The request id is limited to [A-Za-z0-9._-] by RequestIdFilter, so it needs no escaping.
        String requestId = RequestIdFilter.current();
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status
                + ",\"detail\":\"" + detail + "\",\"message\":\"" + detail + "\",\"instance\":\""
                + instance.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
                + (requestId == null ? "" : ",\"requestId\":\"" + requestId + "\"") + "}");
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
