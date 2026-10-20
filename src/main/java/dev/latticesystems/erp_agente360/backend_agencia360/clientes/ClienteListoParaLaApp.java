package dev.latticesystems.erp_agente360.backend_agencia360.clientes;

/**
 * Se registró (o se encontró) al cliente de un prospecto finalizado y tiene
 * correo, así que puede entrar a la app (CRM-1.3). Lo escucha carecarapp para
 * abrirle la cuenta.
 */
public record ClienteListoParaLaApp(Long clienteId) {
}
