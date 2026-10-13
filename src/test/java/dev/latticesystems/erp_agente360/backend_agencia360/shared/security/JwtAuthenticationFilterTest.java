package dev.latticesystems.erp_agente360.backend_agencia360.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

	private static final String SECRETO = "test-only-secret-0123456789-0123456789-0123456789";

	private final JwtService jwtService = new JwtService(SECRETO, 60);
	private final JwtAuthenticationFilter filtro = new JwtAuthenticationFilter(jwtService);

	@AfterEach
	void limpiarContexto() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void poneAlUsuarioAutenticadoComoPrincipal() throws Exception {
		String token = jwtService.generateToken(42L, "vendedor1", List.of(Role.VENTAS));

		filtrar("Bearer " + token);

		var authentication = SecurityContextHolder.getContext().getAuthentication();
		assertThat(authentication.getPrincipal())
				.isEqualTo(new UsuarioAutenticado(42L, "vendedor1", Set.of(Role.VENTAS)));
		assertThat(authentication.getName()).isEqualTo("vendedor1");
		assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_VENTAS");
	}

	@Test
	void ignoraUnTokenSinIdDeUsuario() throws Exception {
		String token = Jwts.builder().subject("vendedor1").claim("roles", List.of("VENTAS"))
				.signWith(Keys.hmacShaKeyFor(SECRETO.getBytes())).compact();

		filtrar("Bearer " + token);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void ignoraUnTokenConFirmaInvalida() throws Exception {
		String token = new JwtService("otro-secreto-0123456789-0123456789-0123456789-01", 60).generateToken(42L,
				"vendedor1", List.of(Role.VENTAS));

		filtrar("Bearer " + token);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	private void filtrar(String encabezado) throws Exception {
		var request = new MockHttpServletRequest();
		request.addHeader("Authorization", encabezado);
		filtro.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
	}
}
