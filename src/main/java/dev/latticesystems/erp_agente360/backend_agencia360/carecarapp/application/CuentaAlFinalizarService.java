package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CuentaAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.ClienteListoParaLaApp;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * CRM-1.3: abre sola la cuenta de la app del cliente de un prospecto finalizado
 * y le manda la contraseña temporal por correo. Si el cliente ya tiene cuenta
 * (compra otro auto), no hace nada.
 */
@Service
public class CuentaAlFinalizarService {

	private final CuentaAppRepository cuentaRepository;
	private final CuentasAppService cuentasAppService;

	public CuentaAlFinalizarService(CuentaAppRepository cuentaRepository, CuentasAppService cuentasAppService) {
		this.cuentaRepository = cuentaRepository;
		this.cuentasAppService = cuentasAppService;
	}

	@ApplicationModuleListener
	public void alRegistrarCliente(ClienteListoParaLaApp listo) {
		if (!cuentaRepository.existsByClienteId(listo.clienteId())) {
			cuentasAppService.abrir(listo.clienteId());
		}
	}
}
