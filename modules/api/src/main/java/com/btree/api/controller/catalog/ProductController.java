package com.btree.api.controller.catalog;

import com.btree.api.dto.request.catalog.product.CreateProductRequest;
import com.btree.api.dto.request.catalog.product.ProductImageRequest;
import com.btree.api.dto.response.catalog.product.CreateProductResponse;
import com.btree.api.dto.response.catalog.product.ListAllProductsResponse;
import com.btree.api.dto.response.catalog.product.ListProductsByCategoryResponse;
import com.btree.application.usecase.catalog.product.create.CreateProductCommand;
import com.btree.application.usecase.catalog.product.create.CreateProductUseCase;
import com.btree.application.usecase.catalog.product.list_all.ListAllProductsCommand;
import com.btree.application.usecase.catalog.product.list_all.ListAllProductsUseCase;
import com.btree.application.usecase.catalog.product.list_products_by_category.ListProductsByCategoryCommand;
import com.btree.application.usecase.catalog.product.list_products_by_category.ListProductsByCategoryUseCase;
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

    public ProductController(ListAllProductsUseCase listAllProductsUseCase, ListProductsByCategoryUseCase listProductsByCategoryUseCase, CreateProductUseCase createProductUseCase) {
        this.listAllProductsUseCase = listAllProductsUseCase;
        this.listProductsByCategoryUseCase = listProductsByCategoryUseCase;
        this.createProductUseCase = createProductUseCase;
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
