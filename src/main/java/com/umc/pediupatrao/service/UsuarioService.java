package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class UsuarioService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final Set<String> PERFIS_VALIDOS = Set.of("ADMIN", "GERENTE", "ATENDENTE");
    private static final int SENHA_MINIMA = 4;
    private static final Pattern EMAIL_REGEX = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.reset-token.validade-minutos:30}")
    private int validadeMinutos;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                          EmailService emailService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Iterable<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void salvarUsuario(Usuario usuario) {
        String username = normalizar(usuario.getUsername());
        validarUsername(username);
        garantirUsernameUnico(username, null);

        String role = normalizarRole(usuario.getRole());
        if (role == null) {
            throw new IllegalArgumentException("Selecione o perfil do usuário.");
        }
        validarRole(role);

        validarSenha(usuario.getPassword());

        String email = normalizar(usuario.getEmail());
        validarEmail(email);

        usuario.setUsername(username);
        usuario.setEmail(email);
        usuario.setRole(role);
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        usuarioRepository.save(usuario);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void atualizarUsuario(String id, Usuario usuarioAtualizado) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

        boolean ehProprio = existente.getUsername().equals(SecurityUtils.usuarioAtual());

        String novoUsername = normalizar(usuarioAtualizado.getUsername());
        validarUsername(novoUsername);
        garantirUsernameUnico(novoUsername, id);

        String novaRole = normalizarRole(usuarioAtualizado.getRole());
        if (novaRole != null) {
            validarRole(novaRole);
            if (ehProprio && !novaRole.equals(existente.getRole())) {
                throw new IllegalStateException("Não é possível alterar o próprio perfil.");
            }

            if (ROLE_ADMIN.equals(existente.getRole()) && !ROLE_ADMIN.equals(novaRole)
                    && usuarioRepository.countByRole(ROLE_ADMIN) <= 1) {
                throw new IllegalStateException("Não é possível rebaixar o último administrador do sistema.");
            }
            existente.setRole(novaRole);
        }

        existente.setUsername(novoUsername);

        String novoEmail = normalizar(usuarioAtualizado.getEmail());
        validarEmail(novoEmail);
        existente.setEmail(novoEmail);

        String novaSenha = usuarioAtualizado.getPassword();
        if (novaSenha != null && !novaSenha.isBlank()) {
            validarSenha(novaSenha);
            existente.setPassword(passwordEncoder.encode(novaSenha));
        }

        usuarioRepository.save(existente);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deletarUsuario(String id) {
        usuarioRepository.findById(id).ifPresent(usuario -> {
            if (usuario.getUsername().equals(SecurityUtils.usuarioAtual())) {
                throw new IllegalStateException("Não é possível excluir o próprio usuário.");
            }

            if (ROLE_ADMIN.equals(usuario.getRole()) && usuarioRepository.countByRole(ROLE_ADMIN) <= 1) {
                throw new IllegalStateException("Não é possível excluir o último administrador do sistema.");
            }
            usuarioRepository.deleteById(id);
        });
    }

    public void alterarPropriaSenha(String senhaAtual, String novaSenha, String confirmacao) {
        String username = SecurityUtils.usuarioAtual();
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Sessão inválida. Faça login novamente."));

        if (senhaAtual == null || !passwordEncoder.matches(senhaAtual, usuario.getPassword())) {
            throw new IllegalArgumentException("Senha atual incorreta.");
        }
        if (novaSenha == null || !novaSenha.equals(confirmacao)) {
            throw new IllegalArgumentException("A confirmação não corresponde à nova senha.");
        }
        validarSenha(novaSenha);
        if (passwordEncoder.matches(novaSenha, usuario.getPassword())) {
            throw new IllegalArgumentException("A nova senha deve ser diferente da atual.");
        }

        usuario.setPassword(passwordEncoder.encode(novaSenha));
        usuarioRepository.save(usuario);
    }

    public void solicitarResetSenha(String email, String baseUrl) {
        String alvo = normalizar(email);
        if (alvo == null || alvo.isBlank()) {
            return;
        }
        usuarioRepository.findByEmailIgnoreCase(alvo).ifPresent(usuario -> {
            String token = gerarToken();
            usuario.setResetToken(token);
            usuario.setResetTokenExpiracao(LocalDateTime.now().plusMinutes(validadeMinutos));
            usuarioRepository.save(usuario);

            String link = baseUrl + "/redefinir-senha?token=" + token;
            emailService.enviarLinkResetSenha(usuario.getEmail(), link, validadeMinutos);
        });
    }

    public void redefinirSenhaComToken(String token, String novaSenha, String confirmacao) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Link de redefinição inválido.");
        }
        Usuario usuario = usuarioRepository.findByResetToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Link de redefinição inválido ou já utilizado."));

        if (usuario.getResetTokenExpiracao() == null
                || usuario.getResetTokenExpiracao().isBefore(LocalDateTime.now())) {
            usuario.setResetToken(null);
            usuario.setResetTokenExpiracao(null);
            usuarioRepository.save(usuario);
            throw new IllegalArgumentException("O link de redefinição expirou. Solicite um novo.");
        }
        if (novaSenha == null || !novaSenha.equals(confirmacao)) {
            throw new IllegalArgumentException("A confirmação não corresponde à nova senha.");
        }
        validarSenha(novaSenha);

        usuario.setPassword(passwordEncoder.encode(novaSenha));
        usuario.setResetToken(null);
        usuario.setResetTokenExpiracao(null);
        usuarioRepository.save(usuario);
    }

    public boolean tokenResetValido(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return usuarioRepository.findByResetToken(token)
                .filter(u -> u.getResetTokenExpiracao() != null
                        && u.getResetTokenExpiracao().isAfter(LocalDateTime.now()))
                .isPresent();
    }

    private String gerarToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void validarEmail(String email) {
        if (email != null && !email.isBlank() && !EMAIL_REGEX.matcher(email).matches()) {
            throw new IllegalArgumentException("E-mail inválido.");
        }
    }

    private String normalizar(String valor) {
        return valor == null ? null : valor.trim();
    }

    private String normalizarRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        return role.trim().toUpperCase();
    }

    private void validarUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("O nome de usuário é obrigatório.");
        }
        if (username.length() < 3) {
            throw new IllegalArgumentException("O nome de usuário deve ter ao menos 3 caracteres.");
        }
    }

    private void garantirUsernameUnico(String username, String idAtual) {
        usuarioRepository.findByUsername(username).ifPresent(outro -> {
            if (idAtual == null || !outro.getId().equals(idAtual)) {
                throw new IllegalStateException("Já existe um usuário com esse nome.");
            }
        });
    }

    private void validarRole(String role) {
        if (role != null && !PERFIS_VALIDOS.contains(role)) {
            throw new IllegalArgumentException("Perfil inválido. Use ADMIN, GERENTE ou ATENDENTE.");
        }
    }

    private void validarSenha(String senha) {
        if (senha == null || senha.length() < SENHA_MINIMA) {
            throw new IllegalArgumentException("A senha deve ter ao menos " + SENHA_MINIMA + " caracteres.");
        }
    }
}
