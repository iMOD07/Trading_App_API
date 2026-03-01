package com.mod.trading.service;

import com.mod.trading.entity.Role;
import com.mod.trading.entity.User;
import com.mod.trading.model.AuthResponse;
import com.mod.trading.model.LoginRequest;
import com.mod.trading.model.RegisterRequest;
import com.mod.trading.repository.UserRepository;
import com.mod.trading.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    // Register new user
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setAlpacaApiKey(request.getAlpacaApiKey());
        user.setAlpacaApiSecret(request.getAlpacaApiSecret());
        user.setAlpacaBaseUrl(request.getAlpacaBaseUrl());
        user.setRole(Role.USER);
        user.setActive(false);

        userRepository.save(user);

        String token = jwtService.generateToken(user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getRole());
    }


    public AuthResponse update(RegisterRequest request) {

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getAlpacaApiKey() != null && !request.getAlpacaApiKey().isBlank()) {
            user.setAlpacaApiKey(request.getAlpacaApiKey());
        }
        if (request.getAlpacaApiSecret() != null && !request.getAlpacaApiSecret().isBlank()) {
            user.setAlpacaApiSecret(request.getAlpacaApiSecret());
        }
        if (request.getAlpacaBaseUrl() != null && !request.getAlpacaBaseUrl().isBlank()) {
            user.setAlpacaBaseUrl(request.getAlpacaBaseUrl());
        }

        userRepository.save(user);

        String token = jwtService.generateToken(user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getRole());
    }

    // login
    public AuthResponse login(LoginRequest request) {

        // check User
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // check Active
        if (!user.isActive()) {
            throw new RuntimeException("User Not Active, Call Supervisor");
        }

        // check Password
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        String token = jwtService.generateToken(user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getRole());
    }
}
