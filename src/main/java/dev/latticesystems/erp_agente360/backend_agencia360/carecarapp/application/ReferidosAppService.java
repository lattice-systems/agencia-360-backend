package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoReferido;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MotivoPuntos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Referido;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.ReferidoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.DirectorioClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.ProspectoDeReferidoCreado;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.ProspectoFinalizado;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.ReferidoRegistrado;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.ConsultaProspectos;
import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Referir a un amigo (V2-M12.04): el cliente lo registra con su consentimiento,
 * ventascrm crea el prospecto y, cuando la venta se finaliza, el cliente gana
 * sus puntos una sola vez.
 */
@Service
public class ReferidosAppService {

	static final String REFERENCIA = "REFERIDO";
	private static final String ETAPA_CERRADO = "CERRADO";
	private static final Set<EstadoReferido> ABIERTOS = EnumSet.of(EstadoReferido.REGISTRADO,
			EstadoReferido.EN_SEGUIMIENTO);
	private static final int LARGO_CODIGO = 5;

	private final ReferidoRepository referidos;
	private final DirectorioClientes directorio;
	private final ConsultaProspectos prospectos;
	private final CuentaPuntosService cuenta;
	private final ReglasLealtad reglas;
	private final ApplicationEventPublisher eventos;
	private final Clock reloj;

	@Autowired
	public ReferidosAppService(ReferidoRepository referidos, DirectorioClientes directorio,
			ConsultaProspectos prospectos, CuentaPuntosService cuenta, ReglasLealtad reglas,
			ApplicationEventPublisher eventos) {
		this(referidos, directorio, prospectos, cuenta, reglas, eventos, Clock.systemUTC());
	}

	ReferidosAppService(ReferidoRepository referidos, DirectorioClientes directorio, ConsultaProspectos prospectos,
			CuentaPuntosService cuenta, ReglasLealtad reglas, ApplicationEventPublisher eventos, Clock reloj) {
		this.referidos = referidos;
		this.directorio = directorio;
		this.prospectos = prospectos;
		this.cuenta = cuenta;
		this.reglas = reglas;
		this.eventos = eventos;
		this.reloj = reloj;
	}

	/** El código del cliente para compartir: {@code CC} y su id en base 36. */
	public static String codigoDe(Long clienteId) {
		String base = Long.toString(clienteId, 36).toUpperCase(Locale.ROOT);
		return "CC" + "0".repeat(Math.max(0, LARGO_CODIGO - base.length())) + base;
	}

	/**
	 * @param telefono
	 *            10 dígitos (se quitan espacios y guiones), o {@code null}
	 * @throws IllegalArgumentException
	 *             sin nombre, sin teléfono ni correo, o con un teléfono que no
	 *             tiene 10 dígitos (400)
	 * @throws ConflictoException
	 *             {@code REFERIDO_DUPLICADO} si ya es cliente o ya lo refirieron,
	 *             {@code LIMITE_REFERIDOS} si el cliente tiene el máximo abierto
	 *             (409)
	 */
	@Transactional
	public ReferidoApp referir(Long clienteId, String nombre, String telefono, String correo) {
		String tel = telefono(telefono);
		String mail = correo == null || correo.isBlank() ? null : correo.strip().toLowerCase(Locale.ROOT);
		if (referidos.countByClienteIdAndEstadoIn(clienteId, ABIERTOS) >= reglas.referidosAbiertos()) {
			throw new ConflictoException("Ya tienes " + reglas.referidosAbiertos() + " referidos en seguimiento",
					CodigosError.LIMITE_REFERIDOS);
		}
		boolean yaEsCliente = mail != null && directorio.buscarPorCorreo(mail).isPresent();
		boolean yaReferido = (tel != null && referidos.existsByTelefono(tel))
				|| (mail != null && referidos.existsByCorreoIgnoreCase(mail));
		if (yaEsCliente || yaReferido) {
			throw new ConflictoException("Esa persona ya es cliente o ya la refirieron",
					CodigosError.REFERIDO_DUPLICADO);
		}
		Referido referido = referidos
				.save(new Referido(clienteId, codigoDe(clienteId), nombre, tel, mail, reloj.instant()));
		eventos.publishEvent(new ReferidoRegistrado(referido.getId(), referido.getNombre(), tel, mail));
		return ReferidoApp.de(referido, reglas.puntosPorReferido());
	}

	/**
	 * Del más nuevo al más viejo. Los que siguen en seguimiento se revisan contra
	 * el CRM: si su prospecto se cerró sin venta, pasan a descartados.
	 */
	@Transactional
	public List<ReferidoApp> referidos(Long clienteId) {
		List<Referido> propios = referidos.findByClienteIdOrderByCreadoEnDescIdDesc(clienteId);
		for (Referido referido : propios) {
			if (referido.getEstado() == EstadoReferido.EN_SEGUIMIENTO && cerradoSinVenta(referido.getProspectoId())) {
				referido.descartar();
			}
		}
		return propios.stream().map(r -> ReferidoApp.de(r, reglas.puntosPorReferido())).toList();
	}

	@ApplicationModuleListener
	void alCrearseProspecto(ProspectoDeReferidoCreado creado) {
		referidos.findById(creado.referidoId()).ifPresent(r -> r.ligarProspecto(creado.prospectoId()));
	}

	/** La venta del referido se finalizó: el cliente gana sus puntos una vez. */
	@ApplicationModuleListener
	void alFinalizarProspecto(ProspectoFinalizado finalizado) {
		Referido referido = referidos.findByProspectoId(finalizado.prospectoId()).orElse(null);
		if (referido != null && referido.marcarCompra(reloj.instant())) {
			int puntos = reglas.puntosPorReferido();
			cuenta.mover(referido.getClienteId(), puntos, MotivoPuntos.REFERIDO, REFERENCIA, referido.getId());
		}
	}

	private boolean cerradoSinVenta(Long prospectoId) {
		return prospectoId != null && prospectos.estado(prospectoId)
				.map(estado -> estado.terminado() && ETAPA_CERRADO.equals(estado.etapa())).orElse(false);
	}

	private static String telefono(String telefono) {
		if (telefono == null || telefono.isBlank()) {
			return null;
		}
		String digitos = telefono.replaceAll("[\\s()+-]", "");
		if (digitos.length() == 12 && digitos.startsWith("52")) {
			digitos = digitos.substring(2);
		}
		if (!digitos.matches("\\d{10}")) {
			throw new IllegalArgumentException("El teléfono debe tener 10 dígitos");
		}
		return digitos;
	}
}
