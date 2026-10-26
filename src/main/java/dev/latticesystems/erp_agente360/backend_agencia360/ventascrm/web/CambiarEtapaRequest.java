package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.web;

import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Etapa;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.MotivoCierre;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Etapa a la que pasa el prospecto. El motivo es obligatorio al pasar a
 * {@code CERRADO} (decisión del PO del 12 de octubre), es uno de los tres
 * motivos fijos de CRM-1.2 y se ignora en las demás etapas; esa regla la valida
 * el dominio.
 *
 * @param clienteRechazaApp
 *            solo al pasar a {@code FINALIZADO}: {@code true} si el cliente no
 *            quiere la app CareCar; entonces no se crea su cuenta. Si no viene,
 *            se toma como {@code false}
 * @param precioTotal
 *            solo al pasar a {@code PAGO}, y obligatorio: el precio total de la
 *            venta (ERP-1.1). Lo valida el dominio
 * @param correoCliente
 *            solo al pasar a {@code FINALIZADO}: el correo que le faltaba al
 *            prospecto. Sin correo solo se finaliza si el cliente no quiere la
 *            app
 * @param datosFiscales
 *            solo al pasar a {@code PAGO}, y obligatorios: los datos fiscales
 *            del cliente para la factura (ERP-1.3)
 */
public record CambiarEtapaRequest(@NotNull Etapa etapa, MotivoCierre motivoCierre, Boolean clienteRechazaApp,
		@Email @Size(max = 120) String correoCliente, BigDecimal precioTotal,
		@Valid DatosFiscalesRequest datosFiscales) {
}
