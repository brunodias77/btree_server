package com.btree.api.exception;

import com.btree.shared.domain.DomainException;
import com.btree.shared.exception.BusinessRuleException;
import com.btree.shared.exception.ConflictException;
import com.btree.shared.exception.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

/**
 * Tratamento centralizado de exceções para toda a API REST.
 *
 * <p>Mapeia a hierarquia de exceções do domínio para status HTTP semânticos,
 * garantindo respostas consistentes sem vazar detalhes internos.
 *
 * <p>Hierarquia de captura (mais específica → mais genérica):
 * <ol>
 *   <li>{@link NotFoundException}                       → 404</li>
 *   <li>{@link ConflictException}                       → 409</li>
 *   <li>{@link ObjectOptimisticLockingFailureException} → 409</li>
 *   <li>{@link BusinessRuleException}                   → 422</li>
 *   <li>{@link DomainException}                         → 422</li>
 *   <li>{@link MethodArgumentNotValidException}         → 400</li>
 *   <li>{@link HttpMessageNotReadableException}         → 400</li>
 *   <li>{@link NoResourceFoundException}                → 404</li>
 *   <li>{@link Exception}                               → 500</li>
 * </ol>
 *
 * <p><b>Nota sobre segurança:</b> {@code AuthenticationException} e
 * {@code AccessDeniedException} são tratadas pelo Spring Security antes de
 * chegarem ao {@code DispatcherServlet}. Os handlers correspondentes ficam em
 * {@link com.btree.api.config.SecurityExceptionConfig}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── Domain exceptions ────────────────────────────────────────────────────

    /**
     * Recurso não encontrado no banco de dados → HTTP 404.
     *
     * <p>Captura {@link NotFoundException} lançada por qualquer Use Case quando
     * um {@code findById} ou equivalente não encontra a entidade solicitada.
     * Log em {@code DEBUG} pois é uma situação esperada (ex.: URL com ID inválido).
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            final NotFoundException ex, final HttpServletRequest request) {
        log.debug("Resource not found [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "Not Found", ex.getMessage(), request.getRequestURI(), Instant.now()));
    }

    /**
     * Conflito de unicidade ou estado → HTTP 409.
     *
     * <p>Captura {@link ConflictException} lançada quando uma operação viola
     * uma constraint de unicidade do domínio (ex.: e-mail já cadastrado,
     * slug duplicado). Log em {@code DEBUG} — conflito é responsabilidade do cliente.
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(
            final ConflictException ex, final HttpServletRequest request) {
        log.debug("Conflict [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "Conflict", ex.getMessage(), request.getRequestURI(), Instant.now()));
    }

    /**
     * Conflito de versionamento otimista (stale update) → HTTP 409.
     *
     * <p>Captura {@link ObjectOptimisticLockingFailureException} do Hibernate quando
     * duas transações concorrentes tentam atualizar a mesma entidade versionada
     * (ex.: Product, Cart, Coupon com {@code @Version}). A mensagem genérica evita
     * vazar detalhes de implementação (nome da entidade JPA, versão esperada vs atual).
     * Log em {@code WARN} pois pode indicar contenção excessiva em produção.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(
            final ObjectOptimisticLockingFailureException ex, final HttpServletRequest request) {
        log.warn("Optimistic locking conflict [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "Conflict",
                        "O recurso foi modificado por outra requisição. Tente novamente.",
                        request.getRequestURI(), Instant.now()));
    }

    /**
     * Violação de regra de negócio → HTTP 422.
     *
     * <p>Captura {@link BusinessRuleException} que encapsula uma {@link com.btree.shared.validation.Notification}
     * com um ou mais erros de validação de domínio. Os erros são extraídos e retornados
     * como lista de mensagens no campo {@code messages} do {@link ApiError}.
     * Usado quando o input é sintaticamente válido mas semanticamente inaceitável
     * (ex.: quantidade negativa, coupon expirado, estoque insuficiente).
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(
            final BusinessRuleException ex, final HttpServletRequest request) {
        log.debug("Business rule violation [{}]: {}", request.getRequestURI(), ex.getMessage());
        final List<String> messages = ex.getErrors().stream()
                .map(com.btree.shared.validation.Error::message)
                .toList();
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiError.of(422, "Unprocessable Entity", messages, request.getRequestURI(), Instant.now()));
    }

    /**
     * Exceção genérica de domínio → HTTP 422.
     *
     * <p>Catch-all para {@link DomainException} que não foi capturada por handlers
     * mais específicos ({@link BusinessRuleException}, etc.). Funciona como rede de
     * segurança para qualquer validação de domínio que escape da hierarquia tipada.
     * Log em {@code WARN} pois indica um caminho de validação possivelmente não coberto
     * por uma subclasse especializada.
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> handleDomain(
            final DomainException ex, final HttpServletRequest request) {
        log.warn("Domain exception [{}]: {}", request.getRequestURI(), ex.getMessage());
        final List<String> messages = ex.getErrors().stream()
                .map(com.btree.shared.validation.Error::message)
                .toList();
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiError.of(422, "Unprocessable Entity", messages, request.getRequestURI(), Instant.now()));
    }

    // ── Spring MVC / Bean Validation exceptions ──────────────────────────────

    /**
     * Falha de validação do Bean Validation ({@code @Valid}) → HTTP 400.
     *
     * <p>Disparada automaticamente pelo Spring MVC quando um DTO anotado
     * com {@code @Valid} no controller falha nas constraints
     * ({@code @NotBlank}, {@code @Size}, {@code @Email}, etc.).
     * Erros de campo e erros globais (class-level) são unificados em uma
     * lista única de mensagens legíveis.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBeanValidation(
            final MethodArgumentNotValidException ex, final HttpServletRequest request) {
        final List<String> messages = Stream.concat(
                ex.getBindingResult().getFieldErrors().stream()
                        .map(fe -> "%s: %s".formatted(fe.getField(), fe.getDefaultMessage())),
                ex.getBindingResult().getGlobalErrors().stream()
                        .map(ge -> ge.getDefaultMessage())
        ).toList();
        log.debug("Validation failed [{}]: {}", request.getRequestURI(), messages);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, "Bad Request", messages, request.getRequestURI(), Instant.now()));
    }

    /**
     * Corpo da requisição ilegível ou ausente → HTTP 400.
     *
     * <p>Disparada pelo Jackson quando o JSON é sintaticamente inválido
     * (ex.: chave sem aspas, vírgula faltando, Content-Type incorreto)
     * ou quando o body é obrigatório mas não foi enviado. A mensagem
     * interna do Jackson é ocultada para não vazar detalhes de implementação.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(
            final HttpMessageNotReadableException ex, final HttpServletRequest request) {
        log.debug("Malformed request body [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, "Bad Request",
                        "Corpo da requisição inválido ou malformado.",
                        request.getRequestURI(), Instant.now()));
    }

    /**
     * Query parameter obrigatório não fornecido → HTTP 400.
     *
     * <p>Disparada pelo Spring MVC quando um parâmetro anotado com
     * {@code @RequestParam(required = true)} está ausente na URL.
     * Informa ao cliente qual parâmetro está faltando.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(
            final MissingServletRequestParameterException ex, final HttpServletRequest request) {
        final String message = "Parâmetro obrigatório ausente: '%s'".formatted(ex.getParameterName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, "Bad Request", message, request.getRequestURI(), Instant.now()));
    }

    /**
     * Tipo incompatível em path variable ou query param → HTTP 400.
     *
     * <p>Disparada quando o Spring não consegue converter um parâmetro da URL
     * para o tipo esperado (ex.: {@code /products/abc} onde o controller espera
     * um {@code UUID}, ou {@code ?page=xyz} onde espera {@code int}).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            final MethodArgumentTypeMismatchException ex, final HttpServletRequest request) {
        final String value = ex.getValue() != null ? ex.getValue().toString() : "null";
        final String message = "Valor inválido para o parâmetro '%s': '%s'"
                .formatted(ex.getName(), value);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, "Bad Request", message, request.getRequestURI(), Instant.now()));
    }

    /**
     * Upload excede o tamanho máximo configurado → HTTP 400.
     *
     * <p>Disparada pelo Spring quando um multipart file excede o limite
     * definido em {@code spring.servlet.multipart.max-file-size}.
     * A mensagem informa o limite de forma legível para o cliente.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSize(
            final MaxUploadSizeExceededException ex, final HttpServletRequest request) {
        log.debug("Upload size exceeded [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, "Bad Request",
                        "O arquivo enviado excede o tamanho máximo permitido (10 MB).",
                        request.getRequestURI(), Instant.now()));
    }

    /**
     * Rota inexistente no mapeamento do Spring MVC → HTTP 404.
     *
     * <p>Diferente do {@link #handleNotFound}, que trata entidades de domínio
     * não encontradas, este handler captura requisições para URIs que não
     * correspondem a nenhum {@code @RequestMapping} registrado. Evita o
     * fallback padrão do Spring (página de erro Whitelabel).
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(
            final NoResourceFoundException ex, final HttpServletRequest request) {
        log.debug("Route not found [{}]", request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "Not Found",
                        "Recurso não encontrado: " + request.getRequestURI(),
                        request.getRequestURI(), Instant.now()));
    }

    // ── Fallback ──────────────────────────────────────────────────────────────

    /**
     * Catchall para qualquer exceção não tratada → HTTP 500.
     *
     * <p>Rede de segurança final. Se chegou aqui, é um bug ou uma exceção de
     * infraestrutura não prevista (ex.: timeout de banco, OOM). Log em {@code ERROR}
     * com stack trace completa para diagnóstico. A mensagem genérica evita vazar
     * detalhes internos (nomes de classes, stack frames, queries SQL) para o cliente.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(
            final Exception ex, final HttpServletRequest request) {
        log.error("Unexpected error [{}]: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "Internal Server Error",
                        "Erro interno. Tente novamente mais tarde.",
                        request.getRequestURI(), Instant.now()));
    }
}
