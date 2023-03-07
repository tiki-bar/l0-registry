/*
 * Copyright (c) TIKI Inc.
 * MIT license. See LICENSE file in root directory.
 */

package com.mytiki.l0_registry.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mytiki.l0_registry.utilities.Constants;
import com.mytiki.spring_rest_api.ApiConstants;
import com.mytiki.spring_rest_api.SecurityConstants;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter;

import java.net.URL;
import java.util.*;
import java.util.function.Predicate;

@EnableWebSecurity
public class SecurityConfig {
    private final AccessDeniedHandler accessDeniedHandler;
    private final AuthenticationEntryPoint authenticationEntryPoint;
    private final JwtDecoder jwtDecoder;

    public SecurityConfig(
            @Autowired ObjectMapper objectMapper,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") URL jwtJwkUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.jws-algorithms}") Set<JWSAlgorithm> jwtJwsAlgorithms,
            @Value("${spring.security.oauth2.resourceserver.jwt.audiences}") Set<String> jwtAudiences,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String jwtIssuer) {
        this.accessDeniedHandler = new AccessDeniedHandler(objectMapper);
        this.authenticationEntryPoint = new AuthenticationEntryPoint(objectMapper);
        this.jwtDecoder = jwtDecoder(jwtJwkUri, jwtJwsAlgorithms, jwtAudiences, jwtIssuer);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .addFilter(new WebAsyncManagerIntegrationFilter())
                .servletApi().and()
                .exceptionHandling()
                .accessDeniedHandler(accessDeniedHandler)
                .authenticationEntryPoint(authenticationEntryPoint).and()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
                .securityContext().and()
                .headers()
                .cacheControl().and()
                .contentTypeOptions().and()
                .httpStrictTransportSecurity().and()
                .frameOptions().and()
                .xssProtection().and()
                .referrerPolicy().and()
                .permissionsPolicy().policy(SecurityConstants.FEATURE_POLICY).and()
                .contentSecurityPolicy(SecurityConstants.CONTENT_SECURITY_POLICY).and().and()
                .anonymous().and()
                .cors()
                .configurationSource(SecurityConstants.corsConfigurationSource()).and()
//                .csrf()
//                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
//                .ignoringRequestMatchers(
//                        new AntPathRequestMatcher(ReportController.PATH_CONTROLLER, HttpMethod.POST.name()),
//                        new AntPathRequestMatcher(TokenController.PATH_CONTROLLER, HttpMethod.POST.name())
//                ).and()
                .authorizeHttpRequests()
                .requestMatchers(HttpMethod.GET, ApiConstants.HEALTH_ROUTE, Constants.API_DOCS_PATH ).permitAll()
//                .requestMatchers(HttpMethod.POST, ReportController.PATH_CONTROLLER).hasRole(REMOTE_WORKER_ROLE)
                .anyRequest().authenticated().and()
                .httpBasic()
                .authenticationEntryPoint(authenticationEntryPoint).and()
                .oauth2ResourceServer()
                .jwt().decoder(jwtDecoder).and()
                .accessDeniedHandler(accessDeniedHandler)
                .authenticationEntryPoint(authenticationEntryPoint);
        return http.build();
    }

    private JwtDecoder jwtDecoder(
            URL jwtJwkUri,
            Set<JWSAlgorithm> jwtJwsAlgorithms,
            Set<String> jwtAudiences,
            String jwtIssuer) {
        DefaultJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
        RemoteJWKSet<SecurityContext> remoteJWKSet = new RemoteJWKSet<>(jwtJwkUri);
        jwtProcessor.setJWSKeySelector(
                new JWSVerificationKeySelector<>(jwtJwsAlgorithms, remoteJWKSet));
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(jwtProcessor);
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        validators.add(new JwtIssuerValidator(jwtIssuer));
        validators.add(new JwtClaimValidator<>(JwtClaimNames.SUB, Objects::nonNull));
        validators.add(new JwtClaimValidator<>(JwtClaimNames.IAT, Objects::nonNull));
        Predicate<List<String>> audienceTest = (audience) -> (audience != null)
                && new HashSet<>(audience).containsAll(jwtAudiences);
        validators.add(new JwtClaimValidator<>(JwtClaimNames.AUD, audienceTest));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }
}
