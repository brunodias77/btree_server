-- V013: Align shared.stock_movement_type enum values with the Java StockMovementType enum.
-- DB had:  PURCHASE, SALE, RESERVATION, RELEASE, ADJUSTMENT, RETURN, DAMAGE, TRANSFER
-- Java has: IN,       OUT,  RESERVE,     RELEASE, ADJUSTMENT, RETURN

ALTER TYPE shared.stock_movement_type RENAME VALUE 'PURCHASE'    TO 'IN';
ALTER TYPE shared.stock_movement_type RENAME VALUE 'SALE'        TO 'OUT';
ALTER TYPE shared.stock_movement_type RENAME VALUE 'RESERVATION' TO 'RESERVE';
