package com.mod.trading.service;

import com.mod.trading.entity.ServerInformation;
import com.mod.trading.model.request.ServerInformationRequest;
import com.mod.trading.model.response.ServerInformationResponse;
import com.mod.trading.repository.ServerInformationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServerInformationService {

    private final ServerInformationRepository serverInformationRepository;

    // Register new server
    public ServerInformationResponse registerServer(ServerInformationRequest request) {
        if (serverInformationRepository.findByServerName(request.getServerName()).isPresent()) {
            throw new RuntimeException("Server name already exists");
        }
        if (serverInformationRepository.findByServerHost(request.getServerHost()).isPresent()) {
            throw new RuntimeException("Server host already exists");
        }

        ServerInformation server = new ServerInformation();
        server.setServerName(request.getServerName());
        server.setServerHost(request.getServerHost());
        server.setMode(request.getMode().toUpperCase()); // "LIVE" or "TEST"

        serverInformationRepository.save(server);

        return new ServerInformationResponse(
                server.getServerName(),
                server.getServerHost(),
                server.getMode()
        );
    }

    // Delete server
    public ServerInformationResponse deleteServer(ServerInformationRequest request) {
        ServerInformation server = serverInformationRepository
                .findByServerName(request.getServerName())
                .orElseThrow(() -> new RuntimeException("Server not found"));
        serverInformationRepository.delete(server);

        return new ServerInformationResponse(
                server.getServerName(),
                server.getServerHost(),
                server.getMode()
        );
    }

    // Update server
    public ServerInformationResponse updateServer(ServerInformationRequest request) {
        ServerInformation server = serverInformationRepository
                .findByServerName(request.getServerName())
                .orElseThrow(() -> new RuntimeException("Server not found"));

        server.setServerHost(request.getServerHost());
        server.setMode(request.getMode().toUpperCase());

        serverInformationRepository.save(server);

        return new ServerInformationResponse(
                server.getServerName(),
                server.getServerHost(),
                server.getMode()
        );
    }

    // Get All server
    public List<ServerInformationResponse> getAllServers() {
        return serverInformationRepository.findAll()
                .stream()
                .map(s -> new ServerInformationResponse(
                        s.getServerName(),
                        s.getServerHost(),
                        s.getMode()))
                .toList();
    }


    // Get server info
    public ServerInformationResponse getServer(String serverName) {
        ServerInformation server = serverInformationRepository
                .findByServerName(serverName)
                .orElseThrow(() -> new RuntimeException("Server not found"));

        return new ServerInformationResponse(
                server.getServerName(),
                server.getServerHost(),
                server.getMode()
        );
    }
}
