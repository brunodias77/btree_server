package com.btree.infrastructure.security.service;

import com.btree.infrastructure.config.EncryptionConfig;
import com.btree.shared.contract.StringEncryptor;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

@Component
public class AesStringEncryptor implements StringEncryptor {

    private final TextEncryptor encryptor;

    public AesStringEncryptor(final EncryptionConfig config) {
        this.encryptor = Encryptors.text(config.getSecret(), config.getSalt());
    }

    @Override
    public String encrypt(final String value) {
        return encryptor.encrypt(value);
    }

    @Override
    public String decrypt(final String value) {
        return encryptor.decrypt(value);
    }
}
