package com.btree.domain.user.entity;

import com.btree.domain.UnitTest;
import com.btree.domain.user.identifier.AddressId;
import com.btree.domain.user.identifier.UserId;
import com.btree.shared.validation.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Address (Entity)")
public class AddressTest extends UnitTest {

    // ── Helpers ───────────────────────────────────────────────

    private static final String VALID_STREET      = "Rua das Flores";
    private static final String VALID_NUMBER      = "42";
    private static final String VALID_NEIGHBORHOOD = "Centro";
    private static final String VALID_CITY        = "São Paulo";
    private static final String VALID_STATE       = "SP";
    private static final String VALID_POSTAL_CODE = "01310-100";
    private static final String VALID_COUNTRY     = "BR";
    private static final String VALID_LABEL       = "Casa";
    private static final String VALID_RECIPIENT   = "Bruno Dias";

    /** Cria um endereço válido via factory, já com Notification limpa. */
    private static Address validAddress(UserId userId, boolean isFirstAddress) {
        final var notification = Notification.create();
        final var address = Address.create(
                userId,
                VALID_LABEL, VALID_RECIPIENT,
                VALID_STREET, VALID_NUMBER, null,
                VALID_NEIGHBORHOOD, VALID_CITY, VALID_STATE,
                VALID_POSTAL_CODE, VALID_COUNTRY,
                false, isFirstAddress,
                notification
        );
        assertFalse(notification.hasError(), "pré-condição: endereço válido não deve ter erros");
        return address;
    }

    private static Address validAddress() {
        return validAddress(UserId.unique(), false);
    }

    // ─────────────────────────────────────────────────────────
    // create()
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create() — campos e defaults")
    class CreateFactory {

        @Test
        @DisplayName("deve gerar ID único e não nulo")
        void deveGerarIdUnico() {
            final var a = validAddress();
            final var b = validAddress();
            assertNotNull(a.getId());
            assertNotEquals(a.getId(), b.getId());
        }

        @Test
        @DisplayName("deve persistir todos os campos fornecidos")
        void devePersistirCamposFornecidos() {
            final var userId = UserId.unique();
            final var address = validAddress(userId, false);

            assertEquals(userId,           address.getUserId());
            assertEquals(VALID_LABEL,      address.getLabel());
            assertEquals(VALID_RECIPIENT,  address.getRecipientName());
            assertEquals(VALID_STREET,     address.getStreet());
            assertEquals(VALID_NUMBER,     address.getNumber());
            assertEquals(VALID_NEIGHBORHOOD, address.getNeighborhood());
            assertEquals(VALID_CITY,       address.getCity());
            assertEquals(VALID_STATE,      address.getState());
            assertEquals(VALID_POSTAL_CODE, address.getPostalCode());
            assertEquals(VALID_COUNTRY,    address.getCountry());
        }

        @Test
        @DisplayName("deve usar 'BR' como country padrão quando country for nulo")
        void deveFallbackCountryBR() {
            final var notification = Notification.create();
            final var address = Address.create(
                    UserId.unique(),
                    VALID_LABEL, VALID_RECIPIENT,
                    VALID_STREET, VALID_NUMBER, null,
                    VALID_NEIGHBORHOOD, VALID_CITY, VALID_STATE,
                    VALID_POSTAL_CODE, null,   // country nulo
                    false, false, notification
            );
            assertFalse(notification.hasError());
            assertEquals("BR", address.getCountry(), "country deve ser 'BR' quando nulo");
        }

        @Test
        @DisplayName("deve marcar isDefault=true quando isFirstAddress=true")
        void deveMarcarDefaultParaPrimeiroEndereco() {
            final var address = validAddress(UserId.unique(), true);
            assertTrue(address.isDefault(), "primeiro endereço deve ser o padrão");
        }

        @Test
        @DisplayName("deve marcar isDefault=false quando isFirstAddress=false")
        void naoDeveMarcarDefaultParaEnderecosSeguintes() {
            final var address = validAddress(UserId.unique(), false);
            assertFalse(address.isDefault(), "endereço adicional não deve ser padrão automaticamente");
        }

        @Test
        @DisplayName("deve inicializar com latitude, longitude e ibgeCode nulos")
        void deveInicializarCamposGeoNulos() {
            final var address = validAddress();
            assertNull(address.getLatitude());
            assertNull(address.getLongitude());
            assertNull(address.getIbgeCode());
        }

        @Test
        @DisplayName("deve inicializar deletedAt como nulo (não deletado)")
        void deveInicializarNaoDeletedo() {
            final var address = validAddress();
            assertNull(address.getDeletedAt());
            assertFalse(address.isDeleted());
        }

        @Test
        @DisplayName("deve preencher createdAt e updatedAt no momento da criação")
        void devePreencherTimestamps() {
            final var before  = Instant.now();
            final var address = validAddress();
            final var after   = Instant.now();

            assertNotNull(address.getCreatedAt());
            assertNotNull(address.getUpdatedAt());
            assertFalse(address.getCreatedAt().isBefore(before));
            assertFalse(address.getCreatedAt().isAfter(after));
        }

        @Test
        @DisplayName("deve aceitar complement nulo (campo opcional)")
        void deveAceitarComplementNulo() {
            final var notification = Notification.create();
            assertDoesNotThrow(() -> Address.create(
                    UserId.unique(), VALID_LABEL, VALID_RECIPIENT,
                    VALID_STREET, VALID_NUMBER, null,
                    VALID_NEIGHBORHOOD, VALID_CITY, VALID_STATE,
                    VALID_POSTAL_CODE, VALID_COUNTRY,
                    false, false, notification
            ));
            assertFalse(notification.hasError());
        }
    }

    // ─────────────────────────────────────────────────────────
    // with() — reconstituição
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("with() — reconstituição do banco")
    class WithFactory {

        @Test
        @DisplayName("deve reconstituir todos os campos incluindo geo e ibgeCode")
        void deveReconstituirTodosCampos() {
            final var id     = AddressId.unique();
            final var userId = UserId.unique();
            final var now    = Instant.now();
            final var lat    = new BigDecimal("23.5505");
            final var lng    = new BigDecimal("-46.6333");

            final var address = Address.with(
                    id, userId,
                    "Trabalho", "Maria Silva",
                    "Av. Paulista", "1000", "Apto 42",
                    "Bela Vista", "São Paulo", "SP",
                    "01310-100", "BR",
                    lat, lng, "3550308",
                    true, true,
                    now, now, null
            );

            assertEquals(id,               address.getId());
            assertEquals(userId,           address.getUserId());
            assertEquals("Trabalho",       address.getLabel());
            assertEquals("Maria Silva",    address.getRecipientName());
            assertEquals("Av. Paulista",   address.getStreet());
            assertEquals("1000",           address.getNumber());
            assertEquals("Apto 42",        address.getComplement());
            assertEquals("Bela Vista",     address.getNeighborhood());
            assertEquals("São Paulo",      address.getCity());
            assertEquals("SP",             address.getState());
            assertEquals("01310-100",      address.getPostalCode());
            assertEquals("BR",             address.getCountry());
            assertEquals(lat,              address.getLatitude());
            assertEquals(lng,              address.getLongitude());
            assertEquals("3550308",        address.getIbgeCode());
            assertTrue(address.isDefault());
            assertTrue(address.isBillingAddress());
            assertNull(address.getDeletedAt());
        }

        @Test
        @DisplayName("deve reconstituir endereço deletado com deletedAt preenchido")
        void deveReconstituirEnderecoDeletedo() {
            final var now     = Instant.now();
            final var deleted = now.plusSeconds(3600);

            final var address = Address.with(
                    AddressId.unique(), UserId.unique(),
                    null, null,
                    VALID_STREET, VALID_NUMBER, null,
                    VALID_NEIGHBORHOOD, VALID_CITY, VALID_STATE,
                    VALID_POSTAL_CODE, VALID_COUNTRY,
                    null, null, null,
                    false, false,
                    now, deleted, deleted
            );

            assertNotNull(address.getDeletedAt());
            assertTrue(address.isDeleted());
        }
    }

    // ─────────────────────────────────────────────────────────
    // setAsDefault() / unsetDefault()
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("setAsDefault() e unsetDefault()")
    class DefaultManagement {

        @Test
        @DisplayName("setAsDefault() deve marcar isDefault como true")
        void deveDefinirComoDefault() {
            final var address = validAddress();
            assertFalse(address.isDefault(), "pré-condição: não é padrão");
            address.setAsDefault();
            assertTrue(address.isDefault());
        }

        @Test
        @DisplayName("setAsDefault() deve atualizar updatedAt")
        void setAsDefaultDeveAtualizarUpdatedAt() throws InterruptedException {
            final var address = validAddress();
            final var before  = address.getUpdatedAt();
            Thread.sleep(5);
            address.setAsDefault();
            assertTrue(address.getUpdatedAt().isAfter(before));
        }

        @Test
        @DisplayName("unsetDefault() deve marcar isDefault como false")
        void deveRemoverDefault() {
            final var address = validAddress(UserId.unique(), true);
            assertTrue(address.isDefault(), "pré-condição: é padrão");
            address.unsetDefault();
            assertFalse(address.isDefault());
        }

        @Test
        @DisplayName("unsetDefault() deve atualizar updatedAt")
        void unsetDefaultDeveAtualizarUpdatedAt() throws InterruptedException {
            final var address = validAddress(UserId.unique(), true);
            final var before  = address.getUpdatedAt();
            Thread.sleep(5);
            address.unsetDefault();
            assertTrue(address.getUpdatedAt().isAfter(before));
        }
    }

    // ─────────────────────────────────────────────────────────
    // updateData()
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateData()")
    class UpdateData {

        @Test
        @DisplayName("deve atualizar todos os campos mutáveis")
        void deveAtualizarCampos() {
            final var address = validAddress();
            address.updateData(
                    "Trabalho", "Carlos",
                    "Av. Faria Lima", "2000", "Sala 10",
                    "Itaim Bibi", "São Paulo", "SP",
                    "04538-132", "BR", true
            );

            assertEquals("Trabalho",     address.getLabel());
            assertEquals("Carlos",       address.getRecipientName());
            assertEquals("Av. Faria Lima", address.getStreet());
            assertEquals("2000",         address.getNumber());
            assertEquals("Sala 10",      address.getComplement());
            assertEquals("Itaim Bibi",   address.getNeighborhood());
            assertEquals("São Paulo",    address.getCity());
            assertEquals("SP",          address.getState());
            assertEquals("04538-132",    address.getPostalCode());
            assertEquals("BR",          address.getCountry());
            assertTrue(address.isBillingAddress());
        }

        @Test
        @DisplayName("deve usar 'BR' como fallback de country quando country for nulo no update")
        void deveFallbackCountryNoUpdate() {
            final var address = validAddress();
            address.updateData(
                    VALID_LABEL, VALID_RECIPIENT,
                    VALID_STREET, VALID_NUMBER, null,
                    VALID_NEIGHBORHOOD, VALID_CITY, VALID_STATE,
                    VALID_POSTAL_CODE, null,  // country nulo
                    false
            );
            assertEquals("BR", address.getCountry());
        }

        @Test
        @DisplayName("deve atualizar updatedAt ao chamar updateData()")
        void deveAtualizarUpdatedAt() throws InterruptedException {
            final var address = validAddress();
            final var before  = address.getUpdatedAt();
            Thread.sleep(5);
            address.updateData(
                    VALID_LABEL, VALID_RECIPIENT,
                    "Nova Rua", VALID_NUMBER, null,
                    VALID_NEIGHBORHOOD, VALID_CITY, VALID_STATE,
                    VALID_POSTAL_CODE, VALID_COUNTRY, false
            );
            assertTrue(address.getUpdatedAt().isAfter(before));
        }
    }

    // ─────────────────────────────────────────────────────────
    // softDelete() / isDeleted()
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("softDelete() e isDeleted()")
    class SoftDelete {

        @Test
        @DisplayName("deve preencher deletedAt e updatedAt ao chamar softDelete()")
        void devePrencherdeletedAtEUpdatedAt() {
            final var address = validAddress();
            assertFalse(address.isDeleted(), "pré-condição: não está deletado");
            address.softDelete();
            assertNotNull(address.getDeletedAt());
            assertNotNull(address.getUpdatedAt());
            assertTrue(address.isDeleted());
        }

        @Test
        @DisplayName("deletedAt deve ser posterior à criação")
        void deletedAtDeveSerPosteriorAoCriadoAt() throws InterruptedException {
            final var address = validAddress();
            Thread.sleep(5);
            address.softDelete();
            assertTrue(address.getDeletedAt().isAfter(address.getCreatedAt()));
        }

        @Test
        @DisplayName("isDeleted() deve retornar false para endereço não deletado")
        void isDeletedFalseParaNaoDeletado() {
            assertFalse(validAddress().isDeleted());
        }
    }

    // ─────────────────────────────────────────────────────────
    // validate() — AddressValidator
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validate() — AddressValidator")
    class Validation {

        // ── street ────────────────────────────────────────────

        @Test
        @DisplayName("deve reportar erro quando street for nula")
        void deveRejeitarStreetNula() {
            final var n = Notification.create();
            Address.create(UserId.unique(), VALID_LABEL, VALID_RECIPIENT,
                    null, VALID_NUMBER, null,
                    VALID_NEIGHBORHOOD, VALID_CITY, VALID_STATE,
                    VALID_POSTAL_CODE, VALID_COUNTRY, false, false, n);
            assertTrue(n.hasError());
            assertTrue(n.getErrors().stream().anyMatch(e -> e.message().contains("street")));
        }

        @Test
        @DisplayName("deve reportar erro quando street estiver em branco")
        void deveRejeitarStreetEmBranco() {
            final var n = Notification.create();
            Address.create(UserId.unique(), VALID_LABEL, VALID_RECIPIENT,
                    "  ", VALID_NUMBER, null,
                    VALID_NEIGHBORHOOD, VALID_CITY, VALID_STATE,
                    VALID_POSTAL_CODE, VALID_COUNTRY, false, false, n);
            assertTrue(n.hasError());
            assertTrue(n.getErrors().stream().anyMatch(e -> e.message().contains("street")));
        }

        @Test
        @DisplayName("deve reportar erro quando street exceder 255 caracteres")
        void deveRejeitarStreetLonga() {
            final var n = Notification.create();
            Address.create(UserId.unique(), VALID_LABEL, VALID_RECIPIENT,
                    "A".repeat(256), VALID_NUMBER, null,                          // 256 chars
                    VALID_NEIGHBORHOOD, VALID_CITY, VALID_STATE,
                    VALID_POSTAL_CODE, VALID_COUNTRY, false, false, n);
            assertTrue(n.hasError());
            assertTrue(n.getErrors().stream().anyMatch(e -> e.message().contains("street")));
        }

        @Test
        @DisplayName("deve aceitar street com exatamente 255 caracteres (limite)")
        void deveAceitarStreetNoLimite() {
            final var n = Notification.create();
            Address.create(UserId.unique(), VALID_LABEL, VALID_RECIPIENT,
                    "A".repeat(255), VALID_NUMBER, null,
                    VALID_NEIGHBORHOOD, VALID_CITY, VALID_STATE,
                    VALID_POSTAL_CODE, VALID_COUNTRY, false, false, n);
            assertFalse(n.hasError(), "255 chars deve ser aceito");
        }

        // ── city ──────────────────────────────────────────────

        @Test
        @DisplayName("deve reportar erro quando city for nula ou em branco")
        void deveRejeitarCityNulaOuEmBranco() {
            for (var city : new String[]{null, "  "}) {
                final var n = Notification.create();
                Address.create(UserId.unique(), VALID_LABEL, VALID_RECIPIENT,
                        VALID_STREET, VALID_NUMBER, null,
                        VALID_NEIGHBORHOOD, city, VALID_STATE,
                        VALID_POSTAL_CODE, VALID_COUNTRY, false, false, n);
                assertTrue(n.hasError(), "city=[" + city + "] deve ter erro");
                assertTrue(n.getErrors().stream().anyMatch(e -> e.message().contains("city")));
            }
        }

        @Test
        @DisplayName("deve reportar erro quando city exceder 100 caracteres")
        void deveRejeitarCityLonga() {
            final var n = Notification.create();
            Address.create(UserId.unique(), VALID_LABEL, VALID_RECIPIENT,
                    VALID_STREET, VALID_NUMBER, null,
                    VALID_NEIGHBORHOOD, "C".repeat(101), VALID_STATE,
                    VALID_POSTAL_CODE, VALID_COUNTRY, false, false, n);
            assertTrue(n.hasError());
            assertTrue(n.getErrors().stream().anyMatch(e -> e.message().contains("city")));
        }

        // ── state ─────────────────────────────────────────────

        @ParameterizedTest(name = "state=[{0}]")
        @ValueSource(strings = {"sp", "s", "São Paulo", "S1", "123", "SPP"})
        @DisplayName("deve reportar erro para state com formato inválido (não são 2 letras maiúsculas)")
        void deveRejeitarStateInvalido(String state) {
            final var n = Notification.create();
            Address.create(UserId.unique(), VALID_LABEL, VALID_RECIPIENT,
                    VALID_STREET, VALID_NUMBER, null,
                    VALID_NEIGHBORHOOD, VALID_CITY, state,
                    VALID_POSTAL_CODE, VALID_COUNTRY, false, false, n);
            assertTrue(n.hasError(), "state=[" + state + "] deveria ser inválido");
            assertTrue(n.getErrors().stream().anyMatch(e -> e.message().contains("state")));
        }

        @ParameterizedTest(name = "state=[{0}]")
        @ValueSource(strings = {"SP", "RJ", "MG", "RS", "AM"})
        @DisplayName("deve aceitar state com exatamente 2 letras maiúsculas")
        void deveAceitarStateValido(String state) {
            final var n = Notification.create();
            Address.create(UserId.unique(), VALID_LABEL, VALID_RECIPIENT,
                    VALID_STREET, VALID_NUMBER, null,
                    VALID_NEIGHBORHOOD, VALID_CITY, state,
                    VALID_POSTAL_CODE, VALID_COUNTRY, false, false, n);
            assertFalse(n.hasError(), "state=[" + state + "] deveria ser aceito");
        }

        @Test
        @DisplayName("deve reportar erro quando state for nulo")
        void deveRejeitarStateNulo() {
            final var n = Notification.create();
            Address.create(UserId.unique(), VALID_LABEL, VALID_RECIPIENT,
                    VALID_STREET, VALID_NUMBER, null,
                    VALID_NEIGHBORHOOD, VALID_CITY, null,
                    VALID_POSTAL_CODE, VALID_COUNTRY, false, false, n);
            assertTrue(n.hasError());
            assertTrue(n.getErrors().stream().anyMatch(e -> e.message().contains("state")));
        }

        // ── postalCode ────────────────────────────────────────

        @ParameterizedTest(name = "postalCode=[{0}]")
        @ValueSource(strings = {"1234567", "ABCDE-FGH", "0131010", "01310", "01310-1000", "01310-10"})
        @DisplayName("deve reportar erro para postalCode com formato inválido")
        void deveRejeitarPostalCodeInvalido(String postalCode) {
            final var n = Notification.create();
            Address.create(UserId.unique(), VALID_LABEL, VALID_RECIPIENT,
                    VALID_STREET, VALID_NUMBER, null,
                    VALID_NEIGHBORHOOD, VALID_CITY, VALID_STATE,
                    postalCode, VALID_COUNTRY, false, false, n);
            assertTrue(n.hasError(), "postalCode=[" + postalCode + "] deveria ser inválido");
        }

        @ParameterizedTest(name = "postalCode=[{0}]")
        @ValueSource(strings = {"01310-100", "04538-132", "30130-010"})
        @DisplayName("deve aceitar postalCode no formato NNNNN-NNN")
        void deveAceitarPostalCodeValido(String postalCode) {
            final var n = Notification.create();
            Address.create(UserId.unique(), VALID_LABEL, VALID_RECIPIENT,
                    VALID_STREET, VALID_NUMBER, null,
                    VALID_NEIGHBORHOOD, VALID_CITY, VALID_STATE,
                    postalCode, VALID_COUNTRY, false, false, n);
            assertFalse(n.hasError(), "postalCode=[" + postalCode + "] deveria ser aceito");
        }

        // ── acúmulo de erros ──────────────────────────────────

        @Test
        @DisplayName("deve acumular múltiplos erros quando vários campos estiverem inválidos")
        void deveAcumularMultiplosErros() {
            final var n = Notification.create();
            Address.create(UserId.unique(), null, null,
                    null,  // street inválida
                    null, null,
                    null, null, "minusculo",   // state inválido
                    "INVALIDO",                // postalCode inválido
                    VALID_COUNTRY, false, false, n);

            assertTrue(n.getErrors().size() >= 3,
                    "Deve ter ao menos 3 erros, mas teve: " + n.getErrors().size());
        }
    }
}
