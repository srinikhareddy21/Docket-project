package com.docket.security;

import com.docket.auth.RefreshTokenRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates from the HttpOnly access cookie. The token is only honoured while its login session
 * (token family) is still active, so logout and password reset take effect immediately.
 * Not a Spring bean on purpose: SecurityConfig adds it to the security chain exactly once.
 */
public class JwtAuthFilter extends OncePerRequestFilter {
  private final JwtService jwt;
  private final RefreshTokenRepository sessions;

  public JwtAuthFilter(JwtService jwt, RefreshTokenRepository sessions) { this.jwt = jwt; this.sessions = sessions; }

  /** Extension endpoints accept only the extension Bearer token, never cookies (see ExtensionAuthFilter). */
  @Override
  protected boolean shouldNotFilter(HttpServletRequest req) { return com.docket.extension.ExtensionAuthFilter.isExtensionPath(com.docket.extension.ExtensionAuthFilter.servletPath(req)); }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String token = CookieService.read(req, CookieService.ACCESS);
    if (token != null && !token.isBlank()) {
      jwt.verify(token).ifPresent(u -> {
        if (sessions.countActive(u.sid(), u.userId(), Instant.now()) > 0) {
          SecurityContextHolder.getContext().setAuthentication(
              new UsernamePasswordAuthenticationToken(u, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        }
      });
    }
    chain.doFilter(req, res);
  }
}
