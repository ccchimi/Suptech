package com.suptech.postventa.infrastructure.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class RestClientConfig {

    public static final String CABECERA_IDEMPOTENCIA = "Idempotency-Key";

    @Bean
    RestClient pedidosRestClient(RestClient.Builder builder, IntegracionProperties propiedades) {
        return construir(builder, propiedades.pedidos(), "postventa-service/pedidos");
    }

    @Bean
    RestClient inventarioRestClient(RestClient.Builder builder, IntegracionProperties propiedades) {
        return construir(builder, propiedades.inventario(), "postventa-service/inventario");
    }

    private RestClient construir(RestClient.Builder builder,
                                 IntegracionProperties.Servicio servicio,
                                 String userAgent) {
        var settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(servicio.connectTimeout())
                .withReadTimeout(servicio.readTimeout());

        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.jdk().build(settings);

        return builder
                .baseUrl(servicio.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .build();
    }
}
