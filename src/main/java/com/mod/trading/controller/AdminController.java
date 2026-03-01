package com.mod.trading.controller;

import com.mod.trading.entity.Role;
import com.mod.trading.entity.User;
import com.mod.trading.repository.UserRepository;
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

    // GET /api/admin/users
    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    // POST /api/admin/users/{id}/activate
    @PostMapping("/users/{id}/activate")
    public ResponseEntity<?> activateUser(@PathVariable Long id) {
        return toggleActive(id, true);
    }

    // POST /api/admin/users/{id}/deactivate
    @PostMapping("/users/{id}/deactivate")
    public ResponseEntity<?> deactivateUser(@PathVariable Long id) {
        return toggleActive(id, false);
    }

    // POST /api/admin/users/{id}/role
    @PostMapping("/users/{id}/role")
    public ResponseEntity<?> changeRole(@PathVariable Long id,
                                        @RequestBody Map<String, String> body) {
        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            user.setRole(Role.valueOf(body.get("role").toUpperCase()));
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "Role updated to " + user.getRole()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // =============================================

    private ResponseEntity<?> toggleActive(Long id, boolean active) {
        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            user.setActive(active);
            userRepository.save(user);
            String status = active ? "activated" : "deactivated";
            return ResponseEntity.ok(Map.of("message", "User " + user.getUsername() + " " + status));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}