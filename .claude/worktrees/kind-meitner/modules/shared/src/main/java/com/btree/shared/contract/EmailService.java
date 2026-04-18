package com.btree.shared.contract;

/**
 * Contrato para envio de e-mails transacionais.
 *
 * <p>A implementação real pode usar SMTP, SendGrid, SES, etc.
 * Durante desenvolvimento, uma implementação fake apenas loga a mensagem.
 */
public interface EmailService {

    /**
     * Envia o e-mail de verificação de conta para o usuário recém-registrado.
     *
     * @param toEmail   endereço de destino
     * @param username  nome do usuário
     * @param rawToken  token em texto claro (não hasheado)
     */
    void sendEmailVerification(String toEmail, String username, String rawToken);

    /**
     * Envia o e-mail com o link de redefinição de senha.
     *
     * @param toEmail   endereço de destino
     * @param username  nome do usuário
     * @param rawToken  token em texto claro (não hasheado) para compor o link
     */
    void sendPasswordResetEmail(String toEmail, String username, String rawToken);
}
