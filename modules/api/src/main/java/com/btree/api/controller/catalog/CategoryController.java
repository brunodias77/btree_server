package com.btree.api.controller.catalog;

import com.btree.api.dto.request.catalog.category.CreateCategoryRequest;
import com.btree.api.dto.request.catalog.category.UpdateCategoryRequest;
import com.btree.api.dto.response.catalog.category.CreateCategoryResponse;
import com.btree.api.dto.response.catalog.category.GetCategoryResponse;
import com.btree.api.dto.response.catalog.category.ListCategoriesResponse;
import com.btree.api.dto.response.catalog.category.UpdateCategoryResponse;
import com.btree.application.usecase.catalog.category.create.CreateCategoryCommand;
import com.btree.application.usecase.catalog.category.create.CreateCategoryUseCase;
import com.btree.application.usecase.catalog.category.get_by_id.GetCategoryCommand;
import com.btree.application.usecase.catalog.category.get_by_id.GetCategoryUseCase;
import com.btree.application.usecase.catalog.category.list_all_categories.ListAllCategoriesUseCase;
import com.btree.application.usecase.catalog.category.update.UpdateCategoryCommand;
import com.btree.application.usecase.catalog.category.update.UpdateCategoryUseCase;
import com.btree.shared.domain.DomainException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/catalog/categories")
@Tag(name = "Categories", description = "Gerenciamento de categorias do catálogo de produtos")
@SecurityRequirement(name = "bearerAuth")
public class CategoryController {

    final ListAllCategoriesUseCase listAllCategoriesUseCase;
    final CreateCategoryUseCase createCategoryUseCase;
    final UpdateCategoryUseCase updateCategoryUseCase;
    final GetCategoryUseCase getCategoryUseCase;

    public CategoryController(ListAllCategoriesUseCase listAllCategoriesUseCase, CreateCategoryUseCase createCategoryUseCase, UpdateCategoryUseCase updateCategoryUseCase, GetCategoryUseCase getCategoryUseCase) {
        this.listAllCategoriesUseCase = listAllCategoriesUseCase;
        this.createCategoryUseCase = createCategoryUseCase;
        this.updateCategoryUseCase = updateCategoryUseCase;
        this.getCategoryUseCase = getCategoryUseCase;
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Listar categorias",
            description = "Retorna a árvore completa de categorias ativas. " +
                    "Cada nó raiz contém seus filhos aninhados em 'children'. " +
                    "Categorias removidas (soft delete) são excluídas automaticamente. " +
                    "A ordem de cada nível respeita 'sort_order ASC'."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Árvore de categorias retornada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido")
    })
    public List<ListCategoriesResponse> listAll() {
        return listAllCategoriesUseCase.execute(null)
                .getOrElseThrow(n -> DomainException.with(n.getErrors()))
                .stream()
                .map(ListCategoriesResponse::from)
                .toList();
    }

    // ── UC-45: CreateCategory ─────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Criar categoria",
            description = "Cria uma nova categoria de produtos. " +
                    "Omitir 'parent_id' cria uma categoria raiz. " +
                    "O slug deve ser único entre categorias ativas e seguir o formato kebab-case " +
                    "(letras minúsculas, números e hífens)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Categoria criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos (formato de slug, campos obrigatórios)"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "422", description = "Slug já em uso ou categoria pai não encontrada")
    })
    public CreateCategoryResponse create(@Valid @RequestBody final CreateCategoryRequest request) {
        final var command = new CreateCategoryCommand(
                request.parentId(),
                request.name(),
                request.slug(),
                request.description(),
                request.imageUrl(),
                request.sortOrder()
        );
        return CreateCategoryResponse.from(
                createCategoryUseCase.execute(command)
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }

    // ── UC-46: UpdateCategory ─────────────────────────────────────────────

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Editar categoria",
            description = "Atualiza todos os campos mutáveis de uma categoria existente (PUT semântico). " +
                    "Campos não enviados são gravados como null. " +
                    "Para manter a categoria sem pai, omitir 'parent_id'. " +
                    "Não é possível editar uma categoria removida."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoria atualizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "422", description = "Categoria não encontrada, deletada, slug em uso, " +
                    "referência circular ou categoria pai inválida")
    })
    public UpdateCategoryResponse update(
            @PathVariable final String id,
            @Valid @RequestBody final UpdateCategoryRequest request
    ) {
        return UpdateCategoryResponse.from(
                updateCategoryUseCase.execute(new UpdateCategoryCommand(
                        id,
                        request.parentId(),
                        request.name(),
                        request.slug(),
                        request.description(),
                        request.imageUrl(),
                        request.sortOrder()
                )).getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }

    // ── UC-XX: GetCategoryById ─────────────────────────────────────────────

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Consultar categoria",
            description = "Retorna os dados completos de uma categoria pelo UUID. " +
                    "Categorias removidas (soft delete) retornam 422."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoria encontrada"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "422", description = "Categoria não encontrada ou removida")
    })
    public GetCategoryResponse getById(@PathVariable final String id) {
        return GetCategoryResponse.from(
                getCategoryUseCase.execute(new GetCategoryCommand(id))
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }
}
