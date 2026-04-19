package com.btree.domain.coupon.error;

import com.btree.shared.validation.Error;

public class CouponError {

    public static final Error COUPON_NOT_FOUND           = new Error("Cupom não encontrado");
    public static final Error COUPON_DELETED             = new Error("Cupom excluído não pode ser editado");
    public static final Error COUPON_NOT_EDITABLE        = new Error("Cupom no status '%s' não pode ser editado");
    public static final Error DISCOUNT_VALUE_INVALIDO    = new Error("'discount_value' deve ser maior que zero");
    public static final Error STARTS_AT_NULL             = new Error("'starts_at' não pode ser nulo");
    public static final Error EXPIRES_AT_INVALIDO        = new Error("'expires_at' deve ser posterior a 'starts_at'");
    public static final Error MAX_USES_BELOW_CURRENT     = new Error("'max_uses' não pode ser menor que o total de usos já registrados (%d)");
    public static final Error MAX_USES_PER_USER_INVALIDO = new Error("'max_uses_per_user' deve ser maior ou igual a 1");

    private CouponError() {}
}
