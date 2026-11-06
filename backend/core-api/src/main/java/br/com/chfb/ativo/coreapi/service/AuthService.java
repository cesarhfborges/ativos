package br.com.chfb.ativo.coreapi.service;

import br.com.chfb.ativo.coreapi.dto.LoginRequest;
import br.com.chfb.ativo.coreapi.dto.RegisterRequest;
import br.com.chfb.ativo.coreapi.dto.UserResponse;
import br.com.chfb.ativo.coreapi.entity.User;
import br.com.chfb.ativo.coreapi.exception.EmailAlreadyInUseException;
import br.com.chfb.ativo.coreapi.repository.UserRepository;
import br.com.chfb.ativo.coreapi.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    public UserResponse register(RegisterRequest request) {
        User user = new User(
                request.name().strip(),
                normalizeEmail(request.email()),
                passwordEncoder.encode(request.password()),
                Instant.now());
        user.updateProfile(user.getName(), request.phone(), request.birthDate(), request.investorProfile());
        try {
            // A restrição UNIQUE decide: uma consulta prévia não cobriria registros simultâneos.
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyInUseException();
        }
    }

    public UserResponse login(LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(body.email(), body.password()));

        // Filtros do Spring Security não fazem estes passos porque o login é um controller:
        // troca o id da sessão (session fixation) e renova o token CSRF.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(request), request, response);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return UserResponse.from((AuthenticatedUser) authentication.getPrincipal());
    }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
