package com.btree.shared.domain;

/**
 * Classe base para Value Objects.
 * <p>
 * Um Value Object é identificado por seus atributos (igualdade estrutural),
 * diferente de uma {@link Entity} que é identificada pelo seu {@code id}.
 * <p>
 * Subclasses devem implementar {@code equals} e {@code hashCode} baseados
 * nos campos que definem o valor. Recomenda-se o uso de {@code record} do Java
 * quando possível.
 */
public abstract class ValueObject {

    @Override
    public abstract boolean equals(Object o);

    @Override
    public abstract int hashCode();
}
