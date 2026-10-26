package dev.latticesystems.erp_agente360.backend_agencia360.facturacion.application;

import dev.latticesystems.erp_agente360.backend_agencia360.facturacion.domain.DatosFactura;
import dev.latticesystems.erp_agente360.backend_agencia360.facturacion.domain.DatosFactura.Parte;
import dev.latticesystems.erp_agente360.backend_agencia360.facturacion.domain.DatosFactura.Unidad;
import dev.latticesystems.erp_agente360.backend_agencia360.facturacion.domain.Factura;
import dev.latticesystems.erp_agente360.backend_agencia360.facturacion.domain.Montos;
import dev.latticesystems.erp_agente360.backend_agencia360.facturacion.infrastructure.FacturaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.VentaDeUnidades;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.VentaDeUnidades.UnidadVendida;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.FacturaEmitida;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.ConsultaFacturacion;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.ConsultaFacturacion.DatosFiscales;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.ConsultaFacturacion.ProspectoParaFacturar;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emite la factura de un prospecto en Facturación (ERP-1.3). En una sola
 * transacción: bloquea al prospecto, revisa el saldo y los datos fiscales,
 * vende la unidad en inventario (con su fila bloqueada), toma el folio, arma el
 * PDF, guarda la factura y publica {@link FacturaEmitida}. Si algo falla, no
 * queda nada a medias: la unidad sigue Disponible.
 */
@Service
public class EmisionFacturaService {

	private final FacturaRepository facturaRepository;
	private final ConsultaFacturacion consultaFacturacion;
	private final VentaDeUnidades ventaDeUnidades;
	private final Emisor emisor;
	private final FacturaPdf facturaPdf;
	private final ApplicationEventPublisher eventos;
	private final Clock reloj;

	@Autowired
	public EmisionFacturaService(FacturaRepository facturaRepository, ConsultaFacturacion consultaFacturacion,
			VentaDeUnidades ventaDeUnidades, Emisor emisor, FacturaPdf facturaPdf, ApplicationEventPublisher eventos) {
		this(facturaRepository, consultaFacturacion, ventaDeUnidades, emisor, facturaPdf, eventos, Clock.systemUTC());
	}

	EmisionFacturaService(FacturaRepository facturaRepository, ConsultaFacturacion consultaFacturacion,
			VentaDeUnidades ventaDeUnidades, Emisor emisor, FacturaPdf facturaPdf, ApplicationEventPublisher eventos,
			Clock reloj) {
		this.facturaRepository = facturaRepository;
		this.consultaFacturacion = consultaFacturacion;
		this.ventaDeUnidades = ventaDeUnidades;
		this.emisor = emisor;
		this.facturaPdf = facturaPdf;
		this.eventos = eventos;
		this.reloj = reloj;
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si no existe el prospecto o la unidad (404)
	 * @throws ConflictoException
	 *             si el prospecto no está en Facturación, ya tiene factura, tiene
	 *             saldo, le faltan datos fiscales o la unidad ya no está disponible
	 *             (409)
	 */
	@Transactional
	public FacturaVista emitir(Long prospectoId, Long vehiculoId, UsuarioAutenticado actor) {
		Instant ahora = Instant.now(reloj);
		// La fila del prospecto queda bloqueada: dos personas no le facturan a la vez.
		facturaRepository.bloquearProspecto(prospectoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el prospecto " + prospectoId));
		facturaRepository.findByProspectoId(prospectoId).ifPresent(existente -> {
			throw new ConflictoException("El prospecto ya tiene la factura " + existente.getFolio());
		});
		ProspectoParaFacturar prospecto = consultaFacturacion.paraFacturar(prospectoId).orElseThrow(
				() -> new ConflictoException("Solo se factura un prospecto que está en la etapa Facturación"));
		ReglasDeEmision.bloqueo(prospecto).ifPresent(motivo -> {
			throw new ConflictoException(motivo);
		});

		UnidadVendida unidad = ventaDeUnidades.vender(prospectoId, vehiculoId, actor.id(), ahora);
		DatosFactura datos = new DatosFactura(folio(facturaRepository.siguienteFolio()), prospectoId, unidad.ventaId(),
				ahora, emisor.datos(), receptor(prospecto.receptor()), new Unidad(unidad.vehiculoId(), unidad.vin(),
						unidad.marca(), unidad.modelo(), unidad.version(), unidad.anio(), unidad.color()),
				Montos.desglosar(prospecto.total()));
		Factura factura = facturaRepository.save(Factura.emitir(datos, facturaPdf.armar(datos), actor.id()));
		eventos.publishEvent(new FacturaEmitida(prospectoId, unidad.vehiculoId(), factura.getFolio(), ahora));
		return FacturaVista.de(factura);
	}

	/** F-000001, F-000002… */
	static String folio(long consecutivo) {
		return "F-%06d".formatted(consecutivo);
	}

	private static Parte receptor(DatosFiscales datos) {
		return new Parte(datos.rfc().trim(), datos.nombreCompleto().trim(), datos.domicilioFiscal().trim(),
				datos.regimenFiscal().trim());
	}
}
