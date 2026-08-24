package com.suptech.postventa.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "postventa.integracion")
public record IntegracionProperties(Servicio pedidos, Servicio inventario) {

    public record Servicio(
            String baseUrl,
            @DefaultValue("2s") Duration connectTimeout,
            @DefaultValue("5s") Duration readTimeout
    ) { }
}
