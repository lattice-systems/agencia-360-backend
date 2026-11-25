package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import com.fasterxml.jackson.annotation.JsonFormat;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EstadoAdicional;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EtapaTaller;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.ResultadoInspeccion;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.TallerEnVivo.Adicional;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.TallerEnVivo.Evidencia;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.TallerEnVivo.OrdenEnVivo;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.TallerEnVivo.PasoDeEtapa;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.TallerEnVivo.PuntoRevisado;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.TipoEvidencia;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * La orden en el taller vista por el cliente (V2-M04.02, V2-M04.04 y
 * V2-M04.05). Las horas van en hora de la agencia, sin zona. {@code etapas}
 * trae solo las que ya empezaron; la última es la actual. Los montos traen el
 * IVA incluido y {@code totalEstimado} es el total de la orden más los trabajos
 * adicionales aprobados que aún no pasan a ella; al quedar lista, ya los trae.
 * {@code horarioEntrega} solo viene cuando la orden está lista para entrega
 * (V2-M04.06).
 */
public record OrdenEnVivoApp(Long id, Long vehiculoId, String vehiculo, EstatusServicio estatus, EtapaTaller etapa,
		@JsonFormat(pattern = ServicioApp.FORMATO_FECHA_HORA) LocalDateTime entregaEstimada, String asesor, int avance,
		List<PasoApp> etapas, List<PuntoApp> inspeccion, List<EvidenciaApp> evidencias, List<AdicionalApp> adicionales,
		BigDecimal totalOrden, BigDecimal totalAdicionalesAprobados, BigDecimal totalAdicionalesPendientes,
		BigDecimal totalEstimado, HorarioEntregaApp horarioEntrega) {

	public record PasoApp(EtapaTaller etapa, @JsonFormat(pattern = ServicioApp.FORMATO_FECHA_HORA) LocalDateTime inicio,
			@JsonFormat(pattern = ServicioApp.FORMATO_FECHA_HORA) LocalDateTime fin, String nota) {

		static PasoApp de(PasoDeEtapa paso) {
			return new PasoApp(paso.etapa(), ServicioApp.enLaAgencia(paso.inicio()),
					ServicioApp.enLaAgencia(paso.fin()), paso.nota());
		}
	}

	public record PuntoApp(Long id, String punto, ResultadoInspeccion resultado, String comentario,
			List<EvidenciaApp> evidencias) {

		static PuntoApp de(PuntoRevisado punto) {
			return new PuntoApp(punto.id(), punto.punto(), punto.resultado(), punto.comentario(),
					punto.evidencias().stream().map(EvidenciaApp::de).toList());
		}
	}

	/** {@code url} es temporal y firmada: vence a los 15 minutos. */
	public record EvidenciaApp(Long id, TipoEvidencia tipo, String url) {

		static EvidenciaApp de(Evidencia evidencia) {
			return new EvidenciaApp(evidencia.id(), evidencia.tipo(), evidencia.url());
		}
	}

	public record AdicionalApp(Long id, String concepto, BigDecimal manoDeObra, BigDecimal refacciones,
			BigDecimal total, EstadoAdicional estado,
			@JsonFormat(pattern = ServicioApp.FORMATO_FECHA_HORA) LocalDateTime respondidoEn) {

		static AdicionalApp de(Adicional adicional) {
			return new AdicionalApp(adicional.id(), adicional.concepto(), adicional.manoDeObra(),
					adicional.refacciones(), adicional.total(), adicional.estado(),
					ServicioApp.enLaAgencia(adicional.respondidoEn()));
		}
	}

	static OrdenEnVivoApp de(OrdenEnVivo orden, String vehiculo, HorarioEntregaApp horarioEntrega) {
		return new OrdenEnVivoApp(orden.id(), orden.vehiculoId(), vehiculo,
				EstatusServicio.valueOf(orden.estatus().name()), orden.etapa(),
				ServicioApp.enLaAgencia(orden.entregaEstimada()), orden.asesor(), orden.avance(),
				orden.etapas().stream().map(PasoApp::de).toList(),
				orden.inspeccion().stream().map(PuntoApp::de).toList(),
				orden.evidencias().stream().map(EvidenciaApp::de).toList(),
				orden.adicionales().stream().map(AdicionalApp::de).toList(), orden.totalOrden(),
				orden.totalAdicionalesAprobados(), orden.totalAdicionalesPendientes(), orden.totalEstimado(),
				horarioEntrega);
	}
}
