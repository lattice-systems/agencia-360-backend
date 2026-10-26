package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.DirectorioUsuarios;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.ConsultaFacturacion;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Etapa;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Liquidacion;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Prospecto;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.LiquidacionRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.ProspectoRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de {@link ConsultaFacturacion}: los prospectos en Facturación
 * con lo que facturacion necesita para emitir la factura (ERP-1.3).
 *
 * <p>
 * El receptor son los datos fiscales que captura el vendedor al pasar a Pago
 * (V20). Los prospectos que llegaron a Pago antes no los tienen: su
 * {@code receptor} sale vacío y facturacion no emite.
 */
@Service
public class ConsultaFacturacionService implements ConsultaFacturacion {

	private final ProspectoRepository prospectoRepository;
	private final LiquidacionRepository liquidacionRepository;
	private final DirectorioUsuarios directorioUsuarios;

	public ConsultaFacturacionService(ProspectoRepository prospectoRepository,
			LiquidacionRepository liquidacionRepository, DirectorioUsuarios directorioUsuarios) {
		this.prospectoRepository = prospectoRepository;
		this.liquidacionRepository = liquidacionRepository;
		this.directorioUsuarios = directorioUsuarios;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ProspectoParaFacturar> enFacturacion() {
		return paraFacturar(prospectoRepository.findByEtapaOrderByCreatedAtDescIdDesc(Etapa.FACTURACION));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ProspectoParaFacturar> paraFacturar(Long prospectoId) {
		return prospectoRepository.findById(prospectoId).filter(p -> p.getEtapa() == Etapa.FACTURACION)
				.flatMap(p -> paraFacturar(List.of(p)).stream().findFirst());
	}

	private List<ProspectoParaFacturar> paraFacturar(List<Prospecto> prospectos) {
		Map<Long, String> vendedores = vendedores(prospectos);
		Map<Long, BigDecimal> pagado = liquidacionRepository
				.findByProspectoIdIn(prospectos.stream().map(Prospecto::getId).toList()).stream()
				.collect(Collectors.toMap(l -> l.getProspecto().getId(), Liquidacion::total));
		return prospectos.stream()
				.map(p -> new ProspectoParaFacturar(p.getId(), p.getNombre(), p.getVendedorId(),
						vendedores.get(p.getVendedorId()), p.getPrecioTotal(),
						saldo(p.getPrecioTotal(), pagado.getOrDefault(p.getId(), BigDecimal.ZERO)),
						p.getEtapaActualizadaEn(), receptor(p), p.isCierrePendiente()))
				.toList();
	}

	/**
	 * Los datos fiscales del cliente; vacío si todavía no los tiene. El domicilio
	 * se captura separado y aquí va en una línea, como lo imprime la factura.
	 */
	private static ConsultaFacturacion.DatosFiscales receptor(Prospecto prospecto) {
		var datos = prospecto.getDatosFiscales();
		if (datos == null) {
			return null;
		}
		return new ConsultaFacturacion.DatosFiscales(datos.getRfc(), datos.getNombre(),
				datos.getDomicilio().enUnaLinea(), datos.getRegimen().getClave());
	}

	/** Sin precio total no se sabe cuánto falta: el saldo queda vacío. */
	private static BigDecimal saldo(BigDecimal total, BigDecimal pagado) {
		if (total == null) {
			return null;
		}
		return total.subtract(pagado).max(BigDecimal.ZERO).setScale(2);
	}

	private Map<Long, String> vendedores(List<Prospecto> prospectos) {
		return directorioUsuarios.nombresDe(prospectos.stream().map(Prospecto::getVendedorId).distinct().toList());
	}
}
