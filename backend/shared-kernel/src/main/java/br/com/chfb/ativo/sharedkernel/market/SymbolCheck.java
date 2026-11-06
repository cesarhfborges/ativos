package br.com.chfb.ativo.sharedkernel.market;

/**
 * Resposta do provedor sobre um símbolo. {@code UNAVAILABLE} não diz nada sobre o ativo: pode ser falha,
 * limite de uso, provedor desligado ou falta de token, caso em que a Brapi recusa até ações reais.
 */
public enum SymbolCheck {
    FOUND,
    NOT_FOUND,
    UNAVAILABLE
}
