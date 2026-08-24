package com.suptech.postventa.infrastructure.adapter.out.persistence.repository;

import com.suptech.postventa.infrastructure.adapter.out.persistence.entity.CasoJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataCasoRepository extends JpaRepository<CasoJpaEntity, UUID> {

    List<CasoJpaEntity> findByClienteIdOrderByCreadoEnDesc(String clienteId);
}
