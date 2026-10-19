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
		JavaMailSender sender = mailSender.getIfAvailable();
		if (sender == null) {
			LOG.warn("No hay servidor de correo configurado (spring.mail.host); no se envió el código de recuperación");
			return;
		}
		SimpleMailMessage mensaje = new SimpleMailMessage();
		mensaje.setFrom(remitente);
		mensaje.setTo(correo);
		mensaje.setSubject("Tu código para restablecer la contraseña de CareCar");
		mensaje.setText("""
				Hola, %s:

				Tu código para restablecer la contraseña de la app CareCar es: %s

				Vence en 15 minutos y sirve una sola vez. Si no lo pediste, ignora este correo.
				""".formatted(nombre, codigo));
		try {
			sender.send(mensaje);
		} catch (MailException ex) {
			LOG.error("No se pudo enviar el código de recuperación", ex);
		}
	}
}
