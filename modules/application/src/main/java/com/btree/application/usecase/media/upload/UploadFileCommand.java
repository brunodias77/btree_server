package com.btree.application.usecase.media.upload;

import java.io.InputStream;

public record UploadFileCommand(
        String filename,
        InputStream content,
        long contentLength,
        String contentType
) {}
