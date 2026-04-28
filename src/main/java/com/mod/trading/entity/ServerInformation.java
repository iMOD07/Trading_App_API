package com.mod.trading.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "server_information")
public class ServerInformation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String serverName;

    @Column(unique = true, nullable = false)
    private String serverHost;

    // "LIVE" or "TEST"
    @Column(nullable = false)
    private String mode;
}
