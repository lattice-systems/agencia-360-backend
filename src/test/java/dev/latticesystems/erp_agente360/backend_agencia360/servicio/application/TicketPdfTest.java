package dev.latticesystems.erp_agente360.backend_agencia360.servicio.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.TipoRenglon;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.pdf.FotosDeAutos;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TicketPdfTest {

	@Test
	void armaElTicketConFotoYLoGuardaDeMuestra() throws Exception {
		ServicioDetalle servicio = new ServicioDetalle(42L, 11L, LocalDate.of(2026, 11, 12), 28450, "Servicio",
				List.of(new ServicioDetalle.Renglon(TipoRenglon.MANO_DE_OBRA, "Afinación mayor", 1,
						new BigDecimal("1160.00"), new BigDecimal("1160.00")),
						new ServicioDetalle.Renglon(TipoRenglon.REFACCION, "Aceite sintético 5W-30", 4,
								new BigDecimal("232.00"), new BigDecimal("928.00")),
						new ServicioDetalle.Renglon(TipoRenglon.REFACCION, "Filtro de aire", 1,
								new BigDecimal("348.00"), new BigDecimal("348.00"))),
				new BigDecimal("2100.00"), new BigDecimal("336.00"), new BigDecimal("2436.00"));
		VehiculoDeCliente v = new VehiculoDeCliente(11L, "3N1AB7AP5KY123456", "Nissan", "X-Trail", 2023, "Exclusive",
				"ABC-123-D", 28450, null, null);
		byte[] pdf = new TicketPdf().armar(servicio, v);
		assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
		assertThat(FotosDeAutos.de("X-Trail")).isNotNull();
		assertThat(FotosDeAutos.de("Desconocido")).isNull();
		Files.createDirectories(Path.of("target"));
		Files.write(Path.of("target/ticket-muestra.pdf"), pdf);
		Files.write(Path.of("target/ticket-sinfoto.pdf"), new TicketPdf().armar(servicio,
				new VehiculoDeCliente(12L, "VIN2", null, "Otro", 2020, null, null, null, null, null)));
	}
}
