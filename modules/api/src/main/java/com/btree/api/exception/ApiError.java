package com.btree.api.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Corpo padronizado de resposta para erros da API.
 *
 * <p>O campo {@code errors} só é serializado quando há múltiplas mensagens
 * (ex: validação de campos). Para erros únicos, apenas {@code message} é preenchido.
 *
 * <p>Os factory methods aceitam {@link Instant} explicitamente para facilitar
 * testes determinísticos — o caller controla o timestamp (geralmente {@code Instant.now()}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        int status,
        String error,
        String message,
        List<String> errors,
        Instant timestamp,
        String path
) {

    /** Erro simples com uma única mensagem. */
    public static ApiError of(
            final int status, final String error,
            final String message, final String path,
            final Instant timestamp) {
        return new ApiError(status, error, message, null, timestamp, path);
    }

    /** Erro com lista de mensagens (ex: falhas de validação em múltiplos campos). */
    public static ApiError of(
            final int status, final String error,
            final List<String> errors, final String path,
            final Instant timestamp) {
        final String primary = errors.isEmpty() ? error : errors.get(0);
        return new ApiError(status, error, primary, errors, timestamp, path);
    }
}
