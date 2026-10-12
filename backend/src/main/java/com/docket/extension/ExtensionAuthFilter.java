package com.docket.extension;

import com.docket.security.AuthUser;
import com.docket.security.JsonErrors;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates /api/extension/** from "Authorization: Bearer dkx_..." ONLY. Browser cookies are never honoured on these
 * paths (JwtAuthFilter skips them), which is why they are exempt from CSRF: a forged cross-site request carries no token.
 * A token grants ROLE_EXTENSION, which cannot reach any other endpoint.
 */
public class ExtensionAuthFilter extends OncePerRequestFilter {
  private final ExtensionTokenService tokens;
  public ExtensionAuthFilter(ExtensionTokenService tokens) { this.tokens = tokens; }

  public static boolean isExtensionPath(String uri) {
    if (uri == null) return false;
    // Strip matrix/session suffixes just in case a proxy adds them.
    int semi = uri.indexOf(';');
    if (semi >= 0) uri = uri.substring(0, semi);
    return uri.startsWith("/api/extension/");
  }

  public static String servletPath(HttpServletRequest req) {
    String p = req.getRequestURI();
    String ctx = req.getContextPath();
    if (ctx != null && !ctx.isEmpty() && p.startsWith(ctx)) p = p.substring(ctx.length());
    return p;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest req) {
    // OPTIONS is authorized via permitAll in SecurityConfig; skip token check.
    return !isExtensionPath(servletPath(req)) || "OPTIONS".equals(req.getMethod());
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
    String h = req.getHeader("Authorization");
    String token = null;
    if (h != null) {
      h = h.trim();
      if (h.regionMatches(true, 0, "Bearer ", 0, 7)) token = h.substring(7).trim();
    }
    var owner = tokens.authenticate(token);
    if (owner.isEmpty()) {
      JsonErrors.write(res, 401, "This extension token is missing, expired or revoked. Create a new one in DOCKET settings.");
      return;
    }
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(new AuthUser(owner.get(), "extension"), null, List.of(new SimpleGrantedAuthority("ROLE_EXTENSION"))));
    try {
      chain.doFilter(req, res);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
