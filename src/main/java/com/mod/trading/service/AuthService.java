package com.mod.trading.service;

import com.mod.trading.entity.Role;
import com.mod.trading.entity.User;
import com.mod.trading.exception.BusinessException;
import com.mod.trading.exception.UserNotFoundException;
import com.mod.trading.model.AuthResponse;
import com.mod.trading.model.LoginRequest;
import com.mod.trading.model.RegisterRequest;
import com.mod.trading.model.UpdateProfileRequest;
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
            throw new BusinessException("Username already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        if (request.getIbkrAccountId() != null && !request.getIbkrAccountId().isBlank()) {
            user.setIbkrAccountId(request.getIbkrAccountId());
        }
        user.setRole(Role.USER);
        user.setActive(false);

        userRepository.save(user);
        log.info("New user registered: {}", user.getUsername());

        String token = jwtService.generateToken(user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getRole(),
                jwtService.getExpirationMillis() / 1000);
    }

    @Transactional
    public AuthResponse updateProfile(String authenticatedUsername, UpdateProfileRequest request) {
        User user = userRepository.findByUsername(authenticatedUsername)
                .orElseThrow(() -> new UserNotFoundException(authenticatedUsername));

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getIbkrAccountId() != null && !request.getIbkrAccountId().isBlank()) {
            user.setIbkrAccountId(request.getIbkrAccountId());
        }

        userRepository.save(user);
        log.info("Profile updated: {}", user.getUsername());

        String token = jwtService.generateToken(user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getRole(),
                jwtService.getExpirationMillis() / 1000);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UserNotFoundException(request.getUsername()));

        if (!user.isActive()) {
            throw new BusinessException("Account not activated. Contact administrator.");
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        String token = jwtService.generateToken(user.getUsername());
        log.info("User logged in: {}", user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getRole(),
                jwtService.getExpirationMillis() / 1000);
    }
}
