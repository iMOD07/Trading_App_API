package com.mod.trading.service;

import com.mod.trading.entity.Role;
import com.mod.trading.entity.User;
import com.mod.trading.ibkr.IbkrConnectionPool;
import com.mod.trading.ibkr.IbkrException;
import com.mod.trading.model.IbkrConfigRequest;
import com.mod.trading.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final IbkrConnectionPool connectionPool;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IbkrException("User not found"));
    }

    @Transactional
    public User activateUser(Long id) {
        User user = getUser(id);
        user.setActive(true);
        log.info("Activated user: {}", user.getUsername());
        return userRepository.save(user);
    }

    @Transactional
    public User deactivateUser(Long id) {
        User user = getUser(id);
        user.setActive(false);
        // Disconnect their IBKR if connected
        connectionPool.disconnectUser(id);
        log.info("Deactivated user: {}", user.getUsername());
        return userRepository.save(user);
    }

    @Transactional
    public User changeRole(Long id, String roleStr) {
        User user = getUser(id);
        try {
            user.setRole(Role.valueOf(roleStr.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new IbkrException("Invalid role: " + roleStr);
        }
        log.info("Changed role of {} to {}", user.getUsername(), user.getRole());
        return userRepository.save(user);
    }

    /**
     * Configure IBKR settings for a user.
     * This is critical - sets the VPS connection details.
     */
    @Transactional
    public User configureIbkr(Long id, IbkrConfigRequest config) {
        User user = getUser(id);

        // Disconnect existing connection if config changed
        connectionPool.disconnectUser(id);

        user.setIbkrHost(config.getIbkrHost().trim());
        user.setIbkrPort(config.getIbkrPort());
        user.setIbkrClientId(config.getIbkrClientId());
        user.setIbkrAccountId(config.getIbkrAccountId().trim());
        user.setIbkrPaperTrading(config.getIbkrPaperTrading());

        log.info("Configured IBKR for user {}: {}:{} account={}",
                user.getUsername(), config.getIbkrHost(), config.getIbkrPort(),
                config.getIbkrAccountId());

        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = getUser(id);
        connectionPool.disconnectUser(id);
        userRepository.delete(user);
        log.info("Deleted user: {}", user.getUsername());
    }

    public int getActiveConnectionCount() {
        return connectionPool.getActiveConnectionCount();
    }
}
