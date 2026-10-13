package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ProdutoRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository produtoRepository;

    public List<Produto> listarProdutos() {
        return produtoRepository.findAll();
    }

    public List<Produto> listarAtivos() {
        return produtoRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getAtivo()))
                .toList();
    }

    public Produto buscarPorId(String id) {
        return produtoRepository.findById(id).orElse(null);
    }

    public void excluir(String id) {
        produtoRepository.deleteById(id);
    }

    public Produto salvar(Produto produto) {
        if (produto.getQuantidadeEstoque() == null) {
            produto.setQuantidadeEstoque(0);
        }
        if (produto.getQuantidadeEstoque() < 0) {
            throw new IllegalArgumentException("O estoque não pode ser negativo");
        }
        if (produto.getId() == null) {
            return produtoRepository.save(produto);
        }
        if (produtoRepository.existsById(produto.getId())) {
            return produtoRepository.save(produto);
        }
        throw new IllegalArgumentException("Produto não encontrado para atualização.");
    }
}
