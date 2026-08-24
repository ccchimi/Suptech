package com.suptech.postventa.infrastructure.adapter.out.persistence;

import com.suptech.postventa.domain.model.Caso;
import com.suptech.postventa.domain.port.out.CasoRepositoryPort;
import com.suptech.postventa.infrastructure.adapter.out.persistence.entity.CasoJpaEntity;
import com.suptech.postventa.infrastructure.adapter.out.persistence.repository.SpringDataCasoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CasoPersistenceAdapter implements CasoRepositoryPort {

    private final SpringDataCasoRepository repositorio;

    @Override
    public Caso guardar(Caso caso) {
        CasoJpaEntity entidad = repositorio.findById(caso.id()).orElseGet(CasoJpaEntity::new);
        CasoPersistenceMapper.volcar(caso, entidad);
        return CasoPersistenceMapper.aDominio(repositorio.save(entidad));
    }

    @Override
    public Optional<Caso> buscarPorId(UUID casoId) {
        return repositorio.findById(casoId).map(CasoPersistenceMapper::aDominio);
    }

    @Override
    public List<Caso> buscarPorCliente(String clienteId) {
        return repositorio.findByClienteIdOrderByCreadoEnDesc(clienteId).stream()
                .map(CasoPersistenceMapper::aDominio)
                .toList();
    }
}
