package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.time.LocalDate;

/**
 * Datos del cliente que inició sesión en la app ({@code GET /perfil}). De solo
 * lectura: editarlos es V2-M01.07.
 *
 * @param telefono
 *            nulo cuando la agencia no lo capturó. Se manda igual, en nulo, y
 *            no se omite: la app lo muestra como un dato que falta
 * @param clienteDesde
 *            desde cuándo es cliente, como **fecha** ya resuelta en la zona de
 *            la agencia. Mandar el instante haría que quien se dio de alta de
 *            noche en México apareciera con la fecha del día siguiente
 */
public record PerfilApp(Long id, String nombre, String correo, String telefono, LocalDate clienteDesde) {
}
