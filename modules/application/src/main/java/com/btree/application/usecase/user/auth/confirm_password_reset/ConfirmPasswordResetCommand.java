package com.btree.application.usecase.user.auth.confirm_password_reset;

public record ConfirmPasswordResetCommand(String token, String newPassword) {}
