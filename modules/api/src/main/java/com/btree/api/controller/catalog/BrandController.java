package com.btree.api.controller.catalog;

import com.btree.api.dto.request.catalog.brand.CreateBrandRequest;
import com.btree.api.dto.request.catalog.brand.UpdateBrandRequest;
import com.btree.api.dto.response.catalog.brand.BrandItemResponse;
import com.btree.api.dto.response.catalog.brand.CreateBrandResponse;
import com.btree.api.dto.response.catalog.brand.UpdateBrandResponse;
import com.btree.application.usecase.catalog.brand.create.CreateBrandCommand;
import com.btree.application.usecase.catalog.brand.create.CreateBrandUseCase;
import com.btree.application.usecase.catalog.brand.list_all.ListAllBrandCommand;
import com.btree.application.usecase.catalog.brand.list_all.ListAllBrandUseCase;
import com.btree.application.usecase.catalog.brand.update.UpdateBrandCommand;
import com.btree.application.usecase.catalog.brand.update.UpdateBrandUseCase;
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
@RequestMapping("/v1/catalog/brands")
@Tag(name = "Brands", description = "Gerenciamento de marcas do catálogo de produtos")
public class BrandController {

    private final CreateBrandUseCase createBrandUseCase;
    private final ListAllBrandUseCase listAllBrandUseCase;
    private final UpdateBrandUseCase updateBrandUseCase;

    public BrandController(CreateBrandUseCase createBrandUseCase, ListAllBrandUseCase listAllBrandUseCase, UpdateBrandUseCase updateBrandUseCase) {
        this.createBrandUseCase = createBrandUseCase;
        this.listAllBrandUseCase = listAllBrandUseCase;
        this.updateBrandUseCase = updateBrandUseCase;
    }

// ── UC-52: CreateBrand ────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Criar marca",
            description = "Cadastra uma nova marca no catálogo. " +
                    "O slug deve ser único entre marcas ativas e seguir o formato kebab-case " +
                    "(letras minúsculas, números e hífens)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Marca criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos (formato de slug, campos obrigatórios)"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "422", description = "Slug já em uso")
    })
    public CreateBrandResponse create(@Valid @RequestBody final CreateBrandRequest request) {
        return CreateBrandResponse.from(
                createBrandUseCase.execute(new CreateBrandCommand(
                        request.name(),
                        request.slug(),
                        request.description(),
                        request.logoUrl()
                )).getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }

    // ── UC-53: ListAllBrands ──────────────────────────────────────────────────

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Listar marcas",
            description = "Retorna todas as marcas ativas do catálogo."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    })
    public List<BrandItemResponse> listAll() {
        return listAllBrandUseCase.execute(new ListAllBrandCommand())
                .getOrElseThrow(n -> DomainException.with(n.getErrors()))
                .items()
                .stream()
                .map(BrandItemResponse::from)
                .toList();
    }

    // ── UC-53: UpdateBrand ────────────────────────────────────────────────

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Editar marca",
            description = "Atualiza todos os campos mutáveis de uma marca existente (PUT semântico). " +
                    "Campos não enviados são gravados como null. " +
                    "Não é possível editar uma marca removida."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Marca atualizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "422", description = "Marca não encontrada, deletada ou slug já em uso")
    })
    public UpdateBrandResponse update(
            @PathVariable final String id,
            @Valid @RequestBody final UpdateBrandRequest request
    ) {
        return UpdateBrandResponse.from(
                updateBrandUseCase.execute(new UpdateBrandCommand(
                        id,
                        request.name(),
                        request.slug(),
                        request.description(),
                        request.logoUrl()
                )).getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }
}
