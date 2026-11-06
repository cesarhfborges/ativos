package br.com.chfb.ativo.coreapi.dto;

import br.com.chfb.ativo.coreapi.entity.Role;
import br.com.chfb.ativo.coreapi.entity.User;
import br.com.chfb.ativo.coreapi.security.AuthenticatedUser;
import java.util.UUID;

public record UserResponse(UUID id, String name, String email, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }

    public static UserResponse from(AuthenticatedUser user) {
        return new UserResponse(user.getId(), user.getName(), user.getUsername(), user.getRole());
    }
}
