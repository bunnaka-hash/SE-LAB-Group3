package edu.itc.shop.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // 401/403 are handed to the MVC exception resolver, so ApiExceptionHandler
    // writes the same application/problem+json bodies as every other error.
    private final HandlerExceptionResolver resolver;

    public SecurityConfig(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint entryPoint = (request, response, ex) -> {
            if (resolver.resolveException(request, response, null, ex) == null) {
                response.sendError(HttpStatus.UNAUTHORIZED.value());
            }
        };
        AccessDeniedHandler deniedHandler = (request, response, ex) -> {
            if (resolver.resolveException(request, response, null, ex) == null) {
                response.sendError(HttpStatus.FORBIDDEN.value());
            }
        };

        http.authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.POST, "/api/register").permitAll() // NEW (Task 2)
                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll())
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
                .formLogin(form -> form.disable())
                // CSRF disabled: this chain accepts no cookie-based credentials (Basic / Bearer
                // only)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler));
        return http.build();
    }
}