package com.assetmanagement.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final String HEALTH_ENDPOINT = "/actuator/health";

    @Bean
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            AccessTokenAuthenticationFilter accessTokenAuthenticationFilter,
            AuthBrowserRequestGuardFilter authBrowserRequestGuardFilter,
            MobileApiGuardFilter mobileApiGuardFilter,
            SecurityErrorWriter errorWriter
    )
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                errorWriter.write(
                                        request,
                                        response,
                                        HttpStatus.UNAUTHORIZED,
                                        "AUTH_UNAUTHORIZED",
                                        "Authentication is required"
                                ))
                        .accessDeniedHandler((request, response, exception) ->
                                errorWriter.write(
                                        request,
                                        response,
                                        HttpStatus.FORBIDDEN,
                                        "AUTH_FORBIDDEN",
                                        "Access is denied"
                                )))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HEALTH_ENDPOINT, HEALTH_ENDPOINT + "/**").permitAll()
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/public/branding",
                                "/api/v1/public/geotag/webhook"
                        ).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(authBrowserRequestGuardFilter, AnonymousAuthenticationFilter.class)
                .addFilterBefore(accessTokenAuthenticationFilter, AnonymousAuthenticationFilter.class)
                .addFilterAfter(mobileApiGuardFilter, AccessTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AuthProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.browser().allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                TraceIdFilter.HEADER_NAME
        ));
        configuration.setExposedHeaders(List.of(TraceIdFilter.HEADER_NAME));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
