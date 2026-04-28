package com.mod.trading.service;

import com.mod.trading.entity.Role;
import com.mod.trading.entity.User;
import com.mod.trading.model.response.AuthResponse;
import com.mod.trading.model.request.LoginRequest;
import com.mod.trading.model.request.RegisterRequest;
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
    private final IbkrConnectionPool connectionPool;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER);
        user.setActive(false); // Admin يفعّله
        user.setIbkrAccount(request.getIbkrAccount());
        user.setIbkrHost(request.getIbkrHost());
        user.setIbkrPort(request.getIbkrPort());
        user.setIbkrClientId(request.getIbkrClientId());

        userRepository.save(user);

        // ملاحظة: الاتصال لا يُفتح هنا - ينتظر Admin يفعّل اليوزر
        //
        String token = jwtService.generateToken(user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getRole());
    }

    public AuthResponse update(RegisterRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        // لو غيّر إعدادات IBKR → أعد الاتصال
        boolean ibkrChanged = false;
        if (request.getIbkrHost() != null && !request.getIbkrHost().isBlank()) {
            user.setIbkrHost(request.getIbkrHost());
            user.setIbkrPort(request.getIbkrPort());
            user.setIbkrClientId(request.getIbkrClientId());
            user.setIbkrAccount(request.getIbkrAccount());
            ibkrChanged = true;
        }

        userRepository.save(user);

        if (ibkrChanged && user.isActive()) {
            connectionPool.openConnection(user);
        }

        String token = jwtService.generateToken(user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getRole());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.isActive()) {
            throw new RuntimeException("User Not Active, Call Supervisor");
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        String token = jwtService.generateToken(user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getRole());
    }
}
