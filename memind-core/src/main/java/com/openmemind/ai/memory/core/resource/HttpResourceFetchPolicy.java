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

import com.openmemind.ai.memory.core.exception.ResourceFetchAccessDeniedException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * Security policy applied before opening remote HTTP resources.
 */
public final class HttpResourceFetchPolicy {

    private static final int DEFAULT_MAX_REDIRECTS = 5;
    private static final byte[] IPV6_NAT64_WELL_KNOWN_PREFIX =
            new byte[] {0x00, 0x64, (byte) 0xff, (byte) 0x9b, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] IPV6_NAT64_LOCAL_USE_PREFIX =
            new byte[] {0x00, 0x64, (byte) 0xff, (byte) 0x9b, 0x00, 0x01};

    private final boolean allowPrivateNetwork;
    private final int maxRedirects;

    private HttpResourceFetchPolicy(Builder builder) {
        this.allowPrivateNetwork = builder.allowPrivateNetwork;
        this.maxRedirects = builder.maxRedirects;
    }

    public static HttpResourceFetchPolicy secureDefaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Whether RFC1918 IPv4 ranges and IPv6 unique-local/site-local addresses are allowed.
     *
     * <p>This does not allow loopback, any-local, link-local, multicast, or other reserved
     * addresses.
     */
    public boolean allowPrivateNetwork() {
        return allowPrivateNetwork;
    }

    /**
     * Maximum number of redirects followed manually after validating each redirect target.
     */
    public int maxRedirects() {
        return maxRedirects;
    }

    void validateUri(URI uri) {
        Objects.requireNonNull(uri, "uri is required");
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            deny(uri, "scheme must be http or https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            deny(uri, "host is required");
        }
        if (uri.getUserInfo() != null) {
            deny(uri, "userinfo is not allowed");
        }
    }

    void validateResolvedAddresses(URI uri, List<InetAddress> addresses) {
        Objects.requireNonNull(addresses, "addresses is required");
        if (addresses.isEmpty()) {
            deny(uri, "host did not resolve to any address");
        }
        for (InetAddress address : addresses) {
            if (!isAddressAllowed(address)) {
                deny(uri, "resolved address is not allowed");
            }
        }
    }

    static String describe(URI uri) {
        if (uri == null) {
            return "<unknown>";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("scheme=").append(uri.getScheme() == null ? "<none>" : uri.getScheme());
        builder.append(", host=").append(uri.getHost() == null ? "<none>" : uri.getHost());
        if (uri.getPort() >= 0) {
            builder.append(", port=").append(uri.getPort());
        }
        return builder.toString();
    }

    private boolean isAddressAllowed(InetAddress address) {
        Objects.requireNonNull(address, "address is required");
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        if (address instanceof Inet4Address) {
            return isIpv4AddressAllowed(address.getAddress());
        }
        if (address instanceof Inet6Address) {
            return isIpv6AddressAllowed(address.getAddress(), address.isSiteLocalAddress());
        }
        return false;
    }

    private boolean isIpv4AddressAllowed(byte[] bytes) {
        int first = unsigned(bytes[0]);
        int second = unsigned(bytes[1]);
        int third = unsigned(bytes[2]);
        if (isPrivateIpv4(first, second)) {
            return allowPrivateNetwork;
        }
        return !isReservedIpv4(first, second, third);
    }

    private boolean isIpv6AddressAllowed(byte[] bytes, boolean siteLocal) {
        if (isIpv4MappedIpv6(bytes) || isIpv4CompatibleIpv6(bytes)) {
            return isIpv4AddressAllowed(new byte[] {bytes[12], bytes[13], bytes[14], bytes[15]});
        }
        if (matchesPrefix(bytes, IPV6_NAT64_WELL_KNOWN_PREFIX, 96)) {
            return isIpv4AddressAllowed(new byte[] {bytes[12], bytes[13], bytes[14], bytes[15]});
        }
        if (isUniqueLocalIpv6(bytes) || siteLocal) {
            return allowPrivateNetwork;
        }
        return !isBlockedSpecialUseIpv6(bytes);
    }

    private boolean isPrivateIpv4(int first, int second) {
        return first == 10
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168);
    }

    private boolean isReservedIpv4(int first, int second, int third) {
        if (first == 0 || first == 127 || first >= 224) {
            return true;
        }
        if (first == 100 && second >= 64 && second <= 127) {
            return true;
        }
        if (first == 169 && second == 254) {
            return true;
        }
        if (first == 192 && second == 0 && third == 0) {
            return true;
        }
        if (first == 192 && second == 0 && third == 2) {
            return true;
        }
        if (first == 192 && second == 88 && third == 99) {
            return true;
        }
        if (first == 198 && (second == 18 || second == 19)) {
            return true;
        }
        if (first == 198 && second == 51 && third == 100) {
            return true;
        }
        return first == 203 && second == 0 && third == 113;
    }

    private boolean isIpv4MappedIpv6(byte[] bytes) {
        for (int index = 0; index < 10; index++) {
            if (bytes[index] != 0) {
                return false;
            }
        }
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }

    private boolean isIpv4CompatibleIpv6(byte[] bytes) {
        for (int index = 0; index < 12; index++) {
            if (bytes[index] != 0) {
                return false;
            }
        }
        return true;
    }

    private boolean isUniqueLocalIpv6(byte[] bytes) {
        return (bytes[0] & 0xfe) == 0xfc;
    }

    private boolean isBlockedSpecialUseIpv6(byte[] bytes) {
        return matchesPrefix(bytes, IPV6_NAT64_LOCAL_USE_PREFIX, 48)
                || matchesPrefix(bytes, new byte[] {0x01, 0x00, 0, 0, 0, 0, 0, 0}, 64)
                || matchesPrefix(bytes, new byte[] {0x01, 0x00, 0, 0, 0, 0, 0, 1}, 64)
                || matchesPrefix(bytes, new byte[] {0x20, 0x01, 0, 0}, 32)
                || matchesPrefix(bytes, new byte[] {0x20, 0x01, 0, 0x02}, 48)
                || matchesPrefix(bytes, new byte[] {0x20, 0x01, 0, 0x10}, 28)
                || matchesPrefix(bytes, new byte[] {0x20, 0x01, 0x0d, (byte) 0xb8}, 32)
                || matchesPrefix(bytes, new byte[] {0x20, 0x02}, 16)
                || matchesPrefix(bytes, new byte[] {0x3f, (byte) 0xff, 0}, 20)
                || matchesPrefix(bytes, new byte[] {0x5f, 0x00}, 16);
    }

    private boolean matchesPrefix(byte[] bytes, byte[] prefix, int prefixLength) {
        int fullBytes = prefixLength / 8;
        for (int index = 0; index < fullBytes; index++) {
            if (bytes[index] != prefix[index]) {
                return false;
            }
        }
        int remainingBits = prefixLength % 8;
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xff << (8 - remainingBits);
        return (bytes[fullBytes] & mask) == (prefix[fullBytes] & mask);
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }

    private void deny(URI uri, String reason) {
        throw new ResourceFetchAccessDeniedException(
                "Resource fetch denied: " + reason + ", target=" + describe(uri));
    }

    public static final class Builder {

        private boolean allowPrivateNetwork;
        private int maxRedirects = DEFAULT_MAX_REDIRECTS;

        private Builder() {}

        /**
         * Allow RFC1918 IPv4 ranges and IPv6 unique-local/site-local addresses.
         */
        public Builder allowPrivateNetwork(boolean allowPrivateNetwork) {
            this.allowPrivateNetwork = allowPrivateNetwork;
            return this;
        }

        /**
         * Set the maximum redirect count. Use zero to reject redirects.
         */
        public Builder maxRedirects(int maxRedirects) {
            if (maxRedirects < 0) {
                throw new IllegalArgumentException("maxRedirects must not be negative");
            }
            this.maxRedirects = maxRedirects;
            return this;
        }

        public HttpResourceFetchPolicy build() {
            return new HttpResourceFetchPolicy(this);
        }
    }
}
