package com.btree.shared.usecase;

import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

/**
 * Contrato para casos de uso sem valor de retorno significativo (side-effect only).
 *
 * <p>Retorna {@link Either}: {@code Left(Notification)} em caso de erros;
 * {@code Right(null)} em caso de sucesso.
 *
 * @param <IN> Tipo do Command/Input
 */
public interface UnitUseCase<IN> {
    Either<Notification, Void> execute(IN in);
}
