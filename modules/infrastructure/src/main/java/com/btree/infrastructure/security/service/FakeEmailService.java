package com.btree.infrastructure.security.service;

import com.btree.shared.contract.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementação fake de {@link EmailService} para desenvolvimento/testes.
 *
 * <p>Em vez de enviar um e-mail real, apenas loga a mensagem no console.
 * Substituir por uma implementação real (SMTP, SendGrid, SES, etc.) em produção.
 */
@Component
public class FakeEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(FakeEmailService.class);

    @Override
    public void sendEmailVerification(
            final String toEmail,
            final String username,
            final String rawToken
    ) {
        log.info("""

                ╔══════════════════════════════════════════════════════════╗
                ║              [FAKE EMAIL] Verificação de E-mail          ║
                ╠══════════════════════════════════════════════════════════╣
                ║  Para:    {}
                ║  Assunto: Confirme seu e-mail no BTree
                ║
                ║  Olá, {}!
                ║
                ║  Use o token abaixo para verificar seu e-mail:
                ║
                ║  TOKEN: {}
                ║
                ║  (Válido por 24 horas)
                ╚══════════════════════════════════════════════════════════╝
                """,
                toEmail, username, rawToken);
    }

    @Override
    public void sendPasswordResetEmail(
            final String toEmail,
            final String username,
            final String rawToken
    ) {
        log.info("""

                ╔══════════════════════════════════════════════════════════╗
                ║           [FAKE EMAIL] Redefinição de Senha               ║
                ╠══════════════════════════════════════════════════════════╣
                ║  Para:    {}
                ║  Assunto: Redefinição de senha no BTree
                ║
                ║  Olá, {}!
                ║
                ║  Use o token abaixo para redefinir sua senha:
                ║
                ║  TOKEN: {}
                ║
                ║  (Válido por 30 minutos)
                ╚══════════════════════════════════════════════════════════╝
                """,
                toEmail, username, rawToken);
    }
}
