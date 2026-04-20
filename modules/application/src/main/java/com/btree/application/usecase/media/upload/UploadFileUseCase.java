package com.btree.application.usecase.media.upload;

import com.btree.shared.contract.FileStorageService;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Error;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.Set;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

public class UploadFileUseCase implements UseCase<UploadFileCommand, UploadFileOutput> {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "image/svg+xml"
    );

    private static final Error UNSUPPORTED_TYPE =
            new Error("Tipo de arquivo não suportado. Use JPEG, PNG, WebP, GIF ou SVG.");

    private static final Error FILE_EMPTY =
            new Error("O arquivo enviado está vazio.");

    private final FileStorageService fileStorageService;

    public UploadFileUseCase(final FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @Override
    public Either<Notification, UploadFileOutput> execute(final UploadFileCommand command) {
        final var notification = Notification.create();

        if (command.contentLength() <= 0) {
            notification.append(FILE_EMPTY);
            return Left(notification);
        }

        if (command.contentType() == null || !ALLOWED_TYPES.contains(command.contentType())) {
            notification.append(UNSUPPORTED_TYPE);
            return Left(notification);
        }

        return Try(() -> {
            final var url = this.fileStorageService.store(
                    command.filename(),
                    command.content(),
                    command.contentLength(),
                    command.contentType()
            );
            return new UploadFileOutput(url);
        }).toEither().mapLeft(Notification::create);
    }
}
