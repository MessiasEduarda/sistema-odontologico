package br.com.odontosaas.identity.security;

import br.com.odontosaas.identity.access.AccessQueries;
import br.com.odontosaas.identity.session.SessionCookies;
import br.com.odontosaas.identity.session.SessionStore;
import br.com.odontosaas.shared.web.ProblemResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfException;

/**
 * Segurança HTTP (ADR-0005):
 * <ul>
 *   <li>autenticação por sessão opaca em cookie httpOnly ({@link SessionAuthenticationFilter});
 *   <li>CSRF com cookie {@code XSRF-TOKEN} + header {@code X-XSRF-TOKEN} em toda requisição que altera estado;
 *   <li>negação por padrão: só rotas públicas explícitas são liberadas;
 *   <li>autorização fina por permissão com {@code @PreAuthorize} nos controllers.
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, SessionStore sessions, SessionCookies cookies, AccessQueries access) throws Exception {
        http.authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/api/v1/public/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/error")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/password/forgot",
                                "/api/v1/auth/password/reset")
                        .permitAll()
                        .requestMatchers("/api/v1/platform/**")
                        .hasRole("PLATFORM_ADMIN")
                        .anyRequest()
                        .authenticated())
                .csrf(csrf -> csrf.spa())
                .addFilterBefore(
                        new SessionAuthenticationFilter(sessions, cookies, access), AnonymousAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'"))
                        .frameOptions(frame -> frame.deny()))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                                (request, response, e) -> ProblemResponseWriter.write(
                                        response,
                                        401,
                                        "UNAUTHENTICATED",
                                        "Não autenticado",
                                        "É necessário estar autenticado para acessar este recurso."))
                        .accessDeniedHandler((request, response, e) -> {
                            if (e instanceof CsrfException) {
                                ProblemResponseWriter.write(
                                        response,
                                        403,
                                        "CSRF_INVALID",
                                        "Requisição recusada",
                                        "Token de segurança ausente ou inválido. Recarregue a página e tente novamente.");
                            } else {
                                ProblemResponseWriter.write(
                                        response,
                                        403,
                                        "FORBIDDEN",
                                        "Acesso negado",
                                        "Você não tem permissão para executar esta ação.");
                            }
                        }));
        return http.build();
    }
}
