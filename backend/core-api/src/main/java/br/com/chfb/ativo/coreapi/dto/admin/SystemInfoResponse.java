package br.com.chfb.ativo.coreapi.dto.admin;

/** O que vem das variáveis de ambiente. Do token, só se está definido: o valor nunca sai do servidor. */
public record SystemInfoResponse(
        String cryptoProvider,
        String stockProvider,
        boolean brapiTokenConfigured,
        String collectionCron,
        boolean collectOnStartup,
        boolean predictiveEnabled) {
}
