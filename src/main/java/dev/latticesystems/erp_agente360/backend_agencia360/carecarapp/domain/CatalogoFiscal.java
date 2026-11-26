package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Catálogos del SAT para CFDI 4.0 que necesita un perfil fiscal (V2-M08.05):
 * {@code c_RegimenFiscal} y {@code c_UsoCFDI}, con la persona a la que aplica
 * cada clave y los regímenes del receptor que acepta cada uso. Van dentro del
 * proyecto porque cambian muy poco y la facturación es simulada (ADR 0009): no
 * se consulta ningún servicio externo.
 *
 * <p>
 * De {@code c_UsoCFDI} quedan fuera {@code CP01} (Pagos) y {@code CN01}
 * (Nómina): solo sirven para el complemento de pago y el recibo de nómina, no
 * para la factura de un servicio, y un perfil con ellos nunca podría facturar.
 */
public final class CatalogoFiscal {

	/** Una clave de {@code c_RegimenFiscal}. */
	public record RegimenFiscal(String clave, String descripcion, boolean fisica, boolean moral) {

		public boolean aplicaA(TipoPersona persona) {
			return persona == TipoPersona.FISICA ? fisica : moral;
		}
	}

	/** Una clave de {@code c_UsoCFDI} con los regímenes del receptor que acepta. */
	public record UsoCfdi(String clave, String descripcion, boolean fisica, boolean moral, Set<String> regimenes) {

		public boolean aplicaA(TipoPersona persona) {
			return persona == TipoPersona.FISICA ? fisica : moral;
		}

		public boolean aceptaRegimen(String regimen) {
			return regimenes.contains(regimen);
		}
	}

	private static final Set<String> DE_ACTIVIDAD = Set.of("601", "603", "606", "612", "620", "621", "622", "623",
			"624", "625", "626");
	private static final Set<String> DEDUCCIONES_PERSONALES = Set.of("605", "606", "607", "608", "611", "612", "614",
			"615", "625");
	private static final Set<String> TODOS = Set.of("601", "603", "605", "606", "607", "608", "610", "611", "612",
			"614", "615", "616", "620", "621", "622", "623", "624", "625", "626");

	private static final List<RegimenFiscal> REGIMENES = List.of(
			new RegimenFiscal("601", "General de Ley Personas Morales", false, true),
			new RegimenFiscal("603", "Personas Morales con Fines no Lucrativos", false, true),
			new RegimenFiscal("605", "Sueldos y Salarios e Ingresos Asimilados a Salarios", true, false),
			new RegimenFiscal("606", "Arrendamiento", true, false),
			new RegimenFiscal("607", "Régimen de Enajenación o Adquisición de Bienes", true, false),
			new RegimenFiscal("608", "Demás ingresos", true, false),
			new RegimenFiscal("610", "Residentes en el Extranjero sin Establecimiento Permanente en México", true,
					true),
			new RegimenFiscal("611", "Ingresos por Dividendos (socios y accionistas)", true, false),
			new RegimenFiscal("612", "Personas Físicas con Actividades Empresariales y Profesionales", true, false),
			new RegimenFiscal("614", "Ingresos por intereses", true, false),
			new RegimenFiscal("615", "Régimen de los ingresos por obtención de premios", true, false),
			new RegimenFiscal("616", "Sin obligaciones fiscales", true, false),
			new RegimenFiscal("620", "Sociedades Cooperativas de Producción que optan por diferir sus ingresos", false,
					true),
			new RegimenFiscal("621", "Incorporación Fiscal", true, false),
			new RegimenFiscal("622", "Actividades Agrícolas, Ganaderas, Silvícolas y Pesqueras", false, true),
			new RegimenFiscal("623", "Opcional para Grupos de Sociedades", false, true),
			new RegimenFiscal("624", "Coordinados", false, true),
			new RegimenFiscal("625",
					"Régimen de las Actividades Empresariales con ingresos a través de Plataformas Tecnológicas", true,
					false),
			new RegimenFiscal("626", "Régimen Simplificado de Confianza", true, true));

	private static final List<UsoCfdi> USOS = List.of(
			new UsoCfdi("G01", "Adquisición de mercancías", true, true, DE_ACTIVIDAD),
			new UsoCfdi("G02", "Devoluciones, descuentos o bonificaciones", true, true, DE_ACTIVIDAD),
			new UsoCfdi("G03", "Gastos en general", true, true, DE_ACTIVIDAD),
			new UsoCfdi("I01", "Construcciones", true, true, DE_ACTIVIDAD),
			new UsoCfdi("I02", "Mobiliario y equipo de oficina por inversiones", true, true, DE_ACTIVIDAD),
			new UsoCfdi("I03", "Equipo de transporte", true, true, DE_ACTIVIDAD),
			new UsoCfdi("I04", "Equipo de cómputo y accesorios", true, true, DE_ACTIVIDAD),
			new UsoCfdi("I05", "Dados, troqueles, moldes, matrices y herramental", true, true, DE_ACTIVIDAD),
			new UsoCfdi("I06", "Comunicaciones telefónicas", true, true, DE_ACTIVIDAD),
			new UsoCfdi("I07", "Comunicaciones satelitales", true, true, DE_ACTIVIDAD),
			new UsoCfdi("I08", "Otra maquinaria y equipo", true, true, DE_ACTIVIDAD),
			new UsoCfdi("D01", "Honorarios médicos, dentales y gastos hospitalarios", true, false,
					DEDUCCIONES_PERSONALES),
			new UsoCfdi("D02", "Gastos médicos por incapacidad o discapacidad", true, false, DEDUCCIONES_PERSONALES),
			new UsoCfdi("D03", "Gastos funerales", true, false, DEDUCCIONES_PERSONALES),
			new UsoCfdi("D04", "Donativos", true, false, DEDUCCIONES_PERSONALES),
			new UsoCfdi("D05", "Intereses reales efectivamente pagados por créditos hipotecarios (casa habitación)",
					true, false, DEDUCCIONES_PERSONALES),
			new UsoCfdi("D06", "Aportaciones voluntarias al SAR", true, false, DEDUCCIONES_PERSONALES),
			new UsoCfdi("D07", "Primas por seguros de gastos médicos", true, false, DEDUCCIONES_PERSONALES),
			new UsoCfdi("D08", "Gastos de transportación escolar obligatoria", true, false, DEDUCCIONES_PERSONALES),
			new UsoCfdi("D09", "Depósitos en cuentas para el ahorro, primas que tengan como base planes de pensiones",
					true, false, DEDUCCIONES_PERSONALES),
			new UsoCfdi("D10", "Pagos por servicios educativos (colegiaturas)", true, false, DEDUCCIONES_PERSONALES),
			new UsoCfdi("S01", "Sin efectos fiscales", true, true, TODOS));

	private CatalogoFiscal() {
	}

	public static List<RegimenFiscal> regimenes() {
		return REGIMENES;
	}

	public static List<UsoCfdi> usos() {
		return USOS;
	}

	public static Optional<RegimenFiscal> regimen(String clave) {
		return REGIMENES.stream().filter(r -> r.clave().equals(clave)).findFirst();
	}

	public static Optional<UsoCfdi> uso(String clave) {
		return USOS.stream().filter(u -> u.clave().equals(clave)).findFirst();
	}
}
