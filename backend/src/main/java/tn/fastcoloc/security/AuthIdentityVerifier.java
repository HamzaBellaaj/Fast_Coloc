package tn.fastcoloc.security;
import org.springframework.security.oauth2.jwt.Jwt;
public interface AuthIdentityVerifier { void requireConfirmed(Jwt jwt); }
