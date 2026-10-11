package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import com.umc.pediupatrao.security.SecurityUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuditoriaService {

    private final AuditoriaRepository auditoriaRepository;
    private final MongoTemplate mongoTemplate;

    public AuditoriaService(AuditoriaRepository auditoriaRepository, MongoTemplate mongoTemplate) {
        this.auditoriaRepository = auditoriaRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public void registrar(String operacao, String entidade, String entidadeId,
                          List<CampoAlterado> campos, String justificativa) {
        Auditoria auditoria = new Auditoria();
        auditoria.setUsuario(SecurityUtils.usuarioAtual());
        auditoria.setPerfil(SecurityUtils.perfilAtual());
        auditoria.setDataHora(LocalDateTime.now());
        auditoria.setOperacao(operacao);
        auditoria.setEntidade(entidade);
        auditoria.setEntidadeId(entidadeId);
        auditoria.setCamposAlterados(campos != null ? campos : new ArrayList<>());
        auditoria.setJustificativa(justificativa);
        mongoTemplate.insert(auditoria);
    }

    public void registrar(String operacao, String entidade, String entidadeId, List<CampoAlterado> campos) {
        registrar(operacao, entidade, entidadeId, campos, null);
    }

    public List<Auditoria> listarTodos() {
        return auditoriaRepository.findAll(Sort.by(Sort.Direction.DESC, "dataHora"));
    }

    public List<Auditoria> listarPorEntidade(String entidade) {
        return auditoriaRepository.findByEntidadeOrderByDataHoraDesc(entidade);
    }
}
