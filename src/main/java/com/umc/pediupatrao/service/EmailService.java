package com.umc.pediupatrao.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.from:nao-responder@pediupatrao.com}")
    private String remetente;

    @Value("${spring.mail.host:}")
    private String smtpHost;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    public void enviarLinkResetSenha(String destinatario, String link, int validadeMinutos) {
        String assunto = "Pediu Patrão — redefinição de senha";
        String corpo = """
                Olá,

                Recebemos uma solicitação para redefinir a senha da sua conta no Pediu Patrão.
                Para criar uma nova senha, acesse o link abaixo (válido por %d minutos):

                %s

                Se você não solicitou a redefinição, ignore este e-mail: sua senha permanece inalterada.

                Equipe Pediu Patrão
                """.formatted(validadeMinutos, link);

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null || smtpHost == null || smtpHost.isBlank()) {
            log.warn("SMTP não configurado. Link de reset para {}: {}", destinatario, link);
            return;
        }

        try {
            SimpleMailMessage mensagem = new SimpleMailMessage();
            mensagem.setFrom(remetente);
            mensagem.setTo(destinatario);
            mensagem.setSubject(assunto);
            mensagem.setText(corpo);
            mailSender.send(mensagem);
            log.info("E-mail de reset de senha enviado para {}", destinatario);
        } catch (Exception e) {

            log.error("Falha ao enviar e-mail de reset para {}. Link (fallback): {}", destinatario, link, e);
        }
    }
}
