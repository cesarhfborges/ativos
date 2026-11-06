package br.com.chfb.ativo.coreapi.service.admin;

import br.com.chfb.ativo.coreapi.dto.admin.AdminAuditResponse;
import br.com.chfb.ativo.coreapi.dto.admin.PageResponse;
import br.com.chfb.ativo.coreapi.entity.AdminAuditEntry;
import br.com.chfb.ativo.coreapi.repository.AdminAuditRepository;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Entra na transação de quem chama: a ação e o registro dela são gravados juntos. Nunca recebe senhas. */
@Service
public class AdminAuditService {

    private final AdminAuditRepository repository;

    public AdminAuditService(AdminAuditRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(String actorEmail, String action, String target, String details) {
        repository.save(new AdminAuditEntry(actorEmail, action, target, details, Instant.now()));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminAuditResponse> list(int page, int size) {
        Page<AdminAuditEntry> entries = repository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageResponse<>(entries.stream().map(AdminAuditResponse::from).toList(), entries.getTotalElements());
    }
}
