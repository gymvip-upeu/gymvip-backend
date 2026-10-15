package com.gymvip.security;

import com.gymvip.entity.Account;
import com.gymvip.entity.Session;
import com.gymvip.repository.AccountRepository;
import com.gymvip.repository.SessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final SessionRepository sessionRepository;
    private final AccountRepository accountRepository;

    public TokenAuthenticationFilter(SessionRepository sessionRepository, AccountRepository accountRepository) {
        this.sessionRepository = sessionRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null) {
            Optional<Session> sessionOpt = sessionRepository.findById(token);
            if (sessionOpt.isPresent()) {
                Session session = sessionOpt.get();
                if (session.getExpiresAt().isAfter(LocalDateTime.now())) {
                    Optional<Account> accountOpt = accountRepository.findById(session.getAccountId());
                    if (accountOpt.isPresent() && "ACTIVO".equalsIgnoreCase(accountOpt.get().getStatus())) {
                        Account account = accountOpt.get();
                        List<SimpleGrantedAuthority> authorities = account.getRoles().stream()
                                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                                .collect(Collectors.toList());

                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(account.getEmail(), null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7).trim();
        }
        String customHeader = request.getHeader("X-Auth-Token");
        if (customHeader != null && !customHeader.isBlank()) {
            return customHeader.trim();
        }
        return null;
    }
}
