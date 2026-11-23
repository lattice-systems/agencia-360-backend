package dev.latticesystems.erp_agente360.backend_agencia360.shared.config;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.AutenticacionStomp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	public static final String RUTA = "/ws";

	private final AutenticacionStomp autenticacion;
	private final String[] origenesPermitidos;

	public WebSocketConfig(AutenticacionStomp autenticacion,
			@Value("${app.cors.allowed-origins}") String[] origenesPermitidos) {
		this.autenticacion = autenticacion;
		this.origenesPermitidos = origenesPermitidos;
	}

	@Override
	public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
		registry.addEndpoint(RUTA).setAllowedOrigins(origenesPermitidos);
	}

	@Override
	public void configureMessageBroker(@NonNull MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/queue");
		registry.setApplicationDestinationPrefixes("/app");
		registry.setUserDestinationPrefix("/user");
	}

	@Override
	public void configureClientInboundChannel(@NonNull ChannelRegistration registration) {
		registration.interceptors(autenticacion);
	}
}
