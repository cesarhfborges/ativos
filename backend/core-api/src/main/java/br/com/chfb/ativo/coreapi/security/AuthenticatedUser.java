package br.com.chfb.ativo.coreapi.security;

import br.com.chfb.ativo.coreapi.entity.User;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Principal guardado na sessão. É serializado na tabela {@code spring_session_attributes},
 * por isso o hash da senha é apagado logo após a autenticação.
 *
 * <p>O nome completo da classe vai junto na serialização: renomear ou mover esta classe invalida
 * as sessões já gravadas.
 */
public class AuthenticatedUser implements UserDetails, CredentialsContainer {

    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String name;
    private final String email;
    private String passwordHash;

    public AuthenticatedUser(User user) {
        this.id = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
