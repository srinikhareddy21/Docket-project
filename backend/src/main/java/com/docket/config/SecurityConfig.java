package com.docket.config;

import com.docket.auth.RefreshTokenRepository;
import com.docket.security.AuthRateLimitFilter;
import com.docket.security.CsrfCookieFilter;
import com.docket.security.JwtAuthFilter;
import com.docket.security.JwtService;
import com.docket.security.SlidingWindowLimiter;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
public class SecurityConfig {

  @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http, AppProperties props, JwtService jwt, RefreshTokenRepository sessions,
                                  @Qualifier("ipLimiter") SlidingWindowLimiter ipLimiter,
                                  @Qualifier("shareLimiter") SlidingWindowLimiter shareLimiter,
                                  com.docket.extension.ExtensionTokenService extensionTokens) throws Exception {
    CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrf.setCookieCustomizer(c -> c.secure(props.cookie().secure()).sameSite("Lax"));

    http
        .cors(c -> c.configurationSource(corsSource(props)))
        .csrf(c -> c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
            // Auth is a stateless JWT cookie re-read on every request. Without this, Spring treats each authenticated
            // request as a "new login" and clears the XSRF-TOKEN cookie, so every POST/PUT/DELETE after login failed with 403.
            .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy())
            // Bearer-token-only paths: no cookies are used, so CSRF is not applicable.
            .ignoringRequestMatchers(req -> {
              String p = req.getRequestURI();
              String ctx = req.getContextPath();
              if (ctx != null && !ctx.isEmpty() && p.startsWith(ctx)) p = p.substring(ctx.length());
              return p.startsWith("/api/extension/");
            }))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .headers(h -> h
            .frameOptions(f -> f.deny())
            .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
        .authorizeHttpRequests(a -> a
            // Preflight must not require authentication (Chrome may still send OPTIONS for JSON POSTs).
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/refresh",
                "/api/auth/logout", "/api/auth/forgot-password", "/api/auth/reset-password", "/api/auth/confirm-email-change").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/share/*").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/integrations/google-calendar/callback").permitAll()
            .requestMatchers("/api/extension/**").hasRole("EXTENSION")
            .requestMatchers("/api/**").hasRole("USER")
            // SPA shell and static assets (same origin as /api when deployed as one service)
            .anyRequest().permitAll())
        .exceptionHandling(e -> e
            .authenticationEntryPoint((req, res, ex) -> json(res, 401, "Your session has ended. Log in again."))
            .accessDeniedHandler((req, res, ex) -> {
              if (ex instanceof CsrfException) json(res, 403, "Security check failed. Refresh the page and try again.", "CSRF_INVALID");
              else json(res, 403, "You do not have access to this.");
            }))
        .addFilterBefore(new AuthRateLimitFilter(ipLimiter, shareLimiter), CsrfFilter.class)
        .addFilterBefore(new com.docket.extension.ExtensionAuthFilter(extensionTokens), UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(new JwtAuthFilter(jwt, sessions), UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
    return http.build();
  }

  /**
   * Path-based CORS (not UrlBased pattern map) so /api/extension/** never accidentally inherits the
   * SPA config that only allows the frontend origin. Spring CorsFilter returns HTTP 403 when the
   * request Origin is not allowed — that was the extension POST save failure.
   */
  private CorsConfigurationSource corsSource(AppProperties props) {
    List<String> spaOrigins = props.corsAllowedOrigins();
    List<String> extraExt = props.extension().allowedOrigins() == null ? List.of() : props.extension().allowedOrigins();
    return request -> {
      String path = request.getRequestURI();
      String ctx = request.getContextPath();
      if (ctx != null && !ctx.isEmpty() && path.startsWith(ctx)) path = path.substring(ctx.length());
      if (path.startsWith("/api/extension/")) {
        CorsConfiguration x = new CorsConfiguration();
        java.util.ArrayList<String> patterns = new java.util.ArrayList<>();
        patterns.add("chrome-extension://*");
        patterns.add("http://localhost:*");
        patterns.add("http://127.0.0.1:*");
        for (String o : extraExt) {
          if (o != null && !o.isBlank() && !patterns.contains(o.trim())) patterns.add(o.trim());
        }
        x.setAllowedOriginPatterns(patterns);
        x.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        x.setAllowedHeaders(List.of("Content-Type", "Accept", "Authorization"));
        x.setAllowCredentials(false);
        x.setMaxAge(3600L);
        return x;
      }
      CorsConfiguration c = new CorsConfiguration();
      c.setAllowedOrigins(spaOrigins);
      c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
      c.setAllowedHeaders(List.of("Content-Type", "Accept", "X-XSRF-TOKEN", "Authorization"));
      c.setAllowCredentials(true);
      c.setMaxAge(3600L);
      return c;
    };
  }

  private static void json(HttpServletResponse res, int status, String message) throws java.io.IOException { json(res, status, message, null); }

  private static void json(HttpServletResponse res, int status, String message, String code) throws java.io.IOException {
    res.setStatus(status);
    res.setContentType("application/json");
    res.setCharacterEncoding(StandardCharsets.UTF_8.name());
    res.getWriter().write("{\"message\":\"" + message + "\",\"fieldErrors\":{}" + (code == null ? "" : ",\"code\":\"" + code + "\"") + "}");
  }
}
