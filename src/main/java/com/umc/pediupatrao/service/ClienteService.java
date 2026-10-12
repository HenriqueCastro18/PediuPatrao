package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.repository.ClienteRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class ClienteService {

    private static final String ENTIDADE = "CLIENTE";

    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoriaService;

    public ClienteService(ClienteRepository clienteRepository, AuditoriaService auditoriaService) {
        this.clienteRepository = clienteRepository;
        this.auditoriaService = auditoriaService;
    }

    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    public void excluir(String id) {
        clienteRepository.findById(id).ifPresent(cliente -> {
            clienteRepository.deleteById(id);
            auditoriaService.registrar("ALTERACAO", ENTIDADE, id,
                    List.of(new CampoAlterado("cliente", cliente.getNome(), "EXCLUIDO")));
        });
    }

    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    public Cliente salvar(Cliente cliente) {
        if (cliente.getId() == null) {
            Cliente salvo = clienteRepository.save(cliente);
            auditoriaService.registrar("INCLUSAO", ENTIDADE, salvo.getId(),
                    List.of(new CampoAlterado("cliente", null, salvo.getNome())));
            return salvo;
        }

        Cliente atual = clienteRepository.findById(cliente.getId())
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado para atualização."));

        List<CampoAlterado> alteracoes = new ArrayList<>();
        diferenca(alteracoes, "nome", atual.getNome(), cliente.getNome());
        diferenca(alteracoes, "telefone", atual.getTelefone(), cliente.getTelefone());
        diferenca(alteracoes, "logradouro", atual.getLogradouro(), cliente.getLogradouro());
        diferenca(alteracoes, "numero", atual.getNumero(), cliente.getNumero());
        diferenca(alteracoes, "complemento", atual.getComplemento(), cliente.getComplemento());
        diferenca(alteracoes, "bairro", atual.getBairro(), cliente.getBairro());
        diferenca(alteracoes, "cidade", atual.getCidade(), cliente.getCidade());
        diferenca(alteracoes, "estado", atual.getEstado(), cliente.getEstado());
        diferenca(alteracoes, "cep", atual.getCep(), cliente.getCep());

        Cliente salvo = clienteRepository.save(cliente);
        if (!alteracoes.isEmpty()) {
            auditoriaService.registrar("ALTERACAO", ENTIDADE, salvo.getId(), alteracoes);
        }
        return salvo;
    }

    private void diferenca(List<CampoAlterado> alteracoes, String campo, String anterior, String novo) {
        String a = anterior == null ? "" : anterior.trim();
        String n = novo == null ? "" : novo.trim();
        if (!Objects.equals(a, n)) {
            alteracoes.add(new CampoAlterado(campo, anterior, novo));
        }
    }
}
