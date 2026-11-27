package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.HorarioSucursal;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Sucursal;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.SucursalRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class SucursalesAppServiceTest {

	private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");

	private final SucursalRepository repository = mock(SucursalRepository.class);

	private Sucursal sucursal() {
		return new Sucursal("CareCar León", "Blvd. Ejemplo 100, León, Gto.", new BigDecimal("21.125000"),
				new BigDecimal("-101.686000"), "+52 477 000 0000", "leon@agencia.example.com",
				List.of(new HorarioSucursal("VENTAS", List.of(1, 2, 3, 4, 5, 6), "09:00", "19:00"),
						new HorarioSucursal("SERVICIO", List.of(1, 2, 3, 4, 5), "08:00", "18:00"),
						new HorarioSucursal("REFACCIONES", List.of(1, 2, 3, 4, 5), "09:00", "17:00")),
				1);
	}

	private SucursalesAppService servicioA(String instante) {
		when(repository.findByActivaTrueOrderByOrdenAscIdAsc()).thenReturn(List.of(sucursal()));
		return new SucursalesAppService(repository, Clock.fixed(Instant.parse(instante), ZONA));
	}

	@Test
	void usaLaHoraDeLaAgenciaNoLaDelServidor() {
		// 16:00 UTC es lunes 10:00 en la Ciudad de México: todo abierto.
		SucursalApp lunes = servicioA("2026-11-02T16:00:00Z").listar().get(0);

		assertThat(lunes.abiertaAhora()).isTrue();
		assertThat(lunes.horarios()).allMatch(HorarioSucursalApp::abiertoAhora);
	}

	@Test
	void marcaCerradoElDepartamentoQueYaCerro() {
		// Lunes 17:30: refacciones (17:00) ya cerró, ventas y servicio no.
		SucursalApp tarde = servicioA("2026-11-02T23:30:00Z").listar().get(0);

		assertThat(tarde.abiertaAhora()).isTrue();
		assertThat(tarde.horarios()).filteredOn(h -> h.departamento().equals("REFACCIONES"))
				.allMatch(h -> !h.abiertoAhora());
		assertThat(tarde.horarios()).filteredOn(h -> h.departamento().equals("VENTAS"))
				.allMatch(HorarioSucursalApp::abiertoAhora);
	}

	@Test
	void laSucursalCierraCuandoCierranTodosSusDepartamentos() {
		SucursalApp noche = servicioA("2026-11-03T03:00:00Z").listar().get(0);

		assertThat(noche.abiertaAhora()).isFalse();
	}

	@Test
	void elDomingoEstaTodoCerrado() {
		// Domingo 8 de noviembre, 12:00 hora de la agencia.
		SucursalApp domingo = servicioA("2026-11-08T18:00:00Z").listar().get(0);

		assertThat(domingo.abiertaAhora()).isFalse();
		assertThat(domingo.latitud()).isEqualTo(21.125);
		assertThat(domingo.longitud()).isEqualTo(-101.686);
	}

	@Test
	void sinSucursalesDevuelveUnaListaVacia() {
		when(repository.findByActivaTrueOrderByOrdenAscIdAsc()).thenReturn(List.of());

		assertThat(new SucursalesAppService(repository, Clock.system(ZONA)).listar()).isEmpty();
	}
}
