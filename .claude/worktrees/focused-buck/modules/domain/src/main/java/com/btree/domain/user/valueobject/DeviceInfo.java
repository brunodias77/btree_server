package com.btree.domain.user.valueobject;

import com.btree.shared.domain.ValueObject;

import java.util.Objects;

/**
 * Value Object com informações do dispositivo/cliente de uma sessão ou login.
 * Mapeado para as colunas ip_address e user_agent nas tabelas sessions e login_history.
 */
public class DeviceInfo extends ValueObject {

    private final String ipAddress;
    private final String userAgent;

    private DeviceInfo(final String ipAddress, final String userAgent) {
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public static DeviceInfo of(final String ipAddress, final String userAgent) {
        return new DeviceInfo(ipAddress, userAgent);
    }

    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DeviceInfo that = (DeviceInfo) o;
        return Objects.equals(ipAddress, that.ipAddress)
                && Objects.equals(userAgent, that.userAgent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ipAddress, userAgent);
    }

    @Override
    public String toString() {
        return "DeviceInfo{ip='%s', userAgent='%s'}".formatted(ipAddress, userAgent);
    }
}
