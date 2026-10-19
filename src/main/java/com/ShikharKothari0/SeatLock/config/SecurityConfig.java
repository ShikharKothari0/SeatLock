package com.ShikharKothari0.SeatLock.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.cors(Customizer.withDefaults())
            .addFilterBefore(new AuthRateLimitFilter(), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR, jakarta.servlet.DispatcherType.ASYNC).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/events", "/api/events/**", "/api/auth/csrf", "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers("/api/admin/**", "/actuator/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .requestCache(cache -> cache.disable())
            .formLogin(login -> login.loginProcessingUrl("/api/auth/login").usernameParameter("email")
                .successHandler((request, response, authentication) -> response.setStatus(204))
                .failureHandler((request, response, error) -> jsonError(response, 401, "Invalid email or password")))
            .logout(logout -> logout.logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, error) -> jsonError(response, 401, "Sign in to continue"))
                .accessDeniedHandler((request, response, error) -> jsonError(response, 403, "Access denied or session token expired")))
            .build();
    }

    private static void jsonError(jakarta.servlet.http.HttpServletResponse response, int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
