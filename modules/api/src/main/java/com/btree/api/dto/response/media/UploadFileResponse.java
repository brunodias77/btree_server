package com.btree.api.dto.response.media;

import com.btree.application.usecase.media.upload.UploadFileOutput;

public record UploadFileResponse(String url) {

    public static UploadFileResponse from(final UploadFileOutput output) {
        return new UploadFileResponse(output.url());
    }
}
