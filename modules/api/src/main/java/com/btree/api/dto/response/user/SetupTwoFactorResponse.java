package com.btree.api.dto.response.user;

import com.btree.application.usecase.user.auth.setup_two_factor.SetupTwoFactorOutput;
import com.fasterxml.jackson.annotation.JsonProperty;

public record SetupTwoFactorResponse(
        @JsonProperty("setup_token_id") String setupTokenId,
        String secret,
        @JsonProperty("qr_code_uri") String qrCodeUri
) {
    public static SetupTwoFactorResponse from(final SetupTwoFactorOutput output) {
        return new SetupTwoFactorResponse(output.setupTokenId(), output.secret(), output.qrCodeUri());
    }
}
