package com.miro.fxservice.repository;

import com.miro.fxservice.domain.Conversion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ConversionRepository extends JpaRepository<Conversion, Long> {

    Optional<Conversion> findByClientIdAndIdempotencyKey(String clientId, String idempotencyKey);

    Optional<Conversion> findByTransactionId(String transactionId);

    @Query("""
            select c
            from Conversion c
            where (:transactionId is null or c.transactionId = :transactionId)
              and (:clientId is null or c.clientId = :clientId)
              and (:startDateTime is null or c.createdAt >= :startDateTime)
              and (:endDateTime is null or c.createdAt < :endDateTime)
            order by c.createdAt desc
            """)
    Page<Conversion> searchConversions(
            @Param("transactionId") String transactionId,
            @Param("clientId") String clientId,
            @Param("startDateTime") Instant startDateTime,
            @Param("endDateTime") Instant endDateTime,
            Pageable pageable
    );
}