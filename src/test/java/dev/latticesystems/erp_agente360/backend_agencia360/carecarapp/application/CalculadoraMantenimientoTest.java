package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CalculadoraMantenimiento.Evaluacion;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CalculadoraMantenimiento.OrdenCerrada;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CalculadoraMantenimiento.PlanDeServicio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalculadoraMantenimientoTest {

	private static final LocalDate HOY = LocalDate.of(2026, 6, 1);

	private static final List<PlanDeServicio> PLAN = List.of(plan(3L, 40000, 24), plan(1L, 10000, 6),
			plan(2L, 20000, 12));

	@Test
	void sinPlanNoHayServiciosNiProximo() {
		Evaluacion evaluacion = CalculadoraMantenimiento.evaluar(List.of(), List.of(), 5000, null, HOY);

		assertThat(evaluacion.servicios()).isEmpty();
		assertThat(evaluacion.proximo()).isNull();
	}

	@Test
	void sinKmNiHistorialElPrimeroEsElProximoPorTiempo() {
		Evaluacion evaluacion = CalculadoraMantenimiento.evaluar(PLAN, List.of(), null, null, HOY);

		assertThat(evaluacion.servicios()).extracting(ServicioMantenimientoApp::id).containsExactly(1L, 2L, 3L);
		assertThat(evaluacion.servicios()).extracting(ServicioMantenimientoApp::estado).containsExactly("proximo",
				"pendiente", "pendiente");
		assertThat(evaluacion.proximo().servicioId()).isEqualTo(1L);
		assertThat(evaluacion.proximo().motivo()).isEqualTo("tiempo");
		assertThat(evaluacion.proximo().fechaEstimada()).isEqualTo(HOY.plusMonths(6));
		assertThat(evaluacion.proximo().faltanKm()).isEqualTo(10000);
	}

	@Test
	void unaOrdenCerradaCercaDelKmMarcaElServicioComoHecho() {
		LocalDate fecha = LocalDate.of(2026, 3, 1);

		Evaluacion evaluacion = CalculadoraMantenimiento.evaluar(PLAN, List.of(new OrdenCerrada(fecha, 10200)), 12000,
				null, HOY);

		ServicioMantenimientoApp primero = evaluacion.servicios().getFirst();
		assertThat(primero.estado()).isEqualTo("hecho");
		assertThat(primero.hechoEn()).isEqualTo(fecha);
		assertThat(evaluacion.proximo().servicioId()).isEqualTo(2L);
	}

	@Test
	void unaOrdenFueraDeLaToleranciaNoCuenta() {
		Evaluacion evaluacion = CalculadoraMantenimiento.evaluar(PLAN, List.of(new OrdenCerrada(HOY, 11600)), 11600,
				null, HOY);

		assertThat(evaluacion.servicios().getFirst().estado()).isEqualTo("vencido");
		assertThat(evaluacion.servicios().getFirst().hechoEn()).isNull();
	}

	@Test
	void losServiciosQueYaPasaronElKmSinHacerseSonVencidos() {
		Evaluacion evaluacion = CalculadoraMantenimiento.evaluar(PLAN, List.of(), 25000, null, HOY);

		assertThat(evaluacion.servicios()).extracting(ServicioMantenimientoApp::estado).containsExactly("vencido",
				"vencido", "proximo");
		assertThat(evaluacion.proximo().servicioId()).isEqualTo(3L);
		assertThat(evaluacion.proximo().faltanKm()).isEqualTo(15000);
	}

	@Test
	void ganaElTiempoSiLleganAntesLosMeses() {
		List<OrdenCerrada> ordenes = List.of(new OrdenCerrada(LocalDate.of(2026, 3, 1), 10200));

		Evaluacion evaluacion = CalculadoraMantenimiento.evaluar(PLAN, ordenes, 12000, new BigDecimal("40"), HOY);

		assertThat(evaluacion.proximo().motivo()).isEqualTo("tiempo");
		assertThat(evaluacion.proximo().fechaEstimada()).isEqualTo(LocalDate.of(2026, 9, 1));
		assertThat(evaluacion.proximo().faltanDias()).isEqualTo(92);
		assertThat(evaluacion.proximo().faltanKm()).isEqualTo(8000);
	}

	@Test
	void ganaElKmSiLlegaAntesQueLosMeses() {
		List<OrdenCerrada> ordenes = List.of(new OrdenCerrada(LocalDate.of(2026, 3, 1), 10200));

		Evaluacion evaluacion = CalculadoraMantenimiento.evaluar(PLAN, ordenes, 12000, new BigDecimal("100"), HOY);

		assertThat(evaluacion.proximo().motivo()).isEqualTo("km");
		assertThat(evaluacion.proximo().fechaEstimada()).isEqualTo(HOY.plusDays(80));
		assertThat(evaluacion.proximo().faltanDias()).isEqualTo(80);
	}

	@Test
	void sinProximoSiTodoEstaHechoOVencido() {
		Evaluacion evaluacion = CalculadoraMantenimiento.evaluar(PLAN, List.of(), 90000, null, HOY);

		assertThat(evaluacion.proximo()).isNull();
		assertThat(evaluacion.servicios()).extracting(ServicioMantenimientoApp::estado).containsOnly("vencido");
	}

	private static PlanDeServicio plan(Long id, int km, int meses) {
		return new PlanDeServicio(id, "Servicio de " + km + " km", km, meses, new BigDecimal("1800.00"),
				List.of("Cambio de aceite"));
	}
}
