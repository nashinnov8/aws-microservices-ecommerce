package com.ecommerce.authservice.filter;

import com.ecommerce.authservice.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

public class AuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private static final String HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    public AuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // Extract the token from Authorization header
        String authHeader = request.getHeader(HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = authHeader.substring(BEARER_PREFIX.length());

            // Validate token
            if (!jwtService.validateToken(token, "ACCESS")) {
                throw new BadCredentialsException("Invalid or expired access token");
            }

            Claims claims = jwtService.getClaims(token);

            // Extract the userId and role from claims
            String userId = claims.getSubject();
            String role = claims.get("role", String.class);

            // Convert role string to GrantedAuthority collection
            Collection<GrantedAuthority> authorities = Collections.singleton(
                    new SimpleGrantedAuthority("ROLE_" + role)
            );

            // Use Spring Security's Authentication context
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            authorities
                    );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);

        } catch (BadCredentialsException e) {
            throw e;
        } catch (Exception e) {
            // For any other exception, also throw BadCredentialsException
            throw new BadCredentialsException("Authentication failed: " + e.getMessage(), e);
        }
    }

}
