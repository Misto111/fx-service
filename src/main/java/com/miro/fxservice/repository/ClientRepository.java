package com.miro.fxservice.repository;

import com.miro.fxservice.domain.ClientAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<ClientAccount, Long> {

    Optional<ClientAccount> findByClientId(String clientId);
    boolean existsByClientId(String clientId);
}
