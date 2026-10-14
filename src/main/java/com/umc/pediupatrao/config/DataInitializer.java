package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository usuarioRepo, ProdutoRepository produtoRepo) {
        return args -> {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

            criarUsuario(usuarioRepo, encoder, "admin", "teste123", "ADMIN");
            criarUsuario(usuarioRepo, encoder, "gerente", "gerente123", "GERENTE");
            criarUsuario(usuarioRepo, encoder, "atendente", "atendente123", "ATENDENTE");

            if (produtoRepo.count() == 0) {
                produtoRepo.saveAll(List.of(
                        novoProduto("Pizza Margherita", "Pizza", 49.90),
                        novoProduto("Pizza Calabresa", "Pizza", 52.90),
                        novoProduto("Pizza Quatro Queijos", "Pizza", 58.90),
                        novoProduto("Refrigerante 2L", "Bebida", 12.00),
                        novoProduto("Suco Natural 500ml", "Bebida", 9.50)
                ));
            }

            produtoRepo.findAll().forEach(produto -> {
                if (produto.getQuantidadeEstoque() == null) {
                    produto.setQuantidadeEstoque(20);
                    produtoRepo.save(produto);
                }
            });
        };
    }

    private void criarUsuario(UsuarioRepository repo, BCryptPasswordEncoder encoder,
                              String username, String senha, String role) {
        repo.findByUsername(username).ifPresentOrElse(existente -> {
            if (existente.getRole() == null || existente.getRole().isBlank()) {
                existente.setRole(role);
                repo.save(existente);
            }
        }, () -> {
            Usuario usuario = new Usuario();
            usuario.setUsername(username);
            usuario.setPassword(encoder.encode(senha));
            usuario.setRole(role);
            repo.save(usuario);
        });
    }

    private Produto novoProduto(String nome, String tipo, double preco) {
        Produto produto = new Produto();
        produto.setNome(nome);
        produto.setTipo(tipo);
        produto.setPreco(preco);
        produto.setAtivo(true);
        produto.setQuantidadeEstoque(20);
        return produto;
    }
}
