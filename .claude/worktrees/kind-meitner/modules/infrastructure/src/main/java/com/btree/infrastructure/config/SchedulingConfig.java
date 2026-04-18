package com.btree.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita a execução de agendamentos Assíncronos no projeto.
 * Essencial para o Spring reconhecer métodos marcados com {@code @Scheduled}, 
 * usados para despachar eventos do Outbox Pattern em background, limpeza de caches, 
 * ou rotinas de consolidação noturnas.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}