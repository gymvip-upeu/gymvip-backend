package com.gymvip.security;

import com.gymvip.entity.Account;
import com.gymvip.entity.Session;
import com.gymvip.repository.AccountRepository;
import com.gymvip.repository.SessionRepository;
import io.jsonwebtoken.Claims;
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
    private final JwtService jwtService;

    public TokenAuthenticationFilter(SessionRepository sessionRepository,
                                   AccountRepository accountRepository,
                                   JwtService jwtService) {
        this.sessionRepository = sessionRepository;
        this.accountRepository = accountRepository;
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null) {
            Optional<Claims> claimsOpt = jwtService.validateAndExtractClaims(token);
            if (claimsOpt.isPresent()) {
                Claims claims = claimsOpt.get();
                String sessionId = claims.getId();

                Optional<Session> sessionOpt = sessionRepository.findById(sessionId);
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
                    } else {
                        // Purga reactiva de sesiones expiradas en base de datos
                        sessionRepository.delete(session);
                    }
                }
            } else {
                // Si el token expiró pero poseía firma auténtica, purgar reactivamente la sesión de base de datos
                jwtService.extractSessionIdIfValidSignature(token).ifPresent(sessionId -> {
                    sessionRepository.findById(sessionId).ifPresent(session -> {
                        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
                            sessionRepository.delete(session);
                        }
                    });
                });
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
