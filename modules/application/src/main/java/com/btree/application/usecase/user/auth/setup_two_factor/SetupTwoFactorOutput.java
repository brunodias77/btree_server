package com.btree.application.usecase.user.auth.setup_two_factor;

public record SetupTwoFactorOutput(
        String setupTokenId,
        String secret,
        String qrCodeUri
) {
}
