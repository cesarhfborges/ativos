package br.com.chfb.ativo.assetmanagement.exception;

/** Pedido bem formado que as regras da carteira não permitem, como comprar sem saldo. */
public class BusinessRuleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BusinessRuleException(String message) {
        super(message);
    }
}
