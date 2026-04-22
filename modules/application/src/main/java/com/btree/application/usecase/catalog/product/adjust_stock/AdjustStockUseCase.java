package com.btree.application.usecase.catalog.product.adjust_stock;

import com.btree.domain.catalog.entity.StockMovement;
import com.btree.domain.catalog.error.ProductError;
import com.btree.domain.catalog.error.StockMovementError;
import com.btree.domain.catalog.events.StockAdjustedEvent;
import com.btree.domain.catalog.gateway.ProductGateway;
import com.btree.domain.catalog.gateway.StockMovementGateway;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.event.DomainEventPublisher;
import com.btree.shared.enums.StockMovementType;
import com.btree.shared.exception.NotFoundException;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.List;
import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

/**
 * UC-70 — AdjustStock [CMD P0].
 *
 * <p>Registra uma entrada ou saída manual de estoque para um produto:
 * <ul>
 *   <li>{@code delta > 0} — entrada (ex.: recebimento, correção positiva);</li>
 *   <li>{@code delta < 0} — saída (ex.: baixa por dano, correção negativa).</li>
 * </ul>
 *
 * <p>A operação é atômica: atualiza {@code catalog.products.quantity} e grava em
 * {@code catalog.stock_movements} na mesma transação. Transições automáticas de status
 * ({@code ACTIVE ↔ OUT_OF_STOCK}) são disparadas pelos métodos do aggregate.
 */
public class AdjustStockUseCase implements UseCase<AdjustStockCommand, AdjustStockOutput> {

    private final ProductGateway       productGateway;
    private final StockMovementGateway stockMovementGateway;
    private final DomainEventPublisher eventPublisher;
    private final TransactionManager   transactionManager;

    public AdjustStockUseCase(
            final ProductGateway productGateway,
            final StockMovementGateway stockMovementGateway,
            final DomainEventPublisher eventPublisher,
            final TransactionManager transactionManager
    ) {
        this.productGateway       = productGateway;
        this.stockMovementGateway = stockMovementGateway;
        this.eventPublisher       = eventPublisher;
        this.transactionManager   = transactionManager;
    }

    @Override
    public Either<Notification, AdjustStockOutput> execute(final AdjustStockCommand command) {

        // 1. Carregar produto — NotFoundException propaga como 404
        final var product = this.productGateway.findById(ProductId.from(command.productId()))
                .orElseThrow(() -> NotFoundException.with(ProductError.PRODUCT_NOT_FOUND.message()));

        // 2. Acumular erros de negócio antes de entrar na transação
        final var notification = Notification.create();

        if (product.isDeleted()) {
            notification.append(ProductError.CANNOT_MODIFY_DELETED_PRODUCT);
        }

        if (command.delta() == 0) {
            notification.append(StockMovementError.QUANTITY_ZERO);
        }

        final var movementType = parseMovementType(command.movementType(), notification);

        if (!notification.hasError()
                && command.delta() < 0
                && product.getQuantity() < Math.abs(command.delta())) {
            notification.append(StockMovementError.INSUFFICIENT_STOCK);
        }

        if (notification.hasError()) {
            return Left(notification);
        }

        // 3. Parsear referenceId opcional
        final UUID referenceId;
        try {
            referenceId = command.referenceId() != null && !command.referenceId().isBlank()
                    ? UUID.fromString(command.referenceId())
                    : null;
        } catch (final IllegalArgumentException e) {
            notification.append(new com.btree.shared.validation.Error("'referenceId' não é um UUID válido"));
            return Left(notification);
        }

        // 4. Persistir atomicamente
        return Try(() -> this.transactionManager.execute(() -> {

            // 4a. Mutação no aggregate (dispara transição de status se aplicável)
            if (command.delta() > 0) {
                product.addStock(command.delta());
            } else {
                product.deductStock(Math.abs(command.delta()));
            }

            // 4b. Persiste produto com novo quantity, status e version
            final var updatedProduct = this.productGateway.update(product);

            // 4c. Cria e persiste o registro imutável de movimentação (ledger)
            final var movement = StockMovement.create(
                    updatedProduct.getId(),
                    movementType,
                    command.delta(),
                    referenceId,
                    command.referenceType(),
                    command.notes()
            );
            final var savedMovement = this.stockMovementGateway.save(movement);

            // 4d. Publica eventos do aggregate (ex.: ProductOutOfStockEvent)
            this.eventPublisher.publishAll(updatedProduct.getDomainEvents());

            // 4e. Publica evento de ajuste manual
            this.eventPublisher.publish(new StockAdjustedEvent(
                    updatedProduct.getId().getValue().toString(),
                    command.delta(),
                    updatedProduct.getQuantity(),
                    movementType.name()
            ));

            return AdjustStockOutput.from(updatedProduct, savedMovement);

        })).toEither().mapLeft(Notification::create);
    }

    /**
     * Converte o nome textual para {@link StockMovementType}, acumulando erro se inválido.
     */
    private StockMovementType parseMovementType(
            final String raw,
            final Notification notification
    ) {
        if (raw == null || raw.isBlank()) {
            notification.append(StockMovementError.MOVEMENT_TYPE_NULL);
            return null;
        }
        try {
            return StockMovementType.valueOf(raw.toUpperCase());
        } catch (final IllegalArgumentException e) {
            notification.append(StockMovementError.MOVEMENT_TYPE_NULL);
            return null;
        }
    }
}

