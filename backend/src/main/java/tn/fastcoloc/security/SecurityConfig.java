package tn.fastcoloc.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tn.fastcoloc.api.ApiError;

@Configuration
public class SecurityConfig {
    @Bean
    public JwtDecoder jwtDecoder(@Value("${fastcoloc.auth.url}") String url) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(url + "/auth/v1/.well-known/jwks.json")
                .jwsAlgorithm(SignatureAlgorithm.ES256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(url + "/auth/v1"),
                new SupabaseAccessTokenValidator()));
        return decoder;
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, ObjectMapper mapper, AuthIdentityVerifier verifier,
                                 AccountAuthorizationService access,
                                 @Value("${fastcoloc.frontend-origin}") String origin) throws Exception {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(origin));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        cors.setExposedHeaders(List.of("WWW-Authenticate"));
        cors.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);

        // Bearer-only API: no cookies, server sessions or client-selected authorities.
        http.cors(c -> c.configurationSource(source))
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.disable())
                .addFilterAfter(new AccountAccessFilter(verifier, access, mapper),
                        BearerTokenAuthenticationFilter.class)
                .addFilterBefore(new tn.fastcoloc.auth.AuthRateLimitFilter(),BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                        .requestMatchers(HttpMethod.GET,"/api/v1/listings","/api/v1/listings/areas").permitAll()
                        .requestMatchers("/api/v1/listings","/api/v1/listings/**").hasAnyRole("USER","ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhooks/paypal").permitAll()
                        .requestMatchers("/api/v1/payments","/api/v1/payments/**","/api/payments","/api/payments/**").authenticated()
                        .requestMatchers(HttpMethod.GET,"/api/v1/auth/config","/api/v1/auth/google").permitAll()
                        .requestMatchers(HttpMethod.POST,"/api/v1/auth/signup","/api/v1/auth/login","/api/v1/auth/refresh","/api/v1/auth/exchange","/api/v1/auth/verify","/api/v1/auth/forgot-password","/api/v1/auth/resend-confirmation").permitAll()
                        .requestMatchers("/api/v1/auth/logout","/api/v1/auth/password","/api/v1/auth/session").authenticated()
                        .requestMatchers(HttpMethod.GET,"/swagger-ui.html","/swagger-ui/**","/v3/api-docs","/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/sync").hasAnyRole("PENDING", "USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/blocked", "/api/v1/users/*/block-status", "/api/v1/users/*/public-profile").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/*/block").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/users/*/block").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/v1/users/me","/api/v1/users/me/**","/api/v1/onboarding/**","/api/v1/profile-photos","/api/v1/profile-photos/**","/api/v1/geography/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST,"/api/v1/ai/rent-estimate").hasAnyRole("USER","ADMIN")
                        .requestMatchers(HttpMethod.POST,"/api/v1/recommendations/ai").hasAnyRole("USER","ADMIN")
                        .requestMatchers("/api/v1/notification-preferences").hasAnyRole("USER","ADMIN")
                        .requestMatchers("/api/v1/conversations", "/api/v1/conversations/**").hasAnyRole("USER","ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> write(mapper, res, 401, "UNAUTHORIZED", req.getRequestURI()))
                        .accessDeniedHandler((req, res, ex) -> write(mapper, res, 403, "ACCESS_DENIED", req.getRequestURI())))
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(jwt -> new JwtAuthenticationToken(jwt, List.of())))
                        .authenticationEntryPoint((req, res, ex) -> write(mapper, res, 401, "UNAUTHORIZED", req.getRequestURI())));
        return http.build();
    }

    private static void write(ObjectMapper mapper, HttpServletResponse response, int status,
                              String code, String path) throws IOException {
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), ApiError.of(status, code,
                status == 401 ? "Jeton Supabase valide requis." : "Accès refusé.", path));
    }
}
