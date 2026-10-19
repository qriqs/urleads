package com.urleads.security;

import com.urleads.soap.filter.SoapSizeLimitingFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;

    public SecurityConfig(UserDetailsServiceImpl userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(Collections.singletonList(authenticationProvider()));
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain internalFilterChain(HttpSecurity http, Optional<JwtDecoder> jwtDecoderOptional) throws Exception {
        http
            .securityMatcher("/internal/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
            .requestCache(cache -> cache.disable())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
            );

        if (jwtDecoderOptional.isPresent()) {
            http
                .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt
                        .decoder(jwtDecoderOptional.get())
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())
                    )
                    .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                    .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
                )
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/internal/**").hasAuthority("SCOPE_crm.leads.read")
                    .anyRequest().authenticated()
                );
        } else {
            http.authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated()
            );
        }

        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthorityPrefix("SCOPE_");
        grantedAuthoritiesConverter.setAuthoritiesClaimName("scope");

        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return jwtConverter;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain wsFilterChain(HttpSecurity http, Optional<JwtDecoder> jwtDecoderOptional) throws Exception {
        http
            .securityMatcher(new org.springframework.security.web.util.matcher.OrRequestMatcher(AntPathRequestMatcher.antMatcher("/ws/leads"),
                    AntPathRequestMatcher.antMatcher("/ws/*.wsdl"),
                    AntPathRequestMatcher.antMatcher("/ws/*.xsd")))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
            .requestCache(cache -> cache.disable())
            .addFilterBefore(new SoapSizeLimitingFilter(), SecurityContextHolderFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
            );

        if (jwtDecoderOptional.isPresent()) {
            http
                .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt
                        .decoder(jwtDecoderOptional.get())
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())
                    )
                    .bearerTokenResolver(bearerTokenResolver())
                    .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                    .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
                )
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers(
                        AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/ws/leads.wsdl"),
                        AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/ws/*.wsdl"),
                        AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/ws/*.xsd")
                    ).permitAll()
                    .requestMatchers(
                        AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/ws/leads"),
                        AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/ws/**")
                    ).hasAuthority("SCOPE_crm.leads.read")
                    .anyRequest().authenticated()
                );
        } else {
            http.authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/ws/leads.wsdl"),
                    AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/ws/*.wsdl"),
                    AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/ws/*.xsd")
                ).permitAll()
                .anyRequest().authenticated()
            );
        }

        return http.build();
    }

    private BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver resolver = new DefaultBearerTokenResolver();
        resolver.setAllowUriQueryParameter(false);
        return resolver;
    }

    @Bean
    @Order(3)
    public SecurityFilterChain agendaFilterChain(HttpSecurity http,
            @org.springframework.beans.factory.annotation.Autowired(required = false) JwtService legacyJwtService,
            Optional<JwtDecoder> decoder,
            SecurityContextRepository securityContextRepo) throws Exception {
        http.securityMatcher(new org.springframework.security.web.util.matcher.OrRequestMatcher(
                        AntPathRequestMatcher.antMatcher("/api/v1/**"),
                        AntPathRequestMatcher.antMatcher("/ws/notifications"),
                        AntPathRequestMatcher.antMatcher("/ws/notifications/**")))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .securityContext(context -> context.securityContextRepository(securityContextRepo))
                .requestCache(cache -> cache.disable())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/api/v1/health"),
                                AntPathRequestMatcher.antMatcher("/ws/notifications"),
                                AntPathRequestMatcher.antMatcher("/ws/notifications/**")).permitAll()
                        .anyRequest().authenticated());
        if (decoder.isPresent()) {
            http.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(decoder.get())));
        } else if (legacyJwtService != null) {
            http.addFilterBefore(new JwtAuthenticationFilter(legacyJwtService, userDetailsService),
                    org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
        }
        return http.build();
    }

    @Bean
    @Order(4)
    public SecurityFilterChain filterChain(HttpSecurity http, SecurityContextRepository securityContextRepo) throws Exception {
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null);

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(requestHandler)
                .ignoringRequestMatchers("/api/health")
            )
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            .securityContext(context -> context.securityContextRepository(securityContextRepo))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(fixation -> fixation.changeSessionId())
                .maximumSessions(1)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/health", "/api/auth/csrf").permitAll()
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/api/**").authenticated()
                .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/robots.txt", "/{*path}").permitAll()
                .requestMatchers("/api/auth/session", "/api/auth/me").authenticated()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"No autorizado\"}");
                })
            )
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpServletResponse.SC_NO_CONTENT))
            );

        return http.build();
    }

    private static class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}
