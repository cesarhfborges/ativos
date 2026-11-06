package br.com.chfb.ativo.coreapi.exception;

public class RegistrationClosedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RegistrationClosedException() {
        super("O cadastro de novas contas está fechado. Fale com o administrador.");
    }
}
