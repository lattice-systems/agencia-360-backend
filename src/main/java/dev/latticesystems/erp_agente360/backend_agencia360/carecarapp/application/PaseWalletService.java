package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Cita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoCita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CitaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ServicioNoDisponibleException;
import java.time.ZonedDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pase de Wallet de una cita (V2-M25.03). El QR del pase es el mismo del
 * comprobante (V2-M02.11), así que recepción lo valida con {@link FirmaCitas}.
 */
@Service
public class PaseWalletService {

	private final CitaRepository citaRepository;
	private final VehiculosDeClientes vehiculos;
	private final FirmaCitas firmaCitas;
	private final ConfiguracionWallet configuracion;
	private final PaseAppleGenerador apple;
	private final PaseGoogleGenerador google;

	@Autowired
	public PaseWalletService(CitaRepository citaRepository, VehiculosDeClientes vehiculos, FirmaCitas firmaCitas,
			ConfiguracionWallet configuracion, PaseAppleGenerador apple, PaseGoogleGenerador google) {
		this.citaRepository = citaRepository;
		this.vehiculos = vehiculos;
		this.firmaCitas = firmaCitas;
		this.configuracion = configuracion;
		this.apple = apple;
		this.google = google;
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si la cita no existe o no es del cliente (404)
	 * @throws ConflictoException
	 *             con {@code CITA_CANCELADA} si la cita está cancelada (409)
	 * @throws ServicioNoDisponibleException
	 *             con {@code WALLET_NO_CONFIGURADO} si faltan las credenciales de
	 *             la plataforma (503)
	 */
	@Transactional(readOnly = true)
	public PaseWalletApp pase(Long clienteId, Long citaId, PlataformaWallet plataforma) {
		Cita cita = citaRepository.findByIdAndClienteId(citaId, clienteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la cita " + citaId));
		if (cita.getEstado() == EstadoCita.CANCELADA) {
			throw new ConflictoException("La cita está cancelada", CodigosError.CITA_CANCELADA);
		}
		boolean configurado = plataforma == PlataformaWallet.APPLE
				? configuracion.appleConfigurado()
				: configuracion.googleConfigurado();
		if (!configurado) {
			throw new ServicioNoDisponibleException("El pase de Wallet no está disponible por ahora",
					CodigosError.WALLET_NO_CONFIGURADO);
		}
		String nombre = vehiculos.buscarPorId(cita.getVehiculoClienteId())
				.filter(propio -> propio.propietario().id().equals(clienteId)).map(CitaApp::describir).orElse(null);
		DatosPase datos = new DatosPase(cita.getId(), firmaCitas.contenidoQr(cita.getId()),
				ZonedDateTime.of(cita.getFecha(), cita.getHoraInicio(), AgendaAppService.ZONA_AGENCIA),
				ZonedDateTime.of(cita.getFecha(), cita.getHoraFin(), AgendaAppService.ZONA_AGENCIA), nombre);
		return plataforma == PlataformaWallet.APPLE
				? new PaseWalletApp(apple.generar(datos), null)
				: new PaseWalletApp(null, google.generar(datos));
	}
}
