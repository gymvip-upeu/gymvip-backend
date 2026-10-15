package com.gymvip.service.impl;

import com.gymvip.dto.request.LoginRequest;
import com.gymvip.dto.response.AuthResponse;
import com.gymvip.entity.Account;
import com.gymvip.entity.Session;
import com.gymvip.exception.InvalidCredentialsException;
import com.gymvip.repository.AccountRepository;
import com.gymvip.repository.SessionRepository;
import com.gymvip.service.AuthService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private final AccountRepository accountRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(AccountRepository accountRepository,
                           SessionRepository sessionRepository,
                           PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        String email = request.email() != null ? request.email().trim().toLowerCase() : "";
        Account account = accountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new InvalidCredentialsException("Credenciales de acceso incorrectas"));

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new InvalidCredentialsException("Credenciales de acceso incorrectas");
        }

        if (!"ACTIVO".equalsIgnoreCase(account.getStatus())) {
            throw new InvalidCredentialsException("La cuenta de acceso se encuentra inactiva");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);

        Session session = new Session(token, account.getId(), expiresAt, LocalDateTime.now(), userAgent, ipAddress);
        sessionRepository.save(session);

        return new AuthResponse(token, account.getId(), account.getEmail(), account.getRoles());
    }

    @Override
    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessionRepository.deleteById(token.trim());
        }
    }
}
