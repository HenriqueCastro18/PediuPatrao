package com.umc.pediupatrao;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.service.EmailService;
import com.umc.pediupatrao.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private EmailService emailService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UsuarioService usuarioService;

    private UsuarioService service() {
        if (usuarioService == null) {
            usuarioService = new UsuarioService(usuarioRepository, passwordEncoder, emailService);
        }
        return usuarioService;
    }

    private Usuario usuario(String id, String username, String role, String senhaEmTexto) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setUsername(username);
        u.setRole(role);
        u.setPassword(passwordEncoder.encode(senhaEmTexto));
        return u;
    }

    @Test
    void criarComUsernameDuplicadoBloqueado() {
        when(usuarioRepository.findByUsername("joao")).thenReturn(Optional.of(usuario("1", "joao", "ATENDENTE", "1234")));
        Usuario novo = new Usuario();
        novo.setUsername("joao");
        novo.setPassword("senha123");
        novo.setRole("ATENDENTE");

        assertThrows(IllegalStateException.class, () -> service().salvarUsuario(novo));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void criarComPerfilInvalidoBloqueado() {
        when(usuarioRepository.findByUsername("maria")).thenReturn(Optional.empty());
        Usuario novo = new Usuario();
        novo.setUsername("maria");
        novo.setPassword("senha123");
        novo.setRole("SUPERADMIN");

        assertThrows(IllegalArgumentException.class, () -> service().salvarUsuario(novo));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void criarComSenhaCurtaBloqueado() {
        when(usuarioRepository.findByUsername("ana")).thenReturn(Optional.empty());
        Usuario novo = new Usuario();
        novo.setUsername("ana");
        novo.setPassword("12");
        novo.setRole("GERENTE");

        assertThrows(IllegalArgumentException.class, () -> service().salvarUsuario(novo));
    }

    @Test
    void criarUsuarioValidoCodificaSenha() {
        lenient().when(usuarioRepository.findByUsername("ana")).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        Usuario novo = new Usuario();
        novo.setUsername("ana");
        novo.setPassword("senha123");
        novo.setRole("gerente");

        service().salvarUsuario(novo);

        assertEquals("GERENTE", novo.getRole());
        verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    void rebaixarUltimoAdminBloqueado() {
        Usuario admin = usuario("1", "admin", "ADMIN", "teste123");
        when(usuarioRepository.findById("1")).thenReturn(Optional.of(admin));
        lenient().when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(usuarioRepository.countByRole("ADMIN")).thenReturn(1L);

        Usuario atualizado = new Usuario();
        atualizado.setUsername("admin");
        atualizado.setRole("GERENTE");

        assertThrows(IllegalStateException.class, () -> service().atualizarUsuario("1", atualizado));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void excluirUltimoAdminBloqueado() {
        Usuario admin = usuario("1", "admin", "ADMIN", "teste123");
        when(usuarioRepository.findById("1")).thenReturn(Optional.of(admin));
        when(usuarioRepository.countByRole("ADMIN")).thenReturn(1L);

        assertThrows(IllegalStateException.class, () -> service().deletarUsuario("1"));
        verify(usuarioRepository, never()).deleteById(any());
    }

    @Test
    void trocarPropriaSenhaComAtualErradaBloqueado() {
        Usuario atual = usuario("1", "gerente", "GERENTE", "correta123");

        Usuario sistema = usuario("9", "sistema", "ADMIN", "qualquer1");
        when(usuarioRepository.findByUsername("sistema")).thenReturn(Optional.of(sistema));

        assertThrows(IllegalArgumentException.class,
                () -> service().alterarPropriaSenha("errada", "nova1234", "nova1234"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void trocarPropriaSenhaComSucesso() {
        Usuario sistema = usuario("9", "sistema", "ADMIN", "atual123");
        when(usuarioRepository.findByUsername("sistema")).thenReturn(Optional.of(sistema));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        service().alterarPropriaSenha("atual123", "novaSenha1", "novaSenha1");

        verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    void resetComTokenValidoTrocaSenhaELimpaToken() {
        Usuario u = usuario("1", "joao", "ATENDENTE", "antiga123");
        u.setResetToken("tok-123");
        u.setResetTokenExpiracao(LocalDateTime.now().plusMinutes(10));
        when(usuarioRepository.findByResetToken("tok-123")).thenReturn(Optional.of(u));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        service().redefinirSenhaComToken("tok-123", "novaSenha1", "novaSenha1");

        assertEquals(null, u.getResetToken());
        assertEquals(null, u.getResetTokenExpiracao());
        assertTrue(passwordEncoder.matches("novaSenha1", u.getPassword()));
    }

    @Test
    void resetComTokenExpiradoBloqueado() {
        Usuario u = usuario("1", "joao", "ATENDENTE", "antiga123");
        u.setResetToken("tok-exp");
        u.setResetTokenExpiracao(LocalDateTime.now().minusMinutes(1));
        when(usuarioRepository.findByResetToken("tok-exp")).thenReturn(Optional.of(u));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(IllegalArgumentException.class,
                () -> service().redefinirSenhaComToken("tok-exp", "novaSenha1", "novaSenha1"));

        assertTrue(passwordEncoder.matches("antiga123", u.getPassword()));
    }

    @Test
    void resetComTokenInexistenteBloqueado() {
        when(usuarioRepository.findByResetToken("nao-existe")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> service().redefinirSenhaComToken("nao-existe", "novaSenha1", "novaSenha1"));
    }
}
