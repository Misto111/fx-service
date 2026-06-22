package com.miro.fxservice.repository;

import com.miro.fxservice.domain.ClientAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<ClientAccount, Long> {

    Optional<ClientAccount> findByClientId(String clientId);
    boolean existsByClientId(String clientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c
            from ClientAccount c
            where c.clientId = :clientId
            """)
    Optional<ClientAccount> findByClientIdForUpdate(@Param("clientId") String clientId);
}
