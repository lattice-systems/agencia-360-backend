package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

/**
 * Pase de Wallet ya armado: {@code pkpass} para Apple o {@code url} para
 * Google; el otro campo va {@code null}.
 */
public record PaseWalletApp(byte[] pkpass, String url) {
}
