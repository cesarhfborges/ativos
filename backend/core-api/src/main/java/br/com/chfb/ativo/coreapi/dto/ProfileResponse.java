package br.com.chfb.ativo.coreapi.dto;

import br.com.chfb.ativo.coreapi.entity.InvestorProfile;
import br.com.chfb.ativo.coreapi.entity.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ProfileResponse(
        UUID id,
        String name,
        String email,
        String phone,
        LocalDate birthDate,
        InvestorProfile investorProfile,
        Instant createdAt) {

    public static ProfileResponse from(User user) {
        return new ProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getBirthDate(),
                user.getInvestorProfile(),
                user.getCreatedAt());
    }
}
