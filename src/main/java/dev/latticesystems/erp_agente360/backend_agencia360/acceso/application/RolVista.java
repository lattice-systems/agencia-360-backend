package dev.latticesystems.erp_agente360.backend_agencia360.acceso.application;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Rol;

/** Rol que un administrador puede asignar. */
public record RolVista(String nombre, String descripcion, boolean accesoErp, boolean accesoCrm) {

	static RolVista de(Rol rol) {
		return new RolVista(rol.getNombre(), rol.getDescripcion(), rol.isAccesoErp(), rol.isAccesoCrm());
	}
}
