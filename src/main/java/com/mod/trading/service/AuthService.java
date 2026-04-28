package com.mod.trading.service;

import com.mod.trading.entity.Role;
import com.mod.trading.entity.User;
import com.mod.trading.ibkr.IbkrException;
import com.mod.trading.model.AuthResponse;
import com.mod.trading.model.LoginRequest;
import com.mod.trading.model.RegisterRequest;
import com.mod.trading.repository.UserRepository;
import com.mod.trading.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IbkrException("Username already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER);
        user.setActive(false);  // Admin must activate AND configure IBKR
        // IBKR fields are NULL - admin will set them later

        userRepository.save(user);
        log.info("Registered new user: {}", user.getUsername());

        // No token returned - user must wait for admin approval
        return new AuthResponse(null, user.getUsername(), user.getRole(), false);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IbkrException("Invalid credentials"));

        if (!user.isActive()) {
            throw new IbkrException("Account not active. Please contact admin.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(), request.getPassword())
            );
        } catch (Exception e) {
            throw new IbkrException("Invalid credentials");
        }

        String token = jwtService.generateToken(user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getRole(), user.isIbkrConfigured());
    }

    @Transactional
    public void changePassword(String username, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IbkrException("User not found"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
