package com.mod.trading.controller;

import com.mod.trading.entity.User;
import com.mod.trading.exception.UserNotFoundException;
import com.mod.trading.model.AuthResponse;
import com.mod.trading.model.UpdateProfileRequest;
import com.mod.trading.model.dto.UserDto;
import com.mod.trading.repository.UserRepository;
import com.mod.trading.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;
    private final UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<UserDto> getMe(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new UserNotFoundException(userDetails.getUsername()));
        return ResponseEntity.ok(UserDto.from(user));
    }

    @PutMapping("/me")
    public ResponseEntity<AuthResponse> updateMe(@AuthenticationPrincipal UserDetails userDetails,
                                                  @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(authService.updateProfile(userDetails.getUsername(), request));
    }
}
