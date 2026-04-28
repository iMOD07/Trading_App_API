package com.mod.trading.repository;

import com.mod.trading.entity.ServerInformation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServerInformationRepository extends JpaRepository<ServerInformation, Long> {
    Optional<ServerInformation> findByServerName(String serverName);
    Optional<ServerInformation> findByServerHost(String serverHost);
}
