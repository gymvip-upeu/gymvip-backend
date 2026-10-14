package com.gymvip.shared.security;

import com.gymvip.identity.application.port.in.AuthUseCase;
import com.gymvip.identity.domain.model.Account;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final AuthUseCase authUseCase;

    public TokenAuthenticationFilter(@org.springframework.context.annotation.Lazy AuthUseCase authUseCase) {
        this.authUseCase = authUseCase;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null) {
            Optional<Account> accountOpt = authUseCase.findAccountByToken(token);
            if (accountOpt.isPresent()) {
                Account account = accountOpt.get();
                List<SimpleGrantedAuthority> authorities = account.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                        .collect(Collectors.toList());

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(account.getEmail(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
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
