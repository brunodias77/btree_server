package com.btree.domain.user.gateway;

public interface TotpGateway {
    String generateSecret();
    String getUriForImage(String secret, String accountName, String issuer);
    boolean isValidCode(String secret, String code);
}
