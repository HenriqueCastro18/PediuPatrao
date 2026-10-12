package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Auditoria;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.Repository;

import java.util.List;

public interface AuditoriaRepository extends Repository<Auditoria, String> {

    List<Auditoria> findAll(Sort sort);

    List<Auditoria> findByEntidadeOrderByDataHoraDesc(String entidade);
}
