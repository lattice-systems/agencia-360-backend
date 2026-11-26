package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.time.Instant;

/**
 * Una sesión abierta del cliente (V2-M01.09).
 *
 * <p>
 * Tres de los cinco campos pueden ser nulos, y no es un caso raro: las sesiones
 * abiertas antes de esta historia no tienen ninguno de los tres y siguen siendo
 * válidas.
 *
 * @param dispositivo
 *            nombre que mandó la app al entrar; nulo si no lo mandó
 * @param ultimoUso
 *            cuándo se refrescó por última vez; nulo si nunca se ha refrescado
 * @param iniciadaEn
 *            cuándo empezó la sesión de verdad, que se conserva entre refrescos
 * @param expiraEn
 *            a los 30 días del <strong>último refresco</strong>, no del inicio:
 *            usar la app la mantiene viva
 */
public record SesionActivaApp(Long id, String dispositivo, Instant ultimoUso, Instant iniciadaEn, Instant expiraEn) {
}
