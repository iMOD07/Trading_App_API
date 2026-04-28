package com.mod.trading.controller;

import com.mod.trading.entity.Role;
import com.mod.trading.entity.User;
import com.mod.trading.exception.BusinessException;
import com.mod.trading.exception.UserNotFoundException;
import com.mod.trading.model.dto.UserDto;
import com.mod.trading.repository.UserRepository;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;

    @GetMapping("/users")
    public ResponseEntity<List<UserDto>> getAllUsers() {
        List<UserDto> users = userRepository.findAll().stream()
                .map(UserDto::from)
                .toList();
        return ResponseEntity.ok(users);
    }

    @PostMapping("/users/{id}/activate")
    @Transactional
    public ResponseEntity<Map<String, String>> activateUser(@PathVariable Long id) {
        return toggleActive(id, true);
    }

    @PostMapping("/users/{id}/deactivate")
    @Transactional
    public ResponseEntity<Map<String, String>> deactivateUser(@PathVariable Long id) {
        return toggleActive(id, false);
    }

    @PostMapping("/users/{id}/role")
    @Transactional
    public ResponseEntity<Map<String, String>> changeRole(@PathVariable Long id,
                                                          @RequestBody RoleChangeRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("id=" + id));
        try {
            user.setRole(Role.valueOf(request.getRole().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid role: " + request.getRole());
        }
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Role updated to " + user.getRole()));
    }

    @PostMapping("/users/{id}/trading/{enabled}")
    @Transactional
    public ResponseEntity<Map<String, String>> setTradingEnabled(@PathVariable Long id,
                                                                  @PathVariable boolean enabled) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("id=" + id));
        user.setTradingEnabled(enabled);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of(
                "message", "Trading " + (enabled ? "enabled" : "disabled") + " for " + user.getUsername()));
    }

    private ResponseEntity<Map<String, String>> toggleActive(Long id, boolean active) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("id=" + id));
        user.setActive(active);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of(
                "message", "User " + user.getUsername() + " " + (active ? "activated" : "deactivated")));
    }

    @Data
    public static class RoleChangeRequest {
        @NotBlank
        private String role;
    }
}
