package dev.latticesystems.erp_agente360.backend_agencia360.archivos.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.archivos.AlmacenArchivos.Archivo;
import dev.latticesystems.erp_agente360.backend_agencia360.archivos.AlmacenArchivos.ArchivoGuardado;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ArchivosServiceTest {

	private static final byte[] FOTO = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};

	private final Deposito deposito = mock(Deposito.class);
	private final FirmaArchivos firma = new FirmaArchivos("secreto-de-prueba-0123456789-0123456789",
			Clock.fixed(Instant.parse("2026-11-23T18:00:00Z"), ZoneOffset.UTC));
	private final ArchivosService service = new ArchivosService(deposito, firma);

	@Test
	void guardaConUnaClaveNuevaDentroDeSuCarpeta() {
		ArchivoGuardado guardado = service.guardar("evidencias", "image/jpeg", FOTO);

		assertThat(guardado.clave()).matches("evidencias/[0-9a-f-]{36}");
		assertThat(guardado.tipoContenido()).isEqualTo("image/jpeg");
		assertThat(guardado.tamanoBytes()).isEqualTo(FOTO.length);
		verify(deposito).guardar(guardado.clave(), "image/jpeg", FOTO);
		assertThat(service.guardar("evidencias", "image/jpeg", FOTO).clave()).isNotEqualTo(guardado.clave());
	}

	@Test
	void rechazaCarpetasTiposYTamanosNoValidos() {
		assertThatThrownBy(() -> service.guardar("../etc", "image/jpeg", FOTO))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.guardar("evidencias", "text/html", FOTO))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.guardar("evidencias", "image/jpeg", new byte[0]))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.guardar("evidencias", "application/pdf",
				new byte[(int) ArchivosService.TAMANO_MAXIMO + 1])).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("El archivo pasa de 20 MB");
		verify(deposito, never()).guardar(anyString(), anyString(), any());
	}

	@Test
	void laUrlTemporalLlevaVencimientoYFirma() {
		String clave = "evidencias/0f8c2b6e-2f6d-4c1e-9c2a-1b2c3d4e5f60";

		String url = service.urlTemporal(clave);

		assertThat(url).startsWith("/api/v1/archivos/" + clave + "?expira=").contains("&firma=");
		assertThatThrownBy(() -> service.urlTemporal("../manual.pdf")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void descargaSoloConUnaFirmaValida() {
		String clave = "evidencias/0f8c2b6e-2f6d-4c1e-9c2a-1b2c3d4e5f60";
		when(deposito.leer(clave)).thenReturn(Optional.of(new Archivo(clave, "image/jpeg", FOTO)));
		FirmaArchivos.UrlFirmada firmada = firma.firmar(clave);

		assertThat(service.descargar(clave, firmada.expira(), firmada.firma())).isPresent();
		assertThat(service.descargar(clave, firmada.expira(), "alterada")).isEmpty();
		assertThat(service.descargar("../" + clave, firmada.expira(), firmada.firma())).isEmpty();
		verify(deposito, never()).leer(eq("../" + clave));
	}
}
