package com.btree.api.controller.media;

import com.btree.api.dto.response.media.UploadFileResponse;
import com.btree.application.usecase.media.upload.UploadFileCommand;
import com.btree.application.usecase.media.upload.UploadFileUseCase;
import com.btree.shared.domain.DomainException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/v1/media")
@Tag(name = "Media", description = "Upload de arquivos de mídia")
public class MediaController {

    private final UploadFileUseCase uploadFileUseCase;

    public MediaController(final UploadFileUseCase uploadFileUseCase) {
        this.uploadFileUseCase = uploadFileUseCase;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Upload de imagem",
            description = "Armazena uma imagem no object storage e retorna a URL pública. " +
                    "Formatos aceitos: JPEG, PNG, WebP, GIF, SVG. Tamanho máximo: 10 MB."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Upload realizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Arquivo vazio ou tipo não suportado"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido")
    })
    public UploadFileResponse upload(@RequestParam("file") final MultipartFile file) {
        final UploadFileCommand command;
        try {
            command = new UploadFileCommand(
                    file.getOriginalFilename(),
                    file.getInputStream(),
                    file.getSize(),
                    file.getContentType()
            );
        } catch (IOException e) {
            throw new RuntimeException("Falha ao ler o arquivo enviado.", e);
        }

        return UploadFileResponse.from(
                uploadFileUseCase.execute(command)
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }
}
