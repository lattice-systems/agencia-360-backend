package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PerfilFiscal;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TipoPersona;
import java.time.Instant;

/** Un perfil fiscal como lo ve la app (V2-M08.05). */
public record PerfilFiscalApp(Long id, String rfc, TipoPersona tipoPersona, String razonSocial, String regimenFiscal,
		String codigoPostal, String usoCfdi, String correo, boolean predeterminado, Instant creadoEn) {

	static PerfilFiscalApp de(PerfilFiscal perfil) {
		return new PerfilFiscalApp(perfil.getId(), perfil.getRfc(), perfil.getTipo(), perfil.getRazonSocial(),
				perfil.getRegimenFiscal(), perfil.getCodigoPostal(), perfil.getUsoCfdi(), perfil.getCorreo(),
				perfil.isPredeterminado(), perfil.getCreadoEn());
	}
}
