package com.btree.api.controller.catalog;

import com.btree.api.dto.request.catalog.product.AdjustStockRequest;
import com.btree.api.dto.request.catalog.product.CreateProductRequest;
import com.btree.api.dto.request.catalog.product.ProductImageRequest;
import com.btree.api.dto.request.catalog.product.UpdateProductRequest;
import com.btree.api.dto.response.catalog.product.*;
import com.btree.application.usecase.catalog.product.adjust_stock.AdjustStockCommand;
import com.btree.application.usecase.catalog.product.adjust_stock.AdjustStockUseCase;
import com.btree.application.usecase.catalog.product.list_stock_movements.ListStockMovementsCommand;
import com.btree.application.usecase.catalog.product.list_stock_movements.ListStockMovementsUseCase;
import com.btree.application.usecase.catalog.product.create.CreateProductCommand;
import com.btree.application.usecase.catalog.product.create.CreateProductUseCase;
import com.btree.application.usecase.catalog.product.get_by_id.GetProductByIdCommand;
import com.btree.application.usecase.catalog.product.get_by_id.GetProductByIdUseCase;
import com.btree.application.usecase.catalog.product.list_all.ListAllProductsCommand;
import com.btree.application.usecase.catalog.product.list_all.ListAllProductsUseCase;
import com.btree.application.usecase.catalog.product.list_products_by_category.ListProductsByCategoryCommand;
import com.btree.application.usecase.catalog.product.list_products_by_category.ListProductsByCategoryUseCase;
import com.btree.application.usecase.catalog.product.update.UpdateProductCommand;
import com.btree.application.usecase.catalog.product.update.UpdateProductUseCase;
import com.btree.shared.domain.DomainException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/v1/catalog/products")
@Tag(name = "Products", description = "Gerenciamento de produtos do catálogo")
public class ProductController {

    private final ListAllProductsUseCase listAllProductsUseCase;
    private final ListProductsByCategoryUseCase listProductsByCategoryUseCase;
    private final CreateProductUseCase createProductUseCase;
    private final GetProductByIdUseCase getProductByIdUseCase;
    private final UpdateProductUseCase updateProductUseCase;
    private final AdjustStockUseCase adjustStockUseCase;
    private final ListStockMovementsUseCase listStockMovementsUseCase;

    public ProductController(ListAllProductsUseCase listAllProductsUseCase, ListProductsByCategoryUseCase listProductsByCategoryUseCase, CreateProductUseCase createProductUseCase, GetProductByIdUseCase getProductByIdUseCase, UpdateProductUseCase updateProductUseCase, AdjustStockUseCase adjustStockUseCase, ListStockMovementsUseCase listStockMovementsUseCase) {
        this.listAllProductsUseCase = listAllProductsUseCase;
        this.listProductsByCategoryUseCase = listProductsByCategoryUseCase;
        this.createProductUseCase = createProductUseCase;
        this.getProductByIdUseCase = getProductByIdUseCase;
        this.updateProductUseCase = updateProductUseCase;
        this.adjustStockUseCase = adjustStockUseCase;
        this.listStockMovementsUseCase = listStockMovementsUseCase;
    }

// ── ListAllProducts ───────────────────────────────────────────────────────

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Listar todos os produtos",
            description = "Retorna lista paginada de todos os produtos independente de status."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    })
    public ListAllProductsResponse listAll(
            @RequestParam(defaultValue = "0")  final int page,
            @RequestParam(defaultValue = "20") final int size
    ) {
        return ListAllProductsResponse.from(
                listAllProductsUseCase.execute(new ListAllProductsCommand(page, size))
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }

    // ── UC-64: ListProductsByCategory ─────────────────────────────────────────

    @GetMapping("/by-category/{categoryId}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Listar produtos por categoria",
            description = "Retorna lista paginada de produtos ACTIVE de uma categoria. " +
                    "Requer que a categoria exista e não esteja removida."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada ou removida"),
            @ApiResponse(responseCode = "422", description = "Parâmetros de paginação inválidos")
    })
    public ListProductsByCategoryResponse listByCategory(
            @PathVariable final String categoryId,
            @RequestParam(defaultValue = "0")  final int page,
            @RequestParam(defaultValue = "20") final int size
    ) {
        return ListProductsByCategoryResponse.from(
                listProductsByCategoryUseCase.execute(new ListProductsByCategoryCommand(categoryId, page, size))
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }

    // ── GetProductById ────────────────────────────────────────

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Buscar produto por ID",
            description = "Retorna os dados completos de um produto, incluindo imagens. Produtos deletados retornam 404."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Produto encontrado"),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado ou removido")
    })
    public GetProductResponse getById(@PathVariable final String id) {
        return GetProductResponse.from(
                getProductByIdUseCase.execute(new GetProductByIdCommand(id))
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }

    // ── UC-57 CreateProduct ───────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Cadastrar produto",
            description = "Cria um novo produto em status DRAFT. Slug e SKU devem ser únicos."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Produto criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "409", description = "Slug ou SKU já cadastrado"),
            @ApiResponse(responseCode = "422", description = "Regras de domínio violadas")
    })
    public CreateProductResponse create(@Valid @RequestBody final CreateProductRequest request) {
        return CreateProductResponse.from(
                createProductUseCase.execute(toCommand(request))
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }

    // ── UC-58 UpdateProduct ───────────────────────────────────

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Editar produto",
            description = "Atualiza os dados cadastrais de um produto existente. Não altera status nem estoque."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Produto atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado"),
            @ApiResponse(responseCode = "409", description = "Slug ou SKU já utilizado por outro produto"),
            @ApiResponse(responseCode = "422", description = "Regras de domínio violadas")
    })
    public UpdateProductResponse update(
            @PathVariable final String id,
            @Valid @RequestBody final UpdateProductRequest request
    ) {
        final var command = new UpdateProductCommand(
                id,
                request.categoryId(),
                request.brandId(),
                request.name(),
                request.slug(),
                request.description(),
                request.shortDescription(),
                request.sku(),
                request.price(),
                request.compareAtPrice(),
                request.costPrice(),
                request.lowStockThreshold(),
                request.weight(),
                request.width(),
                request.height(),
                request.depth(),
                request.featured()
        );
        return UpdateProductResponse.from(
                updateProductUseCase.execute(command)
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }

    // ── Helpers ───────────────────────────────────────────────

    private CreateProductCommand toCommand(final CreateProductRequest request) {
        return new CreateProductCommand(
                request.categoryId(),
                request.brandId(),
                request.name(),
                request.slug(),
                request.description(),
                request.shortDescription(),
                request.sku(),
                request.price(),
                request.compareAtPrice(),
                request.costPrice(),
                Objects.requireNonNullElse(request.lowStockThreshold(), 0),
                request.weight(),
                request.width(),
                request.height(),
                request.depth(),
                toImageEntries(request.images())
        );
    }

    // ── UC-70 AdjustStock ────────────────────────────────────

    @PostMapping("/{productId}/stock/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Ajuste manual de estoque",
            description = "Registra uma entrada ou saída manual de estoque. " +
                    "delta > 0 = entrada; delta < 0 = saída. " +
                    "Atualiza o saldo do produto e grava um registro imutável de movimentação. " +
                    "Transições ACTIVE ↔ OUT_OF_STOCK são disparadas automaticamente.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ajuste realizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado"),
            @ApiResponse(responseCode = "409", description = "Conflito de versão (optimistic locking)"),
            @ApiResponse(responseCode = "422", description = "Produto deletado, delta zero, tipo inválido ou estoque insuficiente")
    })
    public AdjustStockResponse adjustStock(
            @PathVariable final String productId,
            @Valid @RequestBody final AdjustStockRequest request
    ) {
        final var command = new AdjustStockCommand(
                productId,
                request.delta(),
                request.movementType(),
                request.notes(),
                request.referenceId(),
                request.referenceType()
        );
        return AdjustStockResponse.from(
                adjustStockUseCase.execute(command)
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }




    // ── ListStockMovements ───────────────────────────────────

    @GetMapping("/{productId}/stock/movements")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Listar movimentações de estoque",
            description = "Retorna histórico paginado de movimentações de estoque de um produto, " +
                    "ordenado do mais recente para o mais antigo."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado")
    })
    public ListStockMovementsResponse listStockMovements(
            @PathVariable final String productId,
            @RequestParam(defaultValue = "0")  final int page,
            @RequestParam(defaultValue = "20") final int size
    ) {
        return ListStockMovementsResponse.from(
                listStockMovementsUseCase.execute(new ListStockMovementsCommand(productId, page, size))
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }

    /**
     * Converte e ordena as imagens do request. Se sortOrder for fornecido, imagens são
     * ordenadas por ele (nulls por último); a posição resultante determina sortOrder e primary no UseCase.
     */
    private List<CreateProductCommand.ImageEntry> toImageEntries(final List<ProductImageRequest> images) {
        if (images == null || images.isEmpty()) return List.of();
        return images.stream()
                .sorted(Comparator.comparingInt(i -> Objects.requireNonNullElse(i.sortOrder(), Integer.MAX_VALUE)))
                .map(i -> new CreateProductCommand.ImageEntry(i.url(), i.altText()))
                .toList();
    }
}
