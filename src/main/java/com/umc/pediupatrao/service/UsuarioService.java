package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.security.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    public boolean redefinirSenha(String username, String novaSenha) {
        return usuarioRepository.findByUsername(username).map(usuario -> {
            usuario.setPassword(passwordEncoder.encode(novaSenha));
            usuarioRepository.save(usuario);
            return true;
        }).orElse(false);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void salvarUsuario(Usuario usuario) {
        if (usuario.getRole() == null || usuario.getRole().isBlank()) {
            usuario.setRole("ATENDENTE");
        }
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        usuarioRepository.save(usuario);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deletarUsuario(String id) {
        usuarioRepository.findById(id).ifPresent(usuario -> {
            if (usuario.getUsername().equals(SecurityUtils.usuarioAtual())) {
                throw new IllegalStateException("Não é possível excluir o próprio usuário.");
            }
            usuarioRepository.deleteById(id);
        });
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Iterable<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void atualizarUsuario(String id, Usuario usuarioAtualizado) {
        usuarioRepository.findById(id).ifPresent(usuarioExistente -> {
            boolean ehProprio = usuarioExistente.getUsername().equals(SecurityUtils.usuarioAtual());
            if (ehProprio && usuarioAtualizado.getRole() != null
                    && !usuarioAtualizado.getRole().equals(usuarioExistente.getRole())) {
                throw new IllegalStateException("Não é possível alterar o próprio perfil.");
            }

            usuarioExistente.setUsername(usuarioAtualizado.getUsername());
            if (usuarioAtualizado.getRole() != null && !usuarioAtualizado.getRole().isBlank()) {
                usuarioExistente.setRole(usuarioAtualizado.getRole());
            }

            if (usuarioAtualizado.getPassword() != null && !usuarioAtualizado.getPassword().isBlank()) {
                usuarioExistente.setPassword(passwordEncoder.encode(usuarioAtualizado.getPassword()));
            }

            usuarioRepository.save(usuarioExistente);
        });
    }
}
