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
 * Implementação de {@link FileStorageService} usando MinIO como backend de armazenamento.
 *
 * <p>MinIO é um servidor de object storage compatível com a API S3 da AWS. Isso significa
 * que, em produção, esta implementação pode apontar tanto para uma instância MinIO
 * própria (self-hosted) quanto para o Amazon S3 real, bastando alterar as configurações
 * em {@link MinioConfig} sem mudar uma linha de código da aplicação.
 *
 * <h3>Responsabilidades</h3>
 * <ul>
 *   <li>Garantir que o bucket configurado exista e seja acessível publicamente na inicialização.</li>
 *   <li>Armazenar arquivos com nomes únicos (UUID + extensão) para evitar colisões.</li>
 *   <li>Retornar a URL pública do arquivo após o upload para uso pelo domínio.</li>
 * </ul>
 *
 * <h3>Configuração necessária</h3>
 * <p>As propriedades são centralizadas em {@link MinioConfig}:
 * <ul>
 *   <li>{@code minio.url} — URL do servidor MinIO (ex.: {@code http://localhost:9000}).</li>
 *   <li>{@code minio.access-key} / {@code minio.secret-key} — credenciais de acesso.</li>
 *   <li>{@code minio.bucket} — nome do bucket onde os arquivos serão armazenados.</li>
 *   <li>{@code minio.public-url} — URL base para construção dos links públicos dos arquivos.</li>
 * </ul>
 */
@Component
public class MinioFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioFileStorageService.class);

    /** Cliente MinIO configurado com URL, access key e secret key via {@link MinioConfig}. */
    private final MinioClient minioClient;

    /** Propriedades de configuração do MinIO (bucket, publicUrl, etc.). */
    private final MinioConfig minioConfig;

    public MinioFileStorageService(
            final MinioClient minioClient,
            final MinioConfig minioConfig
    ) {
        this.minioClient = minioClient;
        this.minioConfig = minioConfig;
    }

    /**
     * Inicializa o bucket MinIO na subida da aplicação.
     *
     * <p>Anotado com {@code @PostConstruct}: executa uma única vez após a injeção
     * de dependências, antes da aplicação começar a servir requisições. Garante que:
     * <ol>
     *   <li>O bucket configurado ({@code minio.bucket}) existe no servidor.</li>
     *   <li>Se não existir, é criado automaticamente com uma política de leitura pública
     *       (qualquer cliente pode fazer {@code GET} nos objetos, sem autenticação).</li>
     * </ol>
     *
     * <p>A política JSON segue o formato IAM da AWS (compatível com MinIO S3):
     * <pre>{@code
     * {
     *   "Version": "2012-10-17",
     *   "Statement": [{
     *     "Effect": "Allow",
     *     "Principal": {"AWS": ["*"]},
     *     "Action": ["s3:GetObject"],
     *     "Resource": ["arn:aws:s3:::nome-do-bucket/*"]
     *   }]
     * }
     * }</pre>
     *
     * <p>Erros na inicialização são logados como {@code ERROR} mas <b>não relançados</b>,
     * para não impedir a subida da aplicação em ambientes onde o MinIO pode estar
     * temporariamente indisponível (o erro surgirá no primeiro upload real).
     */
    @PostConstruct
    public void init() {
        try {
            final String bucket = minioConfig.getBucket();

            // Verifica se o bucket já existe para evitar erro de "bucket already owned by you".
            final boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build()
            );

            if (!exists) {
                // Cria o bucket com as configurações padrão do servidor MinIO.
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());

                // Aplica política de leitura pública: qualquer um pode fazer GET nos objetos.
                // Necessário para que as URLs retornadas por store() sejam acessíveis diretamente
                // pelo browser sem precisar de URLs pre-assinadas.
                final String policy = """
                        {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},"Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}
                        """.formatted(bucket).strip();

                minioClient.setBucketPolicy(
                        SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build()
                );
                log.info("MinIO bucket '{}' criado com política de leitura pública.", bucket);
            }
        } catch (Exception e) {
            // Loga o erro sem relançar: uma falha aqui não deve derrubar a aplicação inteira.
            log.error("Falha ao inicializar bucket MinIO: {}", e.getMessage(), e);
        }
    }

    /**
     * Armazena um arquivo no MinIO e retorna sua URL pública.
     *
     * <h3>Nomenclatura do objeto</h3>
     * <p>O arquivo é salvo com nome {@code <UUID>.<extensão>} (ex.: {@code a3f7c1...d9.jpg}).
     * O UUID garante unicidade sem depender do nome original do arquivo, evitando:
     * <ul>
     *   <li>Colisões de nomes entre uploads de arquivos diferentes.</li>
     *   <li>Exposição do nome original (que pode conter dados sensíveis do usuário).</li>
     *   <li>Problemas com caracteres especiais ou espaços em nomes de arquivos.</li>
     * </ul>
     *
     * <h3>URL retornada</h3>
     * <p>O formato é {@code {publicUrl}/{bucket}/{objectName}}, por exemplo:
     * {@code http://localhost:9000/btree-uploads/a3f7c1d9.jpg}. Esta URL é persistida
     * no domínio e enviada ao cliente para exibição direta (ex.: foto de perfil).
     *
     * @param originalFilename nome original do arquivo (usado apenas para extrair a extensão)
     * @param content          stream do conteúdo binário do arquivo
     * @param contentLength    tamanho total em bytes do conteúdo (necessário para o MinIO)
     * @param contentType      MIME type do arquivo (ex.: {@code "image/jpeg"}, {@code "application/pdf"})
     * @return URL pública e diretamente acessível do objeto recém-armazenado
     * @throws RuntimeException se o upload falhar no servidor MinIO
     */
    @Override
    public String store(
            final String originalFilename,
            final InputStream content,
            final long contentLength,
            final String contentType
    ) {
        // Gera nome único para o objeto: UUID + extensão original (ex.: "uuid.jpg").
        final String objectName = UUID.randomUUID() + extractExtension(originalFilename);

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioConfig.getBucket())  // destino: bucket configurado
                            .object(objectName)               // nome do objeto no bucket
                            .stream(content, contentLength, -1) // -1 = sem multipart (upload simples)
                            .contentType(contentType)         // Content-Type para servir o arquivo corretamente
                            .build()
            );

            // Monta e retorna a URL pública no formato: {publicUrl}/{bucket}/{objectName}
            return "%s/%s/%s".formatted(minioConfig.getPublicUrl(), minioConfig.getBucket(), objectName);

        } catch (Exception e) {
            throw new RuntimeException("Falha ao armazenar o arquivo no MinIO: " + e.getMessage(), e);
        }
    }

    /**
     * Extrai a extensão do nome de arquivo original, em letras minúsculas.
     *
     * <p>A extensão é preservada para que o objeto armazenado tenha o mesmo tipo
     * reconhecível pelo browser e por ferramentas de CDN (ex.: {@code .jpg}, {@code .pdf}).
     * Se o nome não contiver ponto ou for nulo, retorna string vazia — o objeto
     * será armazenado sem extensão, o que é aceitável.
     *
     * @param filename nome do arquivo original (pode ser null)
     * @return extensão com ponto em minúsculas (ex.: {@code ".jpg"}) ou {@code ""} se não houver
     */
    private String extractExtension(final String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        // Pega tudo após o último ponto e converte para minúsculas para uniformidade.
        return filename.substring(filename.lastIndexOf('.')).toLowerCase();
    }
}
