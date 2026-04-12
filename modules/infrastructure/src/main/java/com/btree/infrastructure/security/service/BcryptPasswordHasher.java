package com.btree.infrastructure.security.service;

import com.btree.shared.contract.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BcryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder encoder;

    public BcryptPasswordHasher(final PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public String hash(final String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(final String rawPassword, final String hashedPassword) {
        return encoder.matches(rawPassword, hashedPassword);
    }
}
