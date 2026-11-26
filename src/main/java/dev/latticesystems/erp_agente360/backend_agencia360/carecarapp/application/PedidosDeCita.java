package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoPedido;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PedidoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.PedidoAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.CatalogoDeTienda;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.CatalogoDeTienda.ProductoDeTienda;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.PiezasPorInstalar;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los pedidos ligados a una cita (V2-M14.03): se mueven con ella cuando se
 * cancela o se reprograma, y el taller los ve en la orden.
 */
@Component
public class PedidosDeCita implements PiezasPorInstalar {

	private final PedidoAppRepository pedidos;
	private final CatalogoDeTienda catalogo;
	private final Duration vigencia;
	private final Clock reloj;

	@Autowired
	public PedidosDeCita(PedidoAppRepository pedidos, CatalogoDeTienda catalogo,
			@Value("${app.tienda.apartado.vigencia:PT48H}") Duration vigencia) {
		this(pedidos, catalogo, vigencia, Clock.systemUTC());
	}

	PedidosDeCita(PedidoAppRepository pedidos, CatalogoDeTienda catalogo, Duration vigencia, Clock reloj) {
		this.pedidos = pedidos;
		this.catalogo = catalogo;
		this.vigencia = vigencia;
		this.reloj = reloj;
	}

	/** Corre dentro de la misma transacción que cambia la cita. */
	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	public void alCambiarLaCita(CambioDePaseDeCita cambio) {
		for (Long id : pedidos.idsDeCita(cambio.citaId(), PedidoApp.estadosAbiertos())) {
			pedidos.bloquear(id).filter(PedidoApp::abierto).ifPresent(pedido -> mover(pedido, cambio));
		}
	}

	private void mover(PedidoApp pedido, CambioDePaseDeCita cambio) {
		if (cambio.tipo() == CambioDePaseDeCita.Tipo.CANCELADA) {
			pedido.instalarEn(null, ReglasTienda.venceEnMostrador(Instant.now(reloj), vigencia));
		} else {
			pedido.instalarEn(cambio.citaId(), ReglasTienda.venceConCita(cambio.inicio().toLocalDate()));
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<PedidoPorInstalar> deCita(Long citaId) {
		if (citaId == null) {
			return List.of();
		}
		return pedidos.findByCitaIdAndEstadoNotOrderById(citaId, EstadoPedido.CANCELADO).stream()
				.map(this::paraElTaller).toList();
	}

	private PedidoPorInstalar paraElTaller(PedidoApp pedido) {
		Map<Long, ProductoDeTienda> productos = catalogo.porIds(pedido.cantidades().keySet()).stream()
				.collect(Collectors.toMap(ProductoDeTienda::id, Function.identity()));
		List<Pieza> piezas = pedido.getRenglones().stream().map(renglon -> {
			ProductoDeTienda producto = productos.get(renglon.getRefaccionId());
			return new Pieza(producto.codigo(), producto.nombre(), renglon.getCantidad());
		}).toList();
		return new PedidoPorInstalar(pedido.getId(), pedido.getEstado().name(), piezas);
	}
}
