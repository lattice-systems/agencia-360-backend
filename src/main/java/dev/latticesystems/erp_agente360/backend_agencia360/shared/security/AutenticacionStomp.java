package dev.latticesystems.erp_agente360.backend_agencia360.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class AutenticacionStomp implements ChannelInterceptor {

	public static final String PREFIJO_USUARIO = "/user/queue/";
	public static final String PREFIJO_APLICACION = "/app/";
	private static final String BEARER = "Bearer ";

	private final JwtService jwtService;

	public AutenticacionStomp(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null || accessor.getCommand() == null) {
			return message;
		}
		StompCommand comando = accessor.getCommand();
		if (comando == StompCommand.CONNECT) {
			accessor.setUser(autenticar(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION)));
		} else if (comando == StompCommand.SUBSCRIBE) {
			exigir(accessor, PREFIJO_USUARIO);
		} else if (comando == StompCommand.SEND) {
			exigir(accessor, PREFIJO_APLICACION);
		}
		return message;
	}

	private UsernamePasswordAuthenticationToken autenticar(String encabezado) {
		if (encabezado == null || !encabezado.startsWith(BEARER)) {
			throw new MessagingException("Se requiere iniciar sesión");
		}
		try {
			Claims claims = jwtService.parseClaims(encabezado.substring(BEARER.length()));
			return new UsernamePasswordAuthenticationToken(jwtService.extraerUsuario(claims), null,
					jwtService.extractAuthorities(claims));
		} catch (JwtException | IllegalArgumentException invalido) {
			throw new MessagingException("Se requiere iniciar sesión");
		}
	}

	private static void exigir(StompHeaderAccessor accessor, String prefijo) {
		String destino = accessor.getDestination();
		if (accessor.getUser() == null || destino == null || !destino.startsWith(prefijo)) {
			throw new MessagingException("No tienes permiso para ese destino");
		}
	}
}
