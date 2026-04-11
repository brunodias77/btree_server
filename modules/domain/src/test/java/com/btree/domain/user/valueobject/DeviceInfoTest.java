package com.btree.domain.user.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do Value Object DeviceInfo")
public class DeviceInfoTest {

    @Test
    @DisplayName("Deve instanciar um DeviceInfo corretamente")
    void deveInstanciarCorretamente() {
        final var ip = "192.168.1.1";
        final var userAgent = "Mozilla/5.0";

        final var device = DeviceInfo.of(ip, userAgent);

        assertNotNull(device);
        assertEquals(ip, device.getIpAddress());
        assertEquals(userAgent, device.getUserAgent());
    }

    @Test
    @DisplayName("Deve garantir a igualdade de valor e hashcode entre instâncias iguais")
    void deveGarantirIgualdade() {
        final var ip = "10.0.0.1";
        final var userAgent = "Chrome/100.0";

        final var device1 = DeviceInfo.of(ip, userAgent);
        final var device2 = DeviceInfo.of(ip, userAgent);

        assertEquals(device1, device2);
        assertEquals(device1.hashCode(), device2.hashCode());
    }

    @Test
    @DisplayName("Deve suportar valores nulos, caso não exigido validação estrita no domínio")
    void deveSuportarValoresNulos() {
        final var device = DeviceInfo.of(null, null);

        assertNotNull(device);
        assertNull(device.getIpAddress());
        assertNull(device.getUserAgent());
    }
}
