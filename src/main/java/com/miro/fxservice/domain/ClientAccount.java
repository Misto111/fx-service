package com.miro.fxservice.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "clients")
public class ClientAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, unique = true, length = 50)
    private String clientId;

    protected ClientAccount() {
    }

    public ClientAccount(String clientId) {
        this.clientId = clientId;
    }

    public Long getId() {
        return id;
    }


    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

}
