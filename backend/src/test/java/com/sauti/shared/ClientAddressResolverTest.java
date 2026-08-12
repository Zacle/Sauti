package com.sauti.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientAddressResolverTest {
    private final ClientAddressResolver resolver = new ClientAddressResolver();

    @Test
    void ignoresClientSuppliedForwardedForHeaders() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.24");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 192.0.2.44");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.24");
    }

    @Test
    void usesStableFallbackWhenContainerHasNoAddress() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("");

        assertThat(resolver.resolve(request)).isEqualTo("unknown");
    }
}
