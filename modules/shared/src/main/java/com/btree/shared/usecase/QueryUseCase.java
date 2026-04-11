package com.btree.shared.usecase;

/**
 * Caso de uso somente-leitura (query).
 * <p>
 * Semanticamente idêntico a {@link UseCase}, mas sinaliza que a operação
 * é uma consulta que NÃO modifica estado. Permite otimizações como
 * transações read-only na infraestrutura.
 *
 * @param <IN>  Tipo do objeto de entrada (critérios da consulta)
 * @param <OUT> Tipo do objeto de saída (resultado da consulta)
 *
 * @see UseCase
 */
public interface QueryUseCase<IN, OUT> extends UseCase<IN, OUT> {
}
