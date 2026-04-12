package com.btree.infrastructure.storage;

import com.btree.infrastructure.config.MinioConfig;
import com.btree.shared.contract.FileStorageService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.UUID;

/**
 * Implementação de {@link FileStorageService} usando MinIO (S3-compatível).
 *
 * <p>Na inicialização, garante que o bucket configurado existe e possui
 * política de leitura pública. Os objetos são nomeados com UUID para
 * evitar colisões.
 */
@Component
public class MinioFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioFileStorageService.class);

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    public MinioFileStorageService(
            final MinioClient minioClient,
            final MinioConfig minioConfig
    ) {
        this.minioClient = minioClient;
        this.minioConfig = minioConfig;
    }

    @PostConstruct
    public void init() {
        try {
            final String bucket = minioConfig.getBucket();
            final boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build()
            );
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                final String policy = """
                        {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},"Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}
                        """.formatted(bucket).strip();
                minioClient.setBucketPolicy(
                        SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build()
                );
                log.info("MinIO bucket '{}' criado com política de leitura pública.", bucket);
            }
        } catch (Exception e) {
            log.error("Falha ao inicializar bucket MinIO: {}", e.getMessage(), e);
        }
    }

    @Override
    public String store(
            final String originalFilename,
            final InputStream content,
            final long contentLength,
            final String contentType
    ) {
        final String objectName = UUID.randomUUID() + extractExtension(originalFilename);
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioConfig.getBucket())
                            .object(objectName)
                            .stream(content, contentLength, -1)
                            .contentType(contentType)
                            .build()
            );
            return "%s/%s/%s".formatted(minioConfig.getPublicUrl(), minioConfig.getBucket(), objectName);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao armazenar o arquivo no MinIO: " + e.getMessage(), e);
        }
    }

    private String extractExtension(final String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.')).toLowerCase();
    }
}
