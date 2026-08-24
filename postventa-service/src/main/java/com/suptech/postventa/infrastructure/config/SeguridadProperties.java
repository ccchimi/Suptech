package com.suptech.postventa.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "postventa.seguridad")
public record SeguridadProperties(Credenciales agente, Credenciales admin) {

    public record Credenciales(String usuario, String clave) { }
}
