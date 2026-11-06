package br.com.chfb.ativo.assetmanagement.exception;

/** Também vale para operação de outro usuário: a resposta não revela que ela existe. */
public class TransactionNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TransactionNotFoundException() {
        super("Operação não encontrada");
    }
}
