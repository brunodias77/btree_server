package com.btree.application.usecase.user.address.add_address;

import com.btree.domain.user.entity.Address;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.AddressGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

/**
 * Caso de uso UC-17 — AddAddress [CMD P0].
 *
 * <p>Cadastra um novo endereço de entrega para o usuário autenticado.
 *
 * <p>Regra de negócio — endereço padrão automático:
 * Se este for o primeiro endereço do usuário ({@code countActiveByUserId = 0}),
 * ele é automaticamente marcado como padrão ({@code isDefault = true}),
 * garantindo que o usuário sempre tenha um endereço padrão disponível
 * para o checkout sem necessidade de ação adicional.
 *
 * <p>Algoritmo:
 * <ol>
 *   <li>Valida presença e formato do {@code userId}.</li>
 *   <li>Verifica se é o primeiro endereço do usuário.</li>
 *   <li>Cria o aggregate {@link Address} via factory method.</li>
 *   <li>Valida invariantes via {@code AddressValidator}.</li>
 *   <li>Persiste dentro da transação.</li>
 *   <li>Retorna {@link AddAddressOutput} com o endereço criado.</li>
 * </ol>
 */
public class AddAddressUseCase implements UseCase<AddAddressCommand, AddAddressOutput> {

    private final AddressGateway addressGateway;
    private final TransactionManager transactionManager;

    public AddAddressUseCase(
            final AddressGateway addressGateway,
            final TransactionManager transactionManager
    ) {
        this.addressGateway = addressGateway;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, AddAddressOutput> execute(AddAddressCommand addAddressCommand) {

        final var notification = Notification.create();

        // Validar presença e formato do userId
        if (addAddressCommand.userId() == null || addAddressCommand.userId().isBlank()) {
            notification.append(UserError.USER_NOT_FOUND);
            return Left(notification);
        }

        final UserId userId;
        try {
            userId = UserId.from(UUID.fromString(addAddressCommand.userId()));
        } catch (IllegalArgumentException e) {
            notification.append(UserError.USER_NOT_FOUND);
            return Left(notification);
        }

        // Verificar se é o primeiro endereço (determina isDefault automático)
        final boolean isFirstAddress = this.addressGateway.countActiveByUserId(userId) == 0;

        // Criar aggregate com validação acumulada
        final var address = Address.create(
                userId,
                addAddressCommand.label(),
                addAddressCommand.recipientName(),
                addAddressCommand.street(),
                addAddressCommand.number(),
                addAddressCommand.complement(),
                addAddressCommand.neighborhood(),
                addAddressCommand.city(),
                addAddressCommand.state(),
                addAddressCommand.postalCode(),
                addAddressCommand.country(),
                addAddressCommand.isBillingAddress(),
                isFirstAddress,
                notification
        );

        if (notification.hasError()) {
            return Left(notification);
        }


        //  Persistir dentro da transação
        return Try(() -> transactionManager.execute(() -> {
            final var saved = addressGateway.save(address);
            return AddAddressOutput.from(saved);
        })).toEither().mapLeft(Notification::create);

    }
}
