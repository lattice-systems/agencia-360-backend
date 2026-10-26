package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain;

/**
 * Régimen fiscal del cliente que recibe la factura (ERP-1.3), con la clave y la
 * descripción del catálogo c_RegimenFiscal del SAT. El CRM manda y muestra la
 * clave; la factura imprime clave y descripción.
 */
public enum RegimenFiscal {
	/** Persona moral. */
	GENERAL_PERSONAS_MORALES("601", "General de Ley Personas Morales"),
	/** Persona moral. */
	PERSONAS_MORALES_SIN_FINES_DE_LUCRO("603", "Personas Morales con Fines no Lucrativos"),
	/** Persona física. */
	SUELDOS_Y_SALARIOS("605", "Sueldos y Salarios e Ingresos Asimilados a Salarios"),
	/** Persona física. */
	ARRENDAMIENTO("606", "Arrendamiento"),
	/** Persona física. */
	ENAJENACION_DE_BIENES("607", "Régimen de Enajenación o Adquisición de Bienes"),
	/** Persona física. */
	DEMAS_INGRESOS("608", "Demás ingresos"),
	/** Persona física o moral. */
	RESIDENTES_EN_EL_EXTRANJERO("610", "Residentes en el Extranjero sin Establecimiento Permanente en México"),
	/** Persona física. */
	DIVIDENDOS("611", "Ingresos por Dividendos (socios y accionistas)"),
	/** Persona física. */
	ACTIVIDADES_EMPRESARIALES_Y_PROFESIONALES("612", "Personas Físicas con Actividades Empresariales y Profesionales"),
	/** Persona física. */
	INTERESES("614", "Ingresos por intereses"),
	/** Persona física. */
	PREMIOS("615", "Régimen de los ingresos por obtención de premios"),
	/** Persona física. */
	SIN_OBLIGACIONES_FISCALES("616", "Sin obligaciones fiscales"),
	/** Persona moral. */
	COOPERATIVAS_DE_PRODUCCION("620", "Sociedades Cooperativas de Producción que optan por diferir sus ingresos"),
	/** Persona física. */
	INCORPORACION_FISCAL("621", "Incorporación Fiscal"),
	/** Persona moral. */
	ACTIVIDADES_AGRICOLAS("622", "Actividades Agrícolas, Ganaderas, Silvícolas y Pesqueras"),
	/** Persona moral. */
	GRUPOS_DE_SOCIEDADES("623", "Opcional para Grupos de Sociedades"),
	/** Persona moral. */
	COORDINADOS("624", "Coordinados"),
	/** Persona física. */
	PLATAFORMAS_TECNOLOGICAS("625",
			"Régimen de las Actividades Empresariales con ingresos a través de Plataformas Tecnológicas"),
	/** Persona física o moral. */
	SIMPLIFICADO_DE_CONFIANZA("626", "Régimen Simplificado de Confianza");

	private final String clave;
	private final String descripcion;

	RegimenFiscal(String clave, String descripcion) {
		this.clave = clave;
		this.descripcion = descripcion;
	}

	/**
	 * @throws IllegalArgumentException
	 *             si la clave no está en el catálogo
	 */
	public static RegimenFiscal desdeClave(String clave) {
		for (RegimenFiscal regimen : values()) {
			if (regimen.clave.equals(clave)) {
				return regimen;
			}
		}
		throw new IllegalArgumentException("El régimen fiscal " + clave + " no está en el catálogo del SAT");
	}

	public String getClave() {
		return clave;
	}

	public String getDescripcion() {
		return descripcion;
	}
}
