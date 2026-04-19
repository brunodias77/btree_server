package com.btree.shared.usecase;

import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

/**
 * Contrato base para casos de uso de escrita da aplicação.
 *
 * <p>Retorna {@link Either}: {@code Left(Notification)} em caso de erros de
 * validação ou regra de negócio; {@code Right(OUT)} em caso de sucesso.
 *
 * @param <IN>  Tipo do Command/Input (imutável, geralmente um {@code record})
 * @param <OUT> Tipo do Output (imutável, geralmente um {@code record})
 */
public interface UseCase<IN, OUT> {
    Either<Notification, OUT> execute(IN in);
}
