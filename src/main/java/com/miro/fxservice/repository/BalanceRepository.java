package com.miro.fxservice.repository;

import com.miro.fxservice.domain.Balance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BalanceRepository extends JpaRepository<Balance, Long> {

    List<Balance> findByClient_ClientIdOrderByCurrencyAsc(String clientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b
            from Balance b
            where b.client.clientId = :clientId
              and b.currency in :currencies
            order by b.currency asc
            """)
    List<Balance> findBalancesForUpdate(
            @Param("clientId") String clientId,
            @Param("currencies") Collection<String> currencies
    );
}