package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Cita;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TicketCitaPdfTest {

	@Test
	void armaElComprobanteConFoto() throws Exception {
		Cita cita = Cita.confirmar(1L, 11L, LocalDate.of(2026, 12, 3), 2L, LocalTime.of(10, 0), LocalTime.of(11, 0));
		ReflectionTestUtils.setField(cita, "id", 57L);
		VehiculoDeCliente v = new VehiculoDeCliente(11L, "3N1AB7AP5KY123456", "Nissan", "Sentra", 2024, "Advance",
				"ABC-123-D", 12000, null, null);
		byte[] sinQr = new TicketCitaPdf().armar(cita, v, null);
		byte[] pdf = new TicketCitaPdf().armar(cita, v, new FirmaCitas("test-only-citas-qr-0123456789-0123456789").contenidoQr(57L));
		assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
		assertThat(pdf.length).isGreaterThan(sinQr.length);
		Files.createDirectories(Path.of("target"));
		Files.write(Path.of("target/ticket-cita-muestra.pdf"), pdf);
	}
}
