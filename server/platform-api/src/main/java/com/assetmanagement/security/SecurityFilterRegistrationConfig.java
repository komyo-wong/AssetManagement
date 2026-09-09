package com.assetmanagement.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Keep security filters inside the SecurityFilterChain only.
 * Auto-registering them as servlet Filters forces early bean creation during Tomcat
 * startup and can validate JPA schemas before Flyway migrations run.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityFilterRegistrationConfig {

    @Bean
    FilterRegistrationBean<AccessTokenAuthenticationFilter> disableAccessTokenFilterRegistration(
            AccessTokenAuthenticationFilter filter
    ) {
        FilterRegistrationBean<AccessTokenAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<AuthBrowserRequestGuardFilter> disableBrowserGuardFilterRegistration(
            AuthBrowserRequestGuardFilter filter
    ) {
        FilterRegistrationBean<AuthBrowserRequestGuardFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<MobileApiGuardFilter> disableMobileGuardFilterRegistration(
            MobileApiGuardFilter filter
    ) {
        FilterRegistrationBean<MobileApiGuardFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
