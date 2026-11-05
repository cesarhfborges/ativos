package br.com.chfb.ativo.coreapi.exception;

public class EmailAlreadyInUseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EmailAlreadyInUseException() {
        super("Este e-mail já está cadastrado");
    }
}
