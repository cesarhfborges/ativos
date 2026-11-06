package br.com.chfb.ativo.coreapi.service;

import br.com.chfb.ativo.coreapi.dto.ChangePasswordRequest;
import br.com.chfb.ativo.coreapi.dto.ProfileResponse;
import br.com.chfb.ativo.coreapi.dto.UpdateProfileRequest;
import br.com.chfb.ativo.coreapi.dto.UserResponse;
import br.com.chfb.ativo.coreapi.entity.User;
import br.com.chfb.ativo.coreapi.exception.InvalidCurrentPasswordException;
import br.com.chfb.ativo.coreapi.repository.UserRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public ProfileService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionRepository = sessionRepository;
    }

    /** Vem do banco, e não da sessão, onde o nome ficaria desatualizado depois de editar o perfil. */
    @Transactional(readOnly = true)
    public UserResponse current(UUID userId) {
        return UserResponse.from(find(userId));
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(UUID userId) {
        return ProfileResponse.from(find(userId));
    }

    @Transactional
    public ProfileResponse update(UUID userId, UpdateProfileRequest request) {
        User user = find(userId);
        user.updateProfile(request.name().strip(), request.phone(), request.birthDate(), request.investorProfile());
        return ProfileResponse.from(user);
    }

    /** Quem soube a senha antiga pode estar logado em outro lugar: só a sessão atual continua válida. */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request, String currentSessionId) {
        User user = find(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));

        sessionRepository.findByPrincipalName(user.getEmail()).keySet().stream()
                .filter(sessionId -> !sessionId.equals(currentSessionId))
                .forEach(sessionRepository::deleteById);
    }

    /** A sessão guarda o id de quando entrou; se a conta não existe mais, ela não vale. */
    private User find(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
