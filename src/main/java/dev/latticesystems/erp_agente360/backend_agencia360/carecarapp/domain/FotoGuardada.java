package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import java.time.Instant;

/**
 * Una foto de la valuación. {@code clave} es la de {@code AlmacenArchivos} y
 * nunca sale en la API: la URL firmada se arma al consultar.
 */
public record FotoGuardada(String clave, Instant subidaEn) {
}
