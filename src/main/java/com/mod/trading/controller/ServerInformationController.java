package com.mod.trading.controller;

import com.mod.trading.model.request.ServerInformationRequest;
import com.mod.trading.model.response.ServerInformationResponse;
import com.mod.trading.service.ServerInformationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/server")
@RequiredArgsConstructor
public class ServerInformationController {

    private final ServerInformationService serverInformationService;

    // GET /api/server/{serverName}
    @GetMapping("/{serverName}")
    public ResponseEntity<ServerInformationResponse> getServer(@PathVariable String serverName) {
        return ResponseEntity.ok(serverInformationService.getServer(serverName));
    }

    // GET /api/server/All Server
    @GetMapping
    public ResponseEntity<List<ServerInformationResponse>> getAllServers() {
        return ResponseEntity.ok(serverInformationService.getAllServers());
    }

    // POST /api/server/register
    @PostMapping("/register")
    public ResponseEntity<ServerInformationResponse> registerServer(@Valid @RequestBody ServerInformationRequest request) {
        return ResponseEntity.ok(serverInformationService.registerServer(request));
    }

    // DELETE /api/server/delete
    @DeleteMapping("/delete")
    public ResponseEntity<ServerInformationResponse> deleteServer(@Valid @RequestBody ServerInformationRequest request){
        return ResponseEntity.ok(serverInformationService.deleteServer(request));
    }

    // PUT /api/server/update
    @PutMapping("/update")
    public ResponseEntity<ServerInformationResponse> updateServer(@Valid @RequestBody ServerInformationRequest request) {
        return ResponseEntity.ok(serverInformationService.updateServer(request));
    }


}
