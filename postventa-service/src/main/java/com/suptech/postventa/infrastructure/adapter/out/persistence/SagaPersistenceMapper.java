package com.suptech.postventa.infrastructure.adapter.out.persistence;

import com.suptech.postventa.domain.model.saga.SagaCancelacion;
import com.suptech.postventa.infrastructure.adapter.out.persistence.entity.SagaCancelacionJpaEntity;

final class SagaPersistenceMapper {

    private SagaPersistenceMapper() {
    }

    static SagaCancelacion aDominio(SagaCancelacionJpaEntity entidad) {
        return SagaCancelacion.rehidratar(
                entidad.getId(),
                entidad.getCasoId(),
                entidad.getPedidoId(),
                entidad.getEstado(),
                entidad.getPasoPendiente(),
                entidad.getIntentos(),
                entidad.getProximoIntentoEn(),
                entidad.getUltimoError(),
                entidad.getCreadoEn(),
                entidad.getActualizadoEn());
    }

    static void volcar(SagaCancelacion saga, SagaCancelacionJpaEntity destino) {
        destino.setId(saga.id());
        destino.setCasoId(saga.casoId());
        destino.setPedidoId(saga.pedidoId());
        destino.setEstado(saga.estado());
        destino.setPasoPendiente(saga.pasoPendiente());
        destino.setIntentos(saga.intentos());
        destino.setProximoIntentoEn(saga.proximoIntentoEn());
        destino.setUltimoError(recortar(saga.ultimoError()));
        destino.setCreadoEn(saga.creadoEn());
        destino.setActualizadoEn(saga.actualizadoEn());
    }

    private static String recortar(String texto) {
        if (texto == null || texto.length() <= 1000) {
            return texto;
        }
        return texto.substring(0, 997) + "...";
    }
}
