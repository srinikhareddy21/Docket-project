package com.docket.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/** Forces the XSRF-TOKEN cookie to be written on every response so the SPA can echo it in X-XSRF-TOKEN. */
public class CsrfCookieFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    CsrfToken token = (CsrfToken) req.getAttribute(CsrfToken.class.getName());
    if (token != null) token.getToken();
    chain.doFilter(req, res);
  }
}
