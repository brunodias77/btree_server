package com.btree.application.usecase.user.update_profile;

import java.time.LocalDate;

public record UpdateProfileCommand(
        String userId,
        String firtName,
        String lastName,
        String cpf,
        LocalDate birthDate,
        String gender,
        String preferredLanguage,
        String preferredCurrency,
        boolean newsletterSubscribed
) {
}
