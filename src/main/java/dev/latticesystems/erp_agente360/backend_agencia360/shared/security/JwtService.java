package dev.latticesystems.erp_agente360.backend_agencia360.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

/**
 * Emisión y validación de JWT autocontenidos: los roles viajan como claim
 * dentro del token, sin necesidad de una consulta a base de datos en cada
 * request.
 */
@Service
public class JwtService {

	private final Key signingKey;
	private final long expirationMillis;

	public JwtService(@Value("${app.security.jwt.secret}") String secret,
			@Value("${app.security.jwt.expiration-minutes}") long expirationMinutes) {
		this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
		this.expirationMillis = expirationMinutes * 60_000;
	}

	public String generateToken(String subject, List<Role> roles) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + expirationMillis);
		return Jwts.builder().subject(subject)
				.claim("roles", roles.stream().map(Enum::name).collect(Collectors.toList())).issuedAt(now)
				.expiration(expiry).signWith(signingKey).compact();
	}

	public Claims parseClaims(String token) {
		return Jwts.parser().verifyWith((javax.crypto.SecretKey) signingKey).build().parseSignedClaims(token)
				.getPayload();
	}

	@SuppressWarnings("unchecked")
	public List<GrantedAuthority> extractAuthorities(Claims claims) {
		List<String> roles = claims.get("roles", List.class);
		if (roles == null) {
			return List.of();
		}
		return roles.stream().map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
				.collect(Collectors.toList());
	}
}
