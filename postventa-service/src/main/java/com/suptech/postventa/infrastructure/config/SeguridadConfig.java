package com.suptech.postventa.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.net.URI;

@Configuration
@EnableWebSecurity
public class SeguridadConfig {

    private static final String ROL_AGENTE = "AGENTE";
    private static final String ROL_ADMIN = "ADMIN";

    private static final String[] RUTAS_PUBLICAS = {
            "/",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info"
    };

    @Bean
    SecurityFilterChain cadenaFiltros(HttpSecurity http, ObjectMapper mapeador) throws Exception {
        AuthenticationEntryPoint noAutenticado = respuestaProblema(mapeador,
                HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO",
                "Se requieren credenciales para acceder a este recurso");

        AccessDeniedHandler sinPermisos = (peticion, respuesta, excepcion) ->
                escribir(mapeador, respuesta, HttpStatus.FORBIDDEN, "ACCESO_DENEGADO",
                        "Las credenciales no tienen permiso sobre este recurso", peticion.getRequestURI());

        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers(RUTAS_PUBLICAS).permitAll()
                        .requestMatchers("/actuator/**").hasRole(ROL_ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/v1/cancelaciones")
                        .hasAnyRole(ROL_AGENTE, ROL_ADMIN)
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll())
                .httpBasic(basica -> basica.authenticationEntryPoint(noAutenticado))
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(noAutenticado)
                        .accessDeniedHandler(sinPermisos))
                .build();
    }

    private AuthenticationEntryPoint respuestaProblema(ObjectMapper mapeador, HttpStatus estado,
                                                       String titulo, String detalle) {
        return (peticion, respuesta, excepcion) ->
                escribir(mapeador, respuesta, estado, titulo, detalle, peticion.getRequestURI());
    }

    private void escribir(ObjectMapper mapeador, HttpServletResponse respuesta, HttpStatus estado,
                          String titulo, String detalle, String ruta) throws IOException {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        problema.setType(URI.create("https://api.suptech.com/errores/"
                + titulo.toLowerCase().replace('_', '-')));
        problema.setInstance(URI.create(ruta));

        respuesta.setStatus(estado.value());
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        mapeador.writeValue(respuesta.getWriter(), problema);
    }
}
