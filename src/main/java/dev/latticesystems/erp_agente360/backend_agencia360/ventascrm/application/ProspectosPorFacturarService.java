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
 * Lista de trabajo de Facturación en el ERP: los prospectos en Facturación. Es
 * también la implementación de {@link ConsultaFacturacion} que usa facturacion
 * para emitir la factura (ERP-1.3).
 *
 * <p>
 * Los datos fiscales del receptor todavía no existen en el prospecto: los
 * agrega Coper con la V17 (se capturan al pasar a Pagos). Mientras tanto
 * {@code receptor} sale vacío y facturacion no emite.
 */
@Service
public class ProspectosPorFacturarService implements ConsultaFacturacion {

	private final ProspectoRepository prospectoRepository;
	private final LiquidacionRepository liquidacionRepository;
	private final DirectorioUsuarios directorioUsuarios;

	public ProspectosPorFacturarService(ProspectoRepository prospectoRepository,
			LiquidacionRepository liquidacionRepository, DirectorioUsuarios directorioUsuarios) {
		this.prospectoRepository = prospectoRepository;
		this.liquidacionRepository = liquidacionRepository;
		this.directorioUsuarios = directorioUsuarios;
	}

	@Transactional(readOnly = true)
	public List<ProspectoPorFacturar> listar() {
		List<Prospecto> prospectos = prospectoRepository.findByEtapaOrderByCreatedAtDescIdDesc(Etapa.FACTURACION);
		Map<Long, String> vendedores = vendedores(prospectos);
		return prospectos.stream().map(p -> new ProspectoPorFacturar(p.getId(), p.getNombre(), p.getTelefono(),
				p.getCorreo(), p.getVendedorId(), vendedores.get(p.getVendedorId()), p.getEtapaActualizadaEn()))
				.toList();
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
						p.getEtapaActualizadaEn(), null))
				.toList();
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
