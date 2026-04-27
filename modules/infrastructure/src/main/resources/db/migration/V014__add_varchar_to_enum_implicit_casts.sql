-- Allow Hibernate to bind enum parameters as character varying (the default JDBC type).
-- Without these casts, PostgreSQL raises "operator does not exist: <enum> = character varying"
-- when Hibernate sends string literals in WHERE clauses against custom enum columns.
CREATE CAST (character varying AS shared.cart_status)       WITH INOUT AS IMPLICIT;
CREATE CAST (character varying AS shared.shipping_method)   WITH INOUT AS IMPLICIT;
CREATE CAST (character varying AS shared.product_status)    WITH INOUT AS IMPLICIT;
CREATE CAST (character varying AS shared.stock_movement_type) WITH INOUT AS IMPLICIT;
