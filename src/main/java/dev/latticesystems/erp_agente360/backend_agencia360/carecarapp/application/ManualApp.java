package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

/**
 * Manual en la app ({@code Manual} del contrato de la app).
 *
 * @param titulo
 *            "Nissan March 2026 Manual de propietario"
 * @param url
 *            URL temporal firmada para descargar el PDF sin el Bearer; vence a
 *            los 15 minutos
 */
public record ManualApp(Long id, String marca, String modelo, int anio, String titulo, String url) {
}
