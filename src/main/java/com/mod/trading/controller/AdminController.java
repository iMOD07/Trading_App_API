package com.mod.trading.controller;

import com.mod.trading.entity.Role;
import com.mod.trading.entity.User;
import com.mod.trading.repository.UserRepository;
import com.mod.trading.service.IbkrConnectionPool;
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

    private final UserRepository userRepository;
    private final IbkrConnectionPool connectionPool;

    // ── Users ──────────────────────────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<List<User>> getUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    // لما Admin يفعّل يوزر → يفتح له IBKR connection تلقائياً
    @PostMapping("/users/{id}/activate")
    public ResponseEntity<?> activate(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(true);
        userRepository.save(user);

        // افتح الاتصال فوراً
        connectionPool.openConnection(user);

        return ResponseEntity.ok(Map.of("message", "User activated and IBKR connection opened"));
    }

    // لما Admin يوقف يوزر → يغلق الاتصال
    @PostMapping("/users/{id}/deactivate")
    public ResponseEntity<?> deactivate(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(false);
        userRepository.save(user);

        // أغلق الاتصال
        connectionPool.closeConnection(id);

        return ResponseEntity.ok(Map.of("message", "User deactivated and IBKR connection closed"));
    }

    @PostMapping("/users/{id}/role")
    public ResponseEntity<?> changeRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setRole(Role.valueOf(body.get("role").toUpperCase()));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Role updated"));
    }

    // ── Connection Pool Stats ──────────────────────────────────────────────────

    @GetMapping("/connections")
    public ResponseEntity<?> getConnectionStats() {
        return ResponseEntity.ok(connectionPool.getStats());
    }
}
