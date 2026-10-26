package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.DirectorioUsuarios;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.ConsultaUnidades;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.ProspectoFinalizado;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application.ProspectoVista.VehiculoVendido;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.DatosFiscales;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Etapa;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.HistorialEtapa;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.MotivoCierre;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Prospecto;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.ProspectoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seguimiento de prospectos por etapa (CRM-1.1) con la regla de visibilidad de
 * CRM-0.1:
 * <ul>
 * <li>Ventas ve y mueve solo sus prospectos.</li>
 * <li>Gerente de marketing ve todos, pero no cambia etapas; solo los
 * reasigna.</li>
 * <li>Los demás roles no ven la lista.</li>
 * </ul>
 * Un prospecto ajeno se trata como inexistente (404) para no confirmar que
 * existe.
 *
 * <p>
 * Quién mueve cada etapa (decisión del PO del 12 de octubre): el vendedor las
 * iniciales, Pago y Finalizado, y Facturación pasa de Facturación a Entrega al
 * emitir la factura. De Pago a Facturación solo se pasa cuando Contabilidad
 * valida los pagos en ERP-1.1 ({@link LiquidacionesService}); por aquí no, para
 * que nadie se salte la validación (acordado con Ricardo en el Sprint 2).
 *
 * <p>
 * Al finalizar publica {@link ProspectoFinalizado} (CRM-1.4) en la misma
 * transacción, para que inventario registre la venta y clientes cree la cuenta.
 */
@Service
public class SeguimientoProspectosService {

	private final ProspectoRepository prospectoRepository;
	private final DirectorioUsuarios directorioUsuarios;
	private final ApplicationEventPublisher eventos;
	private final ConsultaUnidades consultaUnidades;
	private final Clock reloj;

	@Autowired
	public SeguimientoProspectosService(ProspectoRepository prospectoRepository, DirectorioUsuarios directorioUsuarios,
			ApplicationEventPublisher eventos, ConsultaUnidades consultaUnidades) {
		this(prospectoRepository, directorioUsuarios, eventos, consultaUnidades, Clock.systemUTC());
	}

	SeguimientoProspectosService(ProspectoRepository prospectoRepository, DirectorioUsuarios directorioUsuarios,
			ApplicationEventPublisher eventos, ConsultaUnidades consultaUnidades, Clock reloj) {
		this.prospectoRepository = prospectoRepository;
		this.directorioUsuarios = directorioUsuarios;
		this.eventos = eventos;
		this.consultaUnidades = consultaUnidades;
		this.reloj = reloj;
	}

	/**
	 * @param etapa
	 *            etapa por la que se filtra; {@code null} para todas
	 */
	@Transactional(readOnly = true)
	public List<ProspectoVista> listar(Etapa etapa, UsuarioAutenticado actor) {
		List<Prospecto> prospectos;
		if (veTodos(actor)) {
			prospectos = etapa == null
					? prospectoRepository.findAllByOrderByCreatedAtDescIdDesc()
					: prospectoRepository.findByEtapaOrderByCreatedAtDescIdDesc(etapa);
		} else {
			prospectos = etapa == null
					? prospectoRepository.findByVendedorIdOrderByCreatedAtDescIdDesc(actor.id())
					: prospectoRepository.findByVendedorIdAndEtapaOrderByCreatedAtDescIdDesc(actor.id(), etapa);
		}
		return prospectos.stream().map(this::vista).toList();
	}

	/**
	 * A los finalizados se les agrega la unidad que eligió Facturación (CRM-1.4),
	 * con la consulta que publica inventario para eso.
	 */
	private ProspectoVista vista(Prospecto prospecto) {
		if (prospecto.getEtapa() != Etapa.FINALIZADO) {
			return ProspectoVista.de(prospecto);
		}
		VehiculoVendido vehiculo = consultaUnidades.unidadDelProspecto(prospecto.getId())
				.map(unidad -> new VehiculoVendido(unidad.vin(), unidad.modelo(), unidad.anio())).orElse(null);
		return ProspectoVista.de(prospecto, vehiculo);
	}

	/**
	 * Historial de etapas, del más antiguo al más reciente, con quién hizo cada
	 * cambio.
	 */
	@Transactional(readOnly = true)
	public List<MovimientoVista> historial(Long prospectoId, UsuarioAutenticado actor) {
		List<HistorialEtapa> movimientos = buscarVisible(prospectoId, actor).getHistorial();
		Map<Long, String> nombres = directorioUsuarios
				.nombresDe(movimientos.stream().map(HistorialEtapa::getUsuarioId).toList());
		return movimientos.stream().map(m -> new MovimientoVista(m.getEtapa(), m.getOrigen(), m.getFechaHora(),
				m.getUsuarioId() == null ? null : nombres.get(m.getUsuarioId()))).toList();
	}

	/**
	 * Cambia la etapa según quién la pide: el vendedor dueño del prospecto o
	 * Facturación (de Facturación a Entrega).
	 *
	 * @param motivoCierre
	 *            obligatorio cuando el vendedor pasa a {@link Etapa#CERRADO}
	 * @throws AccessDeniedException
	 *             si el rol no puede hacer ese cambio (403)
	 * @throws RecursoNoEncontradoException
	 *             si el prospecto no existe o es de otro vendedor (404)
	 */
	@Transactional
	public ProspectoVista cambiarEtapa(Long prospectoId, Etapa etapa, MotivoCierre motivoCierre,
			UsuarioAutenticado actor) {
		return cambiarEtapa(prospectoId, etapa, motivoCierre, false, null, null, null, actor);
	}

	/**
	 * Igual que
	 * {@link #cambiarEtapa(Long, Etapa, MotivoCierre, UsuarioAutenticado)}, con los
	 * datos de la app CareCar al finalizar (decisión del PO, Sprint 2).
	 *
	 * <p>
	 * Para finalizar hace falta el correo del cliente, salvo que no quiera la app:
	 * así {@code correoCliente = null} en {@link ProspectoFinalizado} siempre
	 * significa que no la quiere (acordado con Ricardo).
	 *
	 * @param clienteRechazaApp
	 *            solo al pasar a {@link Etapa#FINALIZADO}: el evento sale sin
	 *            correo y no se crea la cuenta. El prospecto conserva su correo
	 * @param correoCliente
	 *            solo al pasar a {@link Etapa#FINALIZADO}: el correo que faltaba (o
	 *            uno corregido); se guarda en el prospecto
	 * @param precioTotal
	 *            obligatorio cuando el vendedor pasa a {@link Etapa#PAGO}: el
	 *            precio total de la venta (ERP-1.1, decisión del PO del 19 de
	 *            octubre)
	 * @param datosFiscales
	 *            obligatorios cuando el vendedor pasa a {@link Etapa#PAGO}: los
	 *            datos fiscales del cliente para la factura (ERP-1.3, decisión del
	 *            PO del 21 de octubre)
	 * @throws IllegalArgumentException
	 *             si se finaliza sin correo y el cliente sí quiere la app, o se
	 *             pasa a Pago sin un precio total válido o sin datos fiscales
	 *             válidos (400)
	 */
	@Transactional
	public ProspectoVista cambiarEtapa(Long prospectoId, Etapa etapa, MotivoCierre motivoCierre,
			boolean clienteRechazaApp, String correoCliente, BigDecimal precioTotal,
			DatosFiscalesCapturados datosFiscales, UsuarioAutenticado actor) {
		Instant ahora = Instant.now(reloj);
		if (actor.tieneRol(Role.VENTAS)) {
			Prospecto prospecto = buscarDelVendedor(prospectoId, actor);
			boolean finaliza = etapa == Etapa.FINALIZADO && prospecto.getEtapa() == Etapa.ENTREGA;
			if (finaliza) {
				// Antes de mover la etapa, para que un error no deje nada a medias.
				prepararCorreoParaLaApp(prospecto, clienteRechazaApp, correoCliente);
			}
			boolean pasaAPago = etapa == Etapa.PAGO && prospecto.getEtapa() != Etapa.PAGO;
			BigDecimal precio = pasaAPago ? Prospecto.validarPrecioTotal(precioTotal) : null;
			DatosFiscales fiscales = pasaAPago ? exigirDatosFiscales(datosFiscales) : null;
			prospecto.moverPorVendedor(etapa, motivoCierre, actor.id(), ahora);
			if (pasaAPago) {
				prospecto.fijarPrecioTotal(precio);
				prospecto.capturarDatosFiscales(fiscales);
			}
			if (finaliza) {
				eventos.publishEvent(new ProspectoFinalizado(prospecto.getId(), prospecto.getVendedorId(),
						prospecto.getNombre(), clienteRechazaApp ? null : prospecto.getCorreo(), ahora));
			}
			return ProspectoVista.de(prospecto);
		}
		if (actor.tieneRol(Role.FACTURACION) && etapa == Etapa.ENTREGA) {
			Prospecto prospecto = buscarCualquiera(prospectoId);
			prospecto.confirmarFacturacion(actor.id(), ahora);
			return ProspectoVista.de(prospecto);
		}
		throw new AccessDeniedException("Tu rol no puede pasar el prospecto a esa etapa");
	}

	/**
	 * Captura o corrige los datos fiscales del cliente (ERP-1.3). El vendedor, en
	 * su prospecto en Pago: sirve para los que llegaron a Pago antes de que se
	 * pidieran y para corregir un error antes de que Contabilidad valide.
	 * Facturación, en cualquier prospecto en Facturación, si encuentra un error
	 * antes de emitir (decisión del PO del 26 de octubre).
	 *
	 * @throws AccessDeniedException
	 *             si no es Ventas ni Facturación (403)
	 * @throws RecursoNoEncontradoException
	 *             si el prospecto no existe o, para el vendedor, es de otro
	 *             vendedor (404)
	 * @throws IllegalArgumentException
	 *             si algún dato no es válido (400)
	 * @throws ConflictoException
	 *             si el prospecto no está en la etapa de quien corrige (409)
	 */
	@Transactional
	public ProspectoVista capturarDatosFiscales(Long prospectoId, DatosFiscalesCapturados datosFiscales,
			UsuarioAutenticado actor) {
		if (actor.tieneRol(Role.VENTAS)) {
			Prospecto prospecto = buscarDelVendedor(prospectoId, actor);
			prospecto.capturarDatosFiscales(exigirDatosFiscales(datosFiscales));
			return ProspectoVista.de(prospecto);
		}
		if (actor.tieneRol(Role.FACTURACION)) {
			Prospecto prospecto = buscarCualquiera(prospectoId);
			prospecto.corregirDatosFiscalesEnFacturacion(exigirDatosFiscales(datosFiscales));
			return ProspectoVista.de(prospecto);
		}
		throw new AccessDeniedException("Solo el vendedor o Facturación corrigen los datos fiscales");
	}

	private static DatosFiscales exigirDatosFiscales(DatosFiscalesCapturados datosFiscales) {
		if (datosFiscales == null) {
			throw new IllegalArgumentException("Captura los datos fiscales del cliente para la factura");
		}
		return datosFiscales.aDominio();
	}

	private static void prepararCorreoParaLaApp(Prospecto prospecto, boolean clienteRechazaApp, String correoCliente) {
		boolean traeCorreo = correoCliente != null && !correoCliente.isBlank();
		if (!clienteRechazaApp && !traeCorreo && prospecto.getCorreo() == null) {
			throw new IllegalArgumentException(
					"Captura el correo del cliente para crear su cuenta de la app, o marca que no la quiere");
		}
		if (traeCorreo) {
			prospecto.capturarCorreo(correoCliente);
		}
	}

	private Prospecto buscarVisible(Long prospectoId, UsuarioAutenticado actor) {
		return veTodos(actor) ? buscarCualquiera(prospectoId) : buscarDelVendedor(prospectoId, actor);
	}

	private Prospecto buscarDelVendedor(Long prospectoId, UsuarioAutenticado actor) {
		return existente(prospectoRepository.findByIdAndVendedorId(prospectoId, actor.id()), prospectoId);
	}

	private Prospecto buscarCualquiera(Long prospectoId) {
		return existente(prospectoRepository.findById(prospectoId), prospectoId);
	}

	private static Prospecto existente(Optional<Prospecto> prospecto, Long prospectoId) {
		return prospecto.orElseThrow(() -> new RecursoNoEncontradoException("No existe el prospecto " + prospectoId));
	}

	private static boolean veTodos(UsuarioAutenticado actor) {
		if (actor.tieneRol(Role.GERENTE_MARKETING)) {
			return true;
		}
		if (actor.tieneRol(Role.VENTAS)) {
			return false;
		}
		throw new AccessDeniedException("Tu rol no tiene acceso a los prospectos");
	}
}
