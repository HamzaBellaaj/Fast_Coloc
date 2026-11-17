package tn.fastcoloc.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import tn.fastcoloc.api.ApiError;
import tn.fastcoloc.api.ApiException;

/** Runs after cryptographic authentication and before route authorization. */
public class AccountAccessFilter extends OncePerRequestFilter {
    private final AuthIdentityVerifier verifier;
    private final AccountAuthorizationService access;
    private final ObjectMapper mapper;

    public AccountAccessFilter(AuthIdentityVerifier verifier, AccountAuthorizationService access,
                               ObjectMapper mapper) {
        this.verifier = verifier;
        this.access = access;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Account suspension must never prevent logout or Auth password recovery.
        // Supabase verifies these tokens again; private business routes keep the DB status check.
        if (request.getRequestURI().startsWith("/api/v1/auth/")) {
            chain.doFilter(request,response); return;
        }
        try {
            if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
                verifier.requireConfirmed(token.getToken());
                String role = access.role(UUID.fromString(token.getToken().getSubject()));
                var authority = new SimpleGrantedAuthority("ROLE_" + role);
                SecurityContextHolder.getContext().setAuthentication(
                        new JwtAuthenticationToken(token.getToken(), List.of(authority)));
            }
        } catch (ApiException e) {
            SecurityContextHolder.clearContext();
            write(response, e.status().value(), e.code(), e.getMessage(), request.getRequestURI());
            return;
        } catch (DataAccessException e) {
            SecurityContextHolder.clearContext();
            logger.warn("Database authorization unavailable");
            write(response, 503, "DATABASE_UNAVAILABLE", "Base de données indisponible.", request.getRequestURI());
            return;
        }
        chain.doFilter(request, response);
    }

    private void write(HttpServletResponse response, int status, String code, String message,
                       String path) throws IOException {
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), ApiError.of(status, code, message, path));
    }
}
