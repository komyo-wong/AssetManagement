package com.assetmanagement.mqtt.infrastructure;

import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Resolves and pins a broker endpoint after rejecting URI tricks and internal
 * network targets. Pinning prevents a second DNS lookup from turning a checked
 * public name into an internal address.
 */
@Component
public class MqttEndpointPolicy {

    private static final Set<String> SCHEMES = Set.of("mqtt", "mqtts", "ws", "wss");

    private final MqttConnectionTestProperties properties;
    private final HostResolver resolver;

    @Autowired
    public MqttEndpointPolicy(MqttConnectionTestProperties properties) {
        this(properties, InetAddress::getAllByName);
    }

    MqttEndpointPolicy(MqttConnectionTestProperties properties, HostResolver resolver) {
        this.properties = properties;
        this.resolver = resolver;
    }

    public void validateConfiguration(String brokerUri, boolean tlsEnabled) {
        validateUri(brokerUri, tlsEnabled);
    }

    public ResolvedMqttEndpoint validateAndResolve(String brokerUri, boolean tlsEnabled) {
        ParsedEndpoint parsed = validateUri(brokerUri, tlsEnabled);

        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(parsed.host());
        } catch (UnknownHostException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Broker hostname cannot be resolved");
        }
        if (addresses.length == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Broker hostname cannot be resolved");
        }

        InetAddress selected = null;
        for (InetAddress address : addresses) {
            if (!isAllowed(address)) {
                throw blocked("Broker hostname resolves to a blocked network address");
            }
            if (selected == null) {
                selected = address;
            }
        }
        try {
            InetAddress pinned = InetAddress.getByAddress(parsed.host(), selected.getAddress());
            return new ResolvedMqttEndpoint(
                    parsed.scheme(),
                    parsed.host(),
                    parsed.path(),
                    new InetSocketAddress(pinned, parsed.port())
            );
        } catch (UnknownHostException exception) {
            throw new IllegalStateException("Resolved broker address was invalid", exception);
        }
    }

    private ParsedEndpoint validateUri(String brokerUri, boolean tlsEnabled) {
        URI uri = parse(brokerUri);
        String scheme = normalizeScheme(uri.getScheme());
        if (!SCHEMES.contains(scheme)) {
            throw blocked("Broker URI scheme is not allowed");
        }
        if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw blocked("Broker URI cannot contain credentials, query parameters or fragments");
        }
        if ((scheme.equals("mqtts") || scheme.equals("wss")) != tlsEnabled) {
            throw blocked("TLS setting does not match the broker URI scheme");
        }
        String host = normalizeHost(uri.getHost());
        int port = uri.getPort();
        if (port < 1 || port > 65_535 || !properties.getAllowedPorts().contains(port)) {
            throw blocked("Broker port is not in the deployment allowlist");
        }
        return new ParsedEndpoint(scheme, host, port, validatePath(scheme, uri.getRawPath()));
    }

    private boolean isAllowed(InetAddress address) {
        String normalizedAddress = address.getHostAddress().toLowerCase(Locale.ROOT);
        if (properties.getAllowedPrivateAddresses().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(normalizedAddress::equals)) {
            return true;
        }
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return first != 0
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 192 && second == 0)
                    && !(first == 192 && second == 0 && Byte.toUnsignedInt(bytes[2]) == 2)
                    && !(first == 198 && (second == 18 || second == 19))
                    && !(first == 198 && second == 51 && Byte.toUnsignedInt(bytes[2]) == 100)
                    && !(first == 203 && second == 0 && Byte.toUnsignedInt(bytes[2]) == 113)
                    && first < 224;
        }
        if (address instanceof Inet6Address) {
            boolean uniqueLocal = (bytes[0] & 0xfe) == 0xfc;
            byte[] documentationPrefix = {0x20, 0x01, 0x0d, (byte) 0xb8};
            boolean documentation = Arrays.equals(Arrays.copyOf(bytes, 4), documentationPrefix);
            return !uniqueLocal && !documentation;
        }
        return false;
    }

    private static URI parse(String value) {
        if (value == null || value.isBlank() || value.length() > 500) {
            throw blocked("Broker URI is required and must be at most 500 characters");
        }
        try {
            return URI.create(value.trim());
        } catch (IllegalArgumentException exception) {
            throw blocked("Broker URI is invalid");
        }
    }

    private static String normalizeScheme(String scheme) {
        return scheme == null ? "" : scheme.toLowerCase(Locale.ROOT);
    }

    private static String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            throw blocked("Broker hostname is required");
        }
        try {
            String ascii = IDN.toASCII(host.endsWith(".") ? host.substring(0, host.length() - 1) : host);
            if (ascii.isBlank() || ascii.length() > 253) {
                throw blocked("Broker hostname is invalid");
            }
            return ascii.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw blocked("Broker hostname is invalid");
        }
    }

    private static String validatePath(String scheme, String rawPath) {
        String path = rawPath == null || rawPath.isBlank() ? "/" : rawPath;
        if ((scheme.equals("mqtt") || scheme.equals("mqtts")) && !path.equals("/")) {
            throw blocked("TCP MQTT broker URI cannot contain a path");
        }
        if (path.length() > 200 || path.contains("..") || path.contains("\\")) {
            throw blocked("Broker WebSocket path is invalid");
        }
        return path;
    }

    private static BusinessException blocked(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    public record ResolvedMqttEndpoint(
            String scheme,
            String originalHost,
            String webSocketPath,
            InetSocketAddress pinnedAddress
    ) {
        public boolean tls() {
            return scheme.equals("mqtts") || scheme.equals("wss");
        }

        public boolean webSocket() {
            return scheme.equals("ws") || scheme.equals("wss");
        }
    }

    private record ParsedEndpoint(String scheme, String host, int port, String path) {
    }
}
