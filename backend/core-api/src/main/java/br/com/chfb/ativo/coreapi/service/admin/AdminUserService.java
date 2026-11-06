package br.com.chfb.ativo.coreapi.service.admin;

import br.com.chfb.ativo.assetmanagement.exception.BusinessRuleException;
import br.com.chfb.ativo.coreapi.dto.admin.AdminCreateUserRequest;
import br.com.chfb.ativo.coreapi.dto.admin.AdminUpdateUserRequest;
import br.com.chfb.ativo.coreapi.dto.admin.AdminUserResponse;
import br.com.chfb.ativo.coreapi.dto.admin.PageResponse;
import br.com.chfb.ativo.coreapi.entity.Role;
import br.com.chfb.ativo.coreapi.entity.User;
import br.com.chfb.ativo.coreapi.exception.AdminNotFoundException;
import br.com.chfb.ativo.coreapi.exception.EmailAlreadyInUseException;
import br.com.chfb.ativo.coreapi.repository.UserRepository;
import br.com.chfb.ativo.coreapi.security.AuthenticatedUser;
import br.com.chfb.ativo.coreapi.service.AuthService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestão de contas. O papel fica gravado na sessão no login, e as sessões são indexadas pelo e-mail: por isso
 * mudar papel, e-mail, situação ou senha encerra as sessões da conta, buscando-as pelo e-mail antigo.
 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);
    private static final String SYSTEM_ACTOR = "sistema";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final AdminAuditService audit;

    public AdminUserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            FindByIndexNameSessionRepository<? extends Session> sessionRepository,
            AdminAuditService audit) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionRepository = sessionRepository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> list(String query, int page, int size) {
        String term = query == null ? "" : query.strip().toLowerCase(Locale.ROOT).replaceAll("([!%_])", "!$1");
        Page<User> users = userRepository.search("%" + term + "%", PageRequest.of(page, size, Sort.by("name", "email")));
        return new PageResponse<>(users.stream().map(AdminUserResponse::from).toList(), users.getTotalElements());
    }

    @Transactional
    public AdminUserResponse create(AdminCreateUserRequest request, AuthenticatedUser actor) {
        User user = new User(
                request.name().strip(),
                AuthService.normalizeEmail(request.email()),
                passwordEncoder.encode(request.password()),
                Instant.now());
        user.updateProfile(user.getName(), request.phone(), request.birthDate(), request.investorProfile());
        user.changeRole(request.role());
        saveCheckingEmail(user);
        audit.record(actor.getUsername(), "USER_CREATED", user.getEmail(), "Papel: " + request.role());
        return AdminUserResponse.from(user);
    }

    @Transactional
    public AdminUserResponse update(UUID id, AdminUpdateUserRequest request, AuthenticatedUser actor) {
        User user = find(id);
        String previousEmail = user.getEmail();
        String email = AuthService.normalizeEmail(request.email());
        boolean emailChanged = !email.equals(previousEmail);
        boolean roleChanged = user.getRole() != request.role();
        if (roleChanged && user.getRole() == Role.ADMIN) {
            requireAnotherActiveAdmin(user);
        }

        user.updateProfile(request.name().strip(), request.phone(), request.birthDate(), request.investorProfile());
        user.changeEmail(email);
        user.changeRole(request.role());
        saveCheckingEmail(user);

        List<String> changes = new ArrayList<>();
        changes.add("Dados atualizados");
        if (emailChanged) {
            changes.add("e-mail anterior: " + previousEmail);
        }
        if (roleChanged) {
            changes.add("papel: " + request.role());
        }
        audit.record(actor.getUsername(), "USER_UPDATED", email, String.join("; ", changes));

        if (emailChanged || roleChanged) {
            endSessionsOf(previousEmail);
        }
        return AdminUserResponse.from(user);
    }

    @Transactional
    public AdminUserResponse setActive(UUID id, boolean active, AuthenticatedUser actor) {
        User user = find(id);
        if (user.isActive() != active) {
            if (!active) {
                if (user.getId().equals(actor.getId())) {
                    throw new BusinessRuleException("Você não pode desativar a própria conta");
                }
                if (user.getRole() == Role.ADMIN) {
                    requireAnotherActiveAdmin(user);
                }
            }
            user.setActive(active);
            audit.record(actor.getUsername(), active ? "USER_ACTIVATED" : "USER_DEACTIVATED", user.getEmail(), null);
            if (!active) {
                endSessionsOf(user.getEmail());
            }
        }
        return AdminUserResponse.from(user);
    }

    @Transactional
    public void resetPassword(UUID id, String password, AuthenticatedUser actor) {
        User user = find(id);
        user.changePassword(passwordEncoder.encode(password));
        audit.record(actor.getUsername(), "USER_PASSWORD_RESET", user.getEmail(), null);
        endSessionsOf(user.getEmail());
    }

    @Transactional
    public void endSessions(UUID id, AuthenticatedUser actor) {
        User user = find(id);
        audit.record(actor.getUsername(), "USER_SESSIONS_ENDED", user.getEmail(), null);
        endSessionsOf(user.getEmail());
    }

    /**
     * Chamado ao iniciar. Encerra as sessões da conta promovida: a sessão guarda o papel de quando entrou, e a
     * tela mostraria o menu de admin para uma sessão que ainda recebe 403.
     */
    @Transactional
    public void promoteAtStartup(String rawEmail) {
        String email = AuthService.normalizeEmail(rawEmail);
        userRepository.findByEmail(email).ifPresentOrElse(user -> {
            if (user.getRole() != Role.ADMIN) {
                user.changeRole(Role.ADMIN);
                user.setActive(true);
                audit.record(SYSTEM_ACTOR, "USER_PROMOTED", email, "Pela variável APP_ADMIN_EMAIL");
                endSessionsOf(email);
                log.info("Conta promovida a administrador pela variável APP_ADMIN_EMAIL");
            }
        }, () -> log.warn("APP_ADMIN_EMAIL definido, mas não há conta com esse e-mail"));
    }

    private void requireAnotherActiveAdmin(User user) {
        boolean anotherAdmin = userRepository.lockActiveByRole(Role.ADMIN).stream()
                .anyMatch(admin -> !admin.getId().equals(user.getId()));
        if (!anotherAdmin) {
            throw new BusinessRuleException("O sistema precisa de pelo menos um administrador ativo");
        }
    }

    /** A restrição UNIQUE decide: uma consulta prévia não cobriria alterações simultâneas. */
    private void saveCheckingEmail(User user) {
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyInUseException();
        }
    }

    private void endSessionsOf(String email) {
        sessionRepository.findByPrincipalName(email).keySet().forEach(sessionRepository::deleteById);
    }

    private User find(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new AdminNotFoundException("Usuário não encontrado"));
    }
}
