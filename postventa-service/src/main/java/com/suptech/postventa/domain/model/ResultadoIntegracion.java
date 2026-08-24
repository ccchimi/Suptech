package com.suptech.postventa.domain.model;

public sealed interface ResultadoIntegracion {

    record Exitoso(String referenciaExterna) implements ResultadoIntegracion {
        public static Exitoso sinReferencia() {
            return new Exitoso(null);
        }
    }

    record FalloTransitorio(String motivo) implements ResultadoIntegracion { }

    record FalloPermanente(String codigo, String motivo) implements ResultadoIntegracion { }

    default boolean esExitoso() {
        return this instanceof Exitoso;
    }
}
