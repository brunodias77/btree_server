package com.btree.infrastructure.persistence;



import com.btree.infrastructure.config.EncryptionConfig;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * JPA {@link AttributeConverter} que criptografa/descriptografa strings sensíveis
 * em repouso usando AES-256 GCM via Spring Security {@link Encryptors#text}.
 *
 * <p>Aplique via {@code @Convert(converter = EncryptedStringConverter.class)}
 * nos campos da JPA Entity que precisam de criptografia (ex: {@code two_factor_secret}).
 *
 * <p>O {@code salt} deve ser hex-encoded (16 chars = 8 bytes).
 * A chave AES-256 é derivada de {@code secret} + {@code salt} via PBKDF2.
 *
 * <p><b>Atenção:</b> alterar {@code secret} ou {@code salt} invalida todos os
 * dados já criptografados no banco. Rotacionar chaves requer migração de dados.
 */
@Converter(autoApply = false)
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final TextEncryptor encryptor;

    public EncryptedStringConverter(final EncryptionConfig config) {
        this.encryptor = Encryptors.text(config.getSecret(), config.getSalt());
    }

    @Override
    public String convertToDatabaseColumn(final String attribute) {
        if (attribute == null) {
            return null;
        }
        return encryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(final String dbData) {
        if (dbData == null) {
            return null;
        }
        return encryptor.decrypt(dbData);
    }
}

