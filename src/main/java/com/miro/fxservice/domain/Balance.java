package com.miro.fxservice.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "balances",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_balances_client_currency",
                        columnNames = {"client_id", "currency"}
                )
        })
public class Balance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * This client_id column is the FK to clients.id.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientAccount client;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /*
     * We keep this concurrency protection later
     * The assignment requires us to prevent double spending
     */
    @Version
    @Column(nullable = false)
    private Long version;

    protected Balance() {
    }

    public Balance(ClientAccount client, String currency, BigDecimal amount) {
        this.client = client;
        this.currency = currency;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public Balance setId(Long id) {
        this.id = id;
        return this;
    }

    public ClientAccount getClient() {
        return client;
    }

    public Balance setClient(ClientAccount client) {
        this.client = client;
        return this;
    }

    public String getCurrency() {
        return currency;
    }

    public Balance setCurrency(String currency) {
        this.currency = currency;
        return this;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Balance setAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    public Long getVersion() {
        return version;
    }

    public Balance setVersion(Long version) {
        this.version = version;
        return this;
    }
}
