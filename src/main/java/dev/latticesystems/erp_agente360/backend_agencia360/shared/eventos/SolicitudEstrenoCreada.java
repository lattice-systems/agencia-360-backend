package dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos;

/**
 * El cliente pidió una prueba de manejo o una cotización desde la app
 * (V2-M16.01). Lo publica carecarapp; ventascrm lo escucha y crea el prospecto.
 *
 * @param tipo
 *            {@code PRUEBA_MANEJO} o {@code COTIZACION}
 */
public record SolicitudEstrenoCreada(Long solicitudId, String nombre, String correo, String telefono, String tipo) {
}
