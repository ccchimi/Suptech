package com.suptech.postventa.infrastructure.adapter.out.persistence;

import com.suptech.postventa.domain.model.Caso;
import com.suptech.postventa.domain.model.LineaAfectada;
import com.suptech.postventa.infrastructure.adapter.out.persistence.entity.CasoJpaEntity;
import com.suptech.postventa.infrastructure.adapter.out.persistence.entity.LineaCasoEmbeddable;

import java.util.ArrayList;
import java.util.List;

final class CasoPersistenceMapper {

    private CasoPersistenceMapper() {
    }

    static Caso aDominio(CasoJpaEntity entidad) {
        List<LineaAfectada> lineas = entidad.getLineas().stream()
                .map(linea -> new LineaAfectada(linea.getSku(), linea.getCantidad(), linea.getMotivoDetalle()))
                .toList();

        return Caso.rehidratar(
                entidad.getId(),
                entidad.getPedidoId(),
                entidad.getClienteId(),
                entidad.getTipo(),
                entidad.getMotivo(),
                entidad.getMontoSolicitado(),
                lineas,
                entidad.getEstado(),
                entidad.getResolucion(),
                entidad.getCreadoEn(),
                entidad.getActualizadoEn());
    }

    static void volcar(Caso caso, CasoJpaEntity destino) {
        destino.setId(caso.id());
        destino.setPedidoId(caso.pedidoId());
        destino.setClienteId(caso.clienteId());
        destino.setTipo(caso.tipo());
        destino.setEstado(caso.estado());
        destino.setMotivo(caso.motivo());
        destino.setMontoSolicitado(caso.montoSolicitado());
        destino.setResolucion(caso.resolucion());
        destino.setCreadoEn(caso.creadoEn());
        destino.setActualizadoEn(caso.actualizadoEn());

        List<LineaCasoEmbeddable> lineas = caso.lineas().stream()
                .map(linea -> new LineaCasoEmbeddable(linea.sku(), linea.cantidad(), linea.motivoDetalle()))
                .toList();
        destino.getLineas().clear();
        destino.getLineas().addAll(new ArrayList<>(lineas));
    }
}
