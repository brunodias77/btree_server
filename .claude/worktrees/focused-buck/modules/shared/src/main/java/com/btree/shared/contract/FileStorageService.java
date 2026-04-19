package com.btree.shared.contract;

import java.io.InputStream;

/**
 * Contrato para armazenamento de arquivos binários.
 *
 * <p>A implementação real pode usar MinIO, S3, GCS, etc.
 * Recebe o stream do arquivo e retorna a URL pública de acesso.
 */
public interface FileStorageService {

    /**
     * Armazena o arquivo e retorna sua URL pública.
     *
     * @param originalFilename nome original do arquivo (usado para extrair extensão)
     * @param content          stream do conteúdo do arquivo
     * @param contentLength    tamanho em bytes do conteúdo
     * @param contentType      MIME type do arquivo (ex: {@code image/jpeg})
     * @return URL pública para acesso ao arquivo armazenado
     */
    String store(String originalFilename, InputStream content, long contentLength, String contentType);
}
