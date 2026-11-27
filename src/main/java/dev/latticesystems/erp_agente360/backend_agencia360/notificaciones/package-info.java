/**
 * Notificaciones (V2-M06).
 *
 * <p>
 * Un solo servicio de envío para los avisos que la agencia le manda al cliente:
 * arma el texto, lo guarda en la bandeja ({@code notificacion}) y lo manda por
 * push (FCM) a los dispositivos activos del cliente ({@code dispositivo_push}).
 * Sin credenciales de FCM no envía y no falla.
 *
 * <p>
 * El módulo solo depende de {@code shared}: quien quiere avisar llama a
 * {@code EntregaDeAvisos}. {@code carecarapp} lo hace al escuchar sus propios
 * eventos de dominio, de modo que no hay ciclo entre módulos.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Notificaciones")
package dev.latticesystems.erp_agente360.backend_agencia360.notificaciones;
