package com.btree.shared.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes para o Value Object Slug")
class SlugTest {

    @Test
    @DisplayName("Deve instanciar um Slug já formatado perfeitamente")
    void deveInstanciarSlugFormatado() {
        final var slug = Slug.of("nome-do-produto-123");
        assertNotNull(slug);
        assertEquals("nome-do-produto-123", slug.getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Nome-com-Maiusculas",
            "nome espaço",
            "nome--consecutivo", // dois hifens seguidos geralmente são rejeitados pelo pattern,
                                 // o of() não deve aceitar algo fora do ^[a-z0-9]+(?:-[a-z0-9]+)*$
            "inicio-com--",      // no fim
            "-inicio",           // hifen no comeco
            "nome_com_under",
            "nome.com.ponto"
    })
    @DisplayName("Deve lançar exceção ao usar of() com slug inválido")
    void deveLancarExcecaoComSlugInvalido(String formato) {
        assertThrows(IllegalArgumentException.class, () -> Slug.of(formato));
    }

    @Test
    @DisplayName("O slugify deve transformar texto sujo em um slug perfeito")
    void slugifyDeveTransformarTextoLivre() {
        final var text = "Camiseta Azul Marinho 100% Algodão! $# %$";
        final var slug = Slug.slugify(text);
        
        assertNotNull(slug);
        assertEquals("camiseta-azul-marinho-100-algodao", slug.getValue());
    }

    @Test
    @DisplayName("Slugify deve remover multiplos hifens subsequentes e espaços sobrando nas pontas")
    void slugifyDeveRemoverEspacosEHifensMultiplos() {
        final var text = "   Meu   Produto --- Legal   ";
        final var slug = Slug.slugify(text);
        
        assertNotNull(slug);
        assertEquals("meu-produto-legal", slug.getValue());
    }
}
