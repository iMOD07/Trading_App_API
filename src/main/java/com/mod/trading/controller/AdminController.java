package com.mod.trading.controller;

import com.mod.trading.entity.User;
import com.mod.trading.model.IbkrConfigRequest;
import com.mod.trading.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<User> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getUser(id));
    }

    @PostMapping("/users/{id}/activate")
    public ResponseEntity<Map<String, Object>> activate(@PathVariable Long id) {
        User user = adminService.activateUser(id);
        return ResponseEntity.ok(Map.of(
                "message", "User activated",
                "username", user.getUsername()
        ));
    }

    @PostMapping("/users/{id}/deactivate")
    public ResponseEntity<Map<String, Object>> deactivate(@PathVariable Long id) {
        User user = adminService.deactivateUser(id);
        return ResponseEntity.ok(Map.of(
                "message", "User deactivated",
                "username", user.getUsername()
        ));
    }

    @PostMapping("/users/{id}/role")
    public ResponseEntity<Map<String, Object>> changeRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String role = body.get("role");
        User user = adminService.changeRole(id, role);
        return ResponseEntity.ok(Map.of(
                "message", "Role updated",
                "role", user.getRole()
        ));
    }

    /**
     * Configure IBKR connection settings for a user.
     * The user has their own VPS with IB Gateway running.
     */
    @PostMapping("/users/{id}/ibkr-config")
    public ResponseEntity<Map<String, Object>> configureIbkr(
            @PathVariable Long id,
            @Valid @RequestBody IbkrConfigRequest config) {
        User user = adminService.configureIbkr(id, config);
        return ResponseEntity.ok(Map.of(
                "message", "IBKR settings updated",
                "username", user.getUsername(),
                "host", user.getIbkrHost(),
                "port", user.getIbkrPort(),
                "accountId", user.getIbkrAccountId(),
                "paperTrading", user.isIbkrPaperTrading()
        ));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
        return ResponseEntity.ok(Map.of("message", "User deleted"));
    }

    @GetMapping("/connections/active")
    public ResponseEntity<Map<String, Object>> activeConnections() {
        return ResponseEntity.ok(Map.of(
                "activeConnections", adminService.getActiveConnectionCount()
        ));
    }
}
