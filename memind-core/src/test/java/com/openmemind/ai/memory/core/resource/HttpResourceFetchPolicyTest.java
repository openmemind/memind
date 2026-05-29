/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.openmemind.ai.memory.core.resource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class HttpResourceFetchPolicyTest {

    private static final URI TARGET = URI.create("https://documents.example/report.pdf");

    @Test
    void validateUriShouldRejectMissingHostAndUserInfo() {
        var policy = HttpResourceFetchPolicy.secureDefaults();

        assertThatThrownBy(() -> policy.validateUri(URI.create("https:///report.pdf")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host is required");
        assertThatThrownBy(() -> policy.validateUri(URI.create("https://user@example.com/report")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userinfo is not allowed");
    }

    @Test
    void validateResolvedAddressesShouldAllowGlobalAddresses() {
        var policy = HttpResourceFetchPolicy.secureDefaults();

        validate(policy, "93.184.216.34");
        validate(policy, "2606:2800:220:1:248:1893:25c8:1946");
    }

    @Test
    void validateResolvedAddressesShouldRejectDocumentationRanges() {
        var policy = HttpResourceFetchPolicy.secureDefaults();

        assertThatThrownBy(() -> validate(policy, "203.0.113.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("denied");
        assertThatThrownBy(() -> validate(policy, "2001:db8::1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("denied");
        assertThatThrownBy(() -> validate(policy, "3fff::1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("denied");
    }

    @Test
    void validateResolvedAddressesShouldRejectIpv6TransitionAndSpecialUseRanges() {
        var policy = HttpResourceFetchPolicy.secureDefaults();

        assertThatThrownBy(() -> validate(policy, "::10.1.2.3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("denied");
        assertThatThrownBy(() -> validate(policy, "64:ff9b::a9fe:a9fe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("denied");
        assertThatThrownBy(() -> validate(policy, "64:ff9b:1::1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("denied");
        assertThatThrownBy(() -> validate(policy, "2001::1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("denied");
        assertThatThrownBy(() -> validate(policy, "2002::1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("denied");
        assertThatThrownBy(() -> validate(policy, "100::1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("denied");
        assertThatThrownBy(() -> validate(policy, "5f00::1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("denied");
    }

    @Test
    void validateResolvedAddressesShouldAllowPrivateNetworkOnlyForExplicitRanges() {
        var defaultPolicy = HttpResourceFetchPolicy.secureDefaults();
        var privatePolicy = HttpResourceFetchPolicy.builder().allowPrivateNetwork(true).build();

        assertThatThrownBy(() -> validate(defaultPolicy, "fc00::1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("denied");

        validate(privatePolicy, "10.1.2.3");
        validate(privatePolicy, "fc00::1");

        assertThatThrownBy(() -> validate(privatePolicy, "100.64.1.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("denied");
        assertThatThrownBy(() -> validate(privatePolicy, "169.254.169.254"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("denied");
        assertThatThrownBy(() -> validate(privatePolicy, "192.88.99.2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("denied");
    }

    @Test
    void builderShouldRejectNegativeRedirectLimit() {
        assertThatThrownBy(() -> HttpResourceFetchPolicy.builder().maxRedirects(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRedirects");
    }

    private static void validate(HttpResourceFetchPolicy policy, String address) {
        policy.validateResolvedAddresses(TARGET, List.of(address(address)));
    }

    private static InetAddress address(String value) {
        try {
            return InetAddress.getByName(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid test address: " + value, ex);
        }
    }
}
