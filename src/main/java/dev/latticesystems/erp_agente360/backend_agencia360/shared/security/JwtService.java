package dev.latticesystems.erp_agente360.backend_agencia360.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

/**
 * Emisión y validación de JWT autocontenidos: el id del usuario (claim
 * {@code uid}) y sus roles (claim {@code roles}) viajan dentro del token, sin
 * necesidad de una consulta a base de datos en cada request.
 */
@Service
public class JwtService {

	static final String CLAIM_USUARIO_ID = "uid";
	static final String CLAIM_ROLES = "roles";
	private static final String PREFIJO_ROL = "ROLE_";

	private final Key signingKey;
	private final long expirationMillis;

	public JwtService(@Value("${app.security.jwt.secret}") String secret,
			@Value("${app.security.jwt.expiration-minutes}") long expirationMinutes) {
		this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
		this.expirationMillis = expirationMinutes * 60_000;
	}

	public String generateToken(Long usuarioId, String subject, List<Role> roles) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + expirationMillis);
		return Jwts.builder().subject(subject).claim(CLAIM_USUARIO_ID, usuarioId)
				.claim(CLAIM_ROLES, roles.stream().map(Enum::name).collect(Collectors.toList())).issuedAt(now)
				.expiration(expiry).signWith(signingKey).compact();
	}

	public Claims parseClaims(String token) {
		return Jwts.parser().verifyWith((javax.crypto.SecretKey) signingKey).build().parseSignedClaims(token)
				.getPayload();
	}

	/**
	 * Arma el usuario autenticado a partir de los claims de un token ya validado.
	 *
	 * @throws MalformedJwtException
	 *             si el token no trae el id del usuario o el nombre de usuario
	 */
	public UsuarioAutenticado extraerUsuario(Claims claims) {
		Number usuarioId = claims.get(CLAIM_USUARIO_ID, Number.class);
		if (usuarioId == null || claims.getSubject() == null) {
			throw new MalformedJwtException("El token no identifica al usuario");
		}
		Set<Role> roles = extractAuthorities(claims).stream().map(GrantedAuthority::getAuthority)
				.map(autoridad -> Role.valueOf(autoridad.substring(PREFIJO_ROL.length())))
				.collect(Collectors.toSet());
		return new UsuarioAutenticado(usuarioId.longValue(), claims.getSubject(), roles);
	}

	@SuppressWarnings("unchecked")
	public List<GrantedAuthority> extractAuthorities(Claims claims) {
		List<String> roles = claims.get(CLAIM_ROLES, List.class);
		if (roles == null) {
			return List.of();
		}
		return roles.stream().map(role -> (GrantedAuthority) new SimpleGrantedAuthority(PREFIJO_ROL + role))
				.collect(Collectors.toList());
	}
}
