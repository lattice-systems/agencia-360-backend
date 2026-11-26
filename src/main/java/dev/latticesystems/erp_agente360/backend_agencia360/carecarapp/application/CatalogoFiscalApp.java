package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.CatalogoFiscal;
import java.util.List;

/**
 * Los catálogos del SAT que la app usa para el formulario de datos fiscales
 * (V2-M08.05): así la app y el backend aplican las mismas combinaciones.
 */
public record CatalogoFiscalApp(List<Regimen> regimenes, List<Uso> usosCfdi) {

	/** Una clave de {@code c_RegimenFiscal}. */
	public record Regimen(String clave, String descripcion, boolean fisica, boolean moral) {
	}

	/** Una clave de {@code c_UsoCFDI} y los regímenes que la aceptan, ordenados. */
	public record Uso(String clave, String descripcion, boolean fisica, boolean moral, List<String> regimenes) {
	}

	public static CatalogoFiscalApp completo() {
		List<Regimen> regimenes = CatalogoFiscal.regimenes().stream().map(CatalogoFiscalApp::regimen).toList();
		List<Uso> usos = CatalogoFiscal.usos().stream().map(CatalogoFiscalApp::uso).toList();
		return new CatalogoFiscalApp(regimenes, usos);
	}

	private static Regimen regimen(CatalogoFiscal.RegimenFiscal regimen) {
		return new Regimen(regimen.clave(), regimen.descripcion(), regimen.fisica(), regimen.moral());
	}

	private static Uso uso(CatalogoFiscal.UsoCfdi uso) {
		List<String> regimenes = uso.regimenes().stream().sorted().toList();
		return new Uso(uso.clave(), uso.descripcion(), uso.fisica(), uso.moral(), regimenes);
	}
}
