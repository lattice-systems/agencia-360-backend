package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioApns.PushApns;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioApns.ResultadoApns;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.RegistroPaseApple;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.RegistroPaseAppleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Avisa a Apple Wallet que el pase de una cita cambió (V2-M25.06): manda un
 * push vacío a cada dispositivo registrado y Wallet vuelve a pedir el pase a
 * {@link PasesAppleService}, que ya sale anulado o con la fecha nueva. Corre
 * después de confirmar la transacción de la cita y nunca la rompe; sin
 * credenciales de APNs o sin URL pública no hace nada.
 *
 * <p>
 * Quitar un token que Apple declara muerto se hace en una transacción propia
 * porque la de la cita ya terminó.
 */
@Component
public class ActualizadorDePasesApple {

	private static final Logger LOG = LoggerFactory.getLogger(ActualizadorDePasesApple.class);

	private final ConfiguracionWallet configuracion;
	private final EnvioApns apns;
	private final RegistroPaseAppleRepository registros;
	private final TransactionTemplate transaccionNueva;

	@Autowired
	ActualizadorDePasesApple(ConfiguracionWallet configuracion, EnvioApns apns, RegistroPaseAppleRepository registros,
			PlatformTransactionManager transacciones) {
		this.configuracion = configuracion;
		this.apns = apns;
		this.registros = registros;
		this.transaccionNueva = new TransactionTemplate(transacciones);
		this.transaccionNueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@TransactionalEventListener(fallbackExecution = true)
	public void alCambiar(CambioDePaseDeCita cambio) {
		if (!configuracion.appleActualizable() || !apns.disponible()) {
			return;
		}
		try {
			for (RegistroPaseApple registro : registros.findByCitaId(cambio.citaId())) {
				ResultadoApns resultado = apns
						.enviar(PushApns.deUnPase(registro.getPushToken(), registro.getPassTypeId()));
				if (resultado.tokenMuerto()) {
					transaccionNueva.executeWithoutResult(estado -> registros.borrarPorId(registro.getId()));
				}
			}
		} catch (RuntimeException fallo) {
			LOG.warn("No se pudo avisar a Apple Wallet del pase de la cita {}", cambio.citaId(), fallo);
		}
	}
}
