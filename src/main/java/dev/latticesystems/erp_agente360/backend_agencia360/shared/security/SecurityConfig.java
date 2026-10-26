package dev.latticesystems.erp_agente360.backend_agencia360.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ApiError;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * API sin estado (sin sesión de servidor): cada request se autentica con el JWT
 * que llega en el header Authorization. El control de acceso por roles se
 * aplica en cada módulo con anotaciones @PreAuthorize sobre sus casos de uso.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	private static final String[] PUBLIC_ENDPOINTS = {"/actuator/health", "/v3/api-docs/**", "/swagger-ui/**",
			"/swagger-ui.html", "/api/v1/auth/login", "/api/v1/app/auth/login", "/api/v1/app/auth/refrescar",
			"/api/v1/app/auth/cerrar-sesion", "/api/v1/app/auth/recuperar-contrasena",
			"/api/v1/app/auth/restablecer-contrasena", "/api/v1/app/manuales/*/archivo"};

	private final JwtService jwtService;
	private final ObjectMapper objectMapper;

	public SecurityConfig(JwtService jwtService, ObjectMapper objectMapper) {
		this.jwtService = jwtService;
		this.objectMapper = objectMapper;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		// cors() aplica la configuración de WebConfig dentro de la seguridad: así el
		// preflight (OPTIONS, sin token) de un origen permitido se responde antes de
		// exigir sesión. Sin esto, el ERP no puede consultar la API desde otro origen.
		return http.cors(Customizer.withDefaults()).csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// Una respuesta asíncrona (Server-Sent Events) vuelve a pasar por los filtros
				// al
				// cerrarse, ya sin el token. La petición original ya se autorizó; si se revisa
				// otra vez, la conexión termina con un error.
				.authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
						.requestMatchers(PUBLIC_ENDPOINTS).permitAll().anyRequest().authenticated())
				.exceptionHandling(errors -> errors
						.authenticationEntryPoint((request, response, ex) -> escribirError(request, response,
								HttpStatus.UNAUTHORIZED, "Se requiere iniciar sesión"))
						.accessDeniedHandler((request, response, ex) -> escribirError(request, response,
								HttpStatus.FORBIDDEN, "No tienes permiso para esta operación")))
				.addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
				.build();
	}

	/**
	 * Las contraseñas se guardan solo como hash BCrypt (DoD: contraseñas con hash).
	 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/**
	 * Responde con el mismo formato de error que el resto de la API cuando el
	 * rechazo ocurre en la cadena de filtros, antes de llegar a un controlador.
	 */
	private void escribirError(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
			String message) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getOutputStream(),
				ApiError.of(status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
	}
}
