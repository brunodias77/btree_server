package com.btree.infrastructure.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração do cliente MinIO (object storage S3-compatível).
 *
 * <p>Propriedades lidas de {@code minio.*} no {@code application.yaml}.
 * Em desenvolvimento usa as credenciais padrão do container Docker.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {

    private String url;
    private String accessKey;
    private String secretKey;
    private String bucket;
    private String publicUrl;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();
    }
}
