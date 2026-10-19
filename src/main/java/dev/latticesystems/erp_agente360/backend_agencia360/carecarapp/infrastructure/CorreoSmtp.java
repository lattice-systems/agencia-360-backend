package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Envía los correos por SMTP con la configuración {@code spring.mail.*}. Sin
 * servidor de correo configurado solo deja un aviso en el log (sin el código).
 */
@Component
public class CorreoSmtp implements EnvioCorreos {

	private static final Logger LOG = LoggerFactory.getLogger(CorreoSmtp.class);

	private final ObjectProvider<JavaMailSender> mailSender;
	private final String remitente;

	public CorreoSmtp(ObjectProvider<JavaMailSender> mailSender,
			@Value("${app.correo.remitente:CareCar <no-responder@agencia360.local>}") String remitente) {
		this.mailSender = mailSender;
		this.remitente = remitente;
	}

	@Override
	public void enviarCodigoRecuperacion(String correo, String nombre, String codigo) {
		enviar(correo, "Tu código para restablecer la contraseña de CareCar", """
				Hola, %s:

				Tu código para restablecer la contraseña de la app CareCar es: %s

				Vence en 15 minutos y sirve una sola vez. Si no lo pediste, ignora este correo.
				""".formatted(nombre, codigo));
	}

	@Override
	public void enviarCuentaNueva(String correo, String nombre, String contrasenaTemporal) {
		enviar(correo, "Tu cuenta de la app CareCar", """
				Hola, %s:

				La agencia te abrió una cuenta en la app CareCar.

				Correo: %s
				Contraseña temporal: %s

				Al entrar por primera vez la app te pedirá cambiarla.
				""".formatted(nombre, correo, contrasenaTemporal));
	}

	private void enviar(String correo, String asunto, String texto) {
		JavaMailSender sender = mailSender.getIfAvailable();
		if (sender == null) {
			LOG.warn("No hay servidor de correo configurado (spring.mail.host); no se envió el correo: {}", asunto);
			return;
		}
		SimpleMailMessage mensaje = new SimpleMailMessage();
		mensaje.setFrom(remitente);
		mensaje.setTo(correo);
		mensaje.setSubject(asunto);
		mensaje.setText(texto);
		try {
			sender.send(mensaje);
		} catch (MailException ex) {
			LOG.error("No se pudo enviar el correo: {}", asunto, ex);
		}
	}
}
