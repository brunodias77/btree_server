package com.btree.infrastructure.user.persistence;

import com.btree.infrastructure.config.EncryptionConfig;
import com.btree.infrastructure.persistence.EncryptedStringConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Unit Test: EncryptedStringConverter")
class EncryptedStringConverterTest {

    private EncryptedStringConverter converter;

    @BeforeEach
    void setUp() {
        // Configuramos a chave mestre (Mínimo 32 caracteres) e o Salt hexadecimal
        var config = new EncryptionConfig();
        config.setSecret("MinhaChaveSuperSecretaDe32Caracs");
        config.setSalt("deadbeefcafebabe");
        converter = new EncryptedStringConverter(config);
    }

    @Test
    @DisplayName("Deve encriptar um texto (O output nunca deve ser igual ao input)")
    void shouldEncryptText() {
        final var clearText = "O segredo vitalico do usuario 77";
        final var cipherText = converter.convertToDatabaseColumn(clearText);

        assertThat(cipherText).isNotNull().isNotEqualTo(clearText);
    }

    @Test
    @DisplayName("Deve desencriptar com sucesso garantindo o Two-way de volta para a string limpa")
    void shouldDecryptText() {
        final var clearText = "Btree_Admin_Super_Secret123!";
        final var cipherText = converter.convertToDatabaseColumn(clearText);
        final var decryptedText = converter.convertToEntityAttribute(cipherText);

        assertThat(decryptedText).isEqualTo(clearText);
    }

    @Test
    @DisplayName("A classe Converter nativa do Hibernate deve tolerar nulos")
    void shouldHandleNullsSafely() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
