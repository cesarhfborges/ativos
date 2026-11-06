package br.com.chfb.ativo.coreapi.dto.admin;

import br.com.chfb.ativo.coreapi.entity.InvestorProfile;
import br.com.chfb.ativo.coreapi.entity.Role;
import br.com.chfb.ativo.coreapi.entity.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String name,
        String email,
        String phone,
        LocalDate birthDate,
        InvestorProfile investorProfile,
        Role role,
        boolean active,
        Instant createdAt,
        Instant lastLoginAt) {

    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getBirthDate(),
                user.getInvestorProfile(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt(),
                user.getLastLoginAt());
    }
}
