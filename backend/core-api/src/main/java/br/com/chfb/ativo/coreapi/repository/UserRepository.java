package br.com.chfb.ativo.coreapi.repository;

import br.com.chfb.ativo.coreapi.entity.Role;
import br.com.chfb.ativo.coreapi.entity.User;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    /** Os termos já chegam em minúsculas e com os curingas escapados por {@code !}. */
    @Query("""
            select u from User u
            where lower(u.name) like :term escape '!' or u.email like :term escape '!'
            """)
    Page<User> search(@Param("term") String term, Pageable pageable);

    /** Trava os admins ativos: duas alterações simultâneas não podem deixar o sistema sem nenhum. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = :role and u.active = true")
    List<User> lockActiveByRole(@Param("role") Role role);

    long countByActiveTrue();

    long countByRoleAndActiveTrue(Role role);

    @Transactional
    @Modifying
    @Query("update User u set u.lastLoginAt = :at where u.id = :id")
    void updateLastLogin(@Param("id") UUID id, @Param("at") Instant at);
}
