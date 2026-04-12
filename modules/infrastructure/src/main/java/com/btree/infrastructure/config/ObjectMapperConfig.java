package com.btree.infrastructure.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuração central do Jackson (serialização/desserialização JSON).
 *
 * <p>Ajustes aplicados:
 * <ul>
 *     <li>Tolerância a envio de propriedades desconhecidas em payloads JSON.</li>
 *     <li>Fomatação de Datas em formato Strings ISO 8601 legíveis, em vez de Timestamps em milissegundos.</li>
 *     <li>Módulo {@code JavaTimeModule} habilitado para suporte às novas APIs de DateTime do Java 8+.</li>
 * </ul>
 */
@Configuration
public class ObjectMapperConfig {

    @Bean
    @Primary
    public com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .addModule(new JavaTimeModule())
                .build();
    }
}