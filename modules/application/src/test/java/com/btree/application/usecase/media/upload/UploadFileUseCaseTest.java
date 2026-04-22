package com.btree.application.usecase.media.upload;

import com.btree.application.usecase.UseCaseTest;
import com.btree.shared.contract.FileStorageService;
import com.btree.shared.validation.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UploadFile use case")
class UploadFileUseCaseTest extends UseCaseTest {

    private static final String FILE_EMPTY_MESSAGE      = "O arquivo enviado está vazio.";
    private static final String UNSUPPORTED_TYPE_MESSAGE =
            "Tipo de arquivo não suportado. Use JPEG, PNG, WebP, GIF ou SVG.";

    @Mock FileStorageService fileStorageService;

    UploadFileUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UploadFileUseCase(fileStorageService);
    }

    // ── caminho feliz ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Caminho feliz")
    class HappyCases {

        @Test
        @DisplayName("Deve retornar URL quando upload JPEG for bem-sucedido")
        void givenValidJpegFile_whenExecute_thenReturnUrl() throws Exception {
            when(fileStorageService.store(any(), any(), anyLong(), any()))
                    .thenReturn("https://cdn.example.com/image.jpg");

            final var cmd = new UploadFileCommand("image.jpg", inputStream(), 1024L, "image/jpeg");
            final var result = useCase.execute(cmd);

            assertTrue(result.isRight());
            assertEquals("https://cdn.example.com/image.jpg", result.get().url());
        }

        @ParameterizedTest(name = "contentType={0}")
        @ValueSource(strings = {"image/jpeg", "image/png", "image/webp", "image/gif", "image/svg+xml"})
        @DisplayName("Deve aceitar todos os tipos de imagem suportados")
        void givenSupportedContentType_whenExecute_thenReturnRight(final String contentType) throws Exception {
            when(fileStorageService.store(any(), any(), anyLong(), any()))
                    .thenReturn("https://cdn.example.com/file");

            final var cmd = new UploadFileCommand("file", inputStream(), 512L, contentType);
            final var result = useCase.execute(cmd);

            assertTrue(result.isRight());
        }

        @Test
        @DisplayName("Deve delegar ao fileStorageService.store com os parâmetros corretos")
        void givenValidCommand_whenExecute_thenDelegateToStorageService() throws Exception {
            final var stream = inputStream();
            when(fileStorageService.store(eq("photo.png"), same(stream), eq(2048L), eq("image/png")))
                    .thenReturn("https://cdn.example.com/photo.png");

            final var cmd = new UploadFileCommand("photo.png", stream, 2048L, "image/png");
            useCase.execute(cmd);

            verify(fileStorageService, times(1)).store("photo.png", stream, 2048L, "image/png");
        }
    }

    // ── validações de entrada ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Validações de entrada")
    class InputValidations {

        @Test
        @DisplayName("Deve retornar erro FILE_EMPTY quando contentLength for zero")
        void givenContentLengthZero_whenExecute_thenReturnFileEmptyError() {
            final var cmd = new UploadFileCommand("image.jpg", inputStream(), 0L, "image/jpeg");
            final var result = useCase.execute(cmd);

            assertTrue(result.isLeft());
            assertError(result.getLeft(), FILE_EMPTY_MESSAGE);
            verifyNoInteractions(fileStorageService);
        }

        @Test
        @DisplayName("Deve retornar erro FILE_EMPTY quando contentLength for negativo")
        void givenNegativeContentLength_whenExecute_thenReturnFileEmptyError() {
            final var cmd = new UploadFileCommand("image.jpg", inputStream(), -1L, "image/jpeg");
            final var result = useCase.execute(cmd);

            assertTrue(result.isLeft());
            assertError(result.getLeft(), FILE_EMPTY_MESSAGE);
            verifyNoInteractions(fileStorageService);
        }

        @Test
        @DisplayName("Deve retornar erro UNSUPPORTED_TYPE quando contentType for nulo")
        void givenNullContentType_whenExecute_thenReturnUnsupportedTypeError() {
            final var cmd = new UploadFileCommand("image.jpg", inputStream(), 1024L, null);
            final var result = useCase.execute(cmd);

            assertTrue(result.isLeft());
            assertError(result.getLeft(), UNSUPPORTED_TYPE_MESSAGE);
            verifyNoInteractions(fileStorageService);
        }

        @ParameterizedTest(name = "contentType={0}")
        @ValueSource(strings = {"application/pdf", "text/plain", "video/mp4", "image/bmp", "image/tiff"})
        @DisplayName("Deve retornar erro UNSUPPORTED_TYPE para tipos não suportados")
        void givenUnsupportedContentType_whenExecute_thenReturnUnsupportedTypeError(final String contentType) {
            final var cmd = new UploadFileCommand("file", inputStream(), 1024L, contentType);
            final var result = useCase.execute(cmd);

            assertTrue(result.isLeft());
            assertError(result.getLeft(), UNSUPPORTED_TYPE_MESSAGE);
            verifyNoInteractions(fileStorageService);
        }

        @Test
        @DisplayName("Deve retornar exatamente um erro quando contentLength for zero")
        void givenContentLengthZero_whenExecute_thenReturnSingleError() {
            final var cmd = new UploadFileCommand("image.jpg", inputStream(), 0L, "image/jpeg");
            final var result = useCase.execute(cmd);

            assertTrue(result.isLeft());
            assertEquals(1, result.getLeft().getErrors().size());
        }
    }

    // ── falha no armazenamento ────────────────────────────────────────────────

    @Nested
    @DisplayName("Falha no armazenamento")
    class StorageFailure {

        @Test
        @DisplayName("Deve retornar Left quando fileStorageService lançar exceção")
        void givenStorageServiceThrows_whenExecute_thenReturnLeftNotification() throws Exception {
            when(fileStorageService.store(any(), any(), anyLong(), any()))
                    .thenThrow(new RuntimeException("storage unavailable"));

            final var cmd = new UploadFileCommand("image.jpg", inputStream(), 1024L, "image/jpeg");
            final var result = useCase.execute(cmd);

            assertTrue(result.isLeft());
            assertFalse(result.getLeft().getErrors().isEmpty());
        }

        @Test
        @DisplayName("Deve retornar Left com mensagem da exceção quando storage falhar")
        void givenStorageServiceThrows_whenExecute_thenNotificationContainsExceptionMessage() throws Exception {
            when(fileStorageService.store(any(), any(), anyLong(), any()))
                    .thenThrow(new RuntimeException("connection timeout"));

            final var cmd = new UploadFileCommand("image.jpg", inputStream(), 1024L, "image/jpeg");
            final var result = useCase.execute(cmd);

            assertTrue(result.isLeft());
            assertTrue(result.getLeft().getErrors().stream()
                    .anyMatch(e -> e.message().contains("connection timeout")));
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private InputStream inputStream() {
        return new ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5});
    }

    private void assertError(final Notification notification, final String message) {
        assertTrue(
                notification.getErrors().stream().anyMatch(e -> e.message().equals(message)),
                "Esperado erro: \"" + message + "\". Encontrado: " + errors(notification)
        );
    }
}
