package net.openhft.chronicle.wire;

import net.openhft.chronicle.wire.internal.FileMarshallableOut;
import net.openhft.chronicle.wire.internal.HTTPMarshallableOut;

import java.net.URL;
import java.util.function.Supplier;

public class MarshallableOutBuilder implements Supplier<MarshallableOut> {
    private final URL url;
    private WireType wireType;

    public MarshallableOutBuilder(URL url) {
        this.url = url;
    }

    @Override
    public MarshallableOut get() {
        switch (url.getProtocol()) {
            case "tcp":
                throw new UnsupportedOperationException("Direct TCP connection not implemented");
            case "file":
                if (wireType != null && wireType != WireType.YAML_ONLY)
                    throw new IllegalArgumentException("Unsupported wireType; " + wireType);
                // URL file protocol doesn't support writing...
                return new FileMarshallableOut(this, wireTypeOr(WireType.YAML_ONLY));
            case "http":
            case "https":
                if (wireType != null && wireType != WireType.JSON_ONLY)
                    throw new IllegalArgumentException("Unsupported wireType; " + wireType);
                return new HTTPMarshallableOut(this, wireTypeOr(WireType.JSON_ONLY));
            default:
                throw new UnsupportedOperationException("Writing to " + url.getProtocol() + " is  not implemented");
        }
    }

    private WireType wireTypeOr(WireType wireType) {
        return this.wireType == null ? wireType : this.wireType;
    }

    public URL url() {
        return url;
    }

    public MarshallableOutBuilder wireType(WireType wireType) {
        this.wireType = wireType;
        return this;
    }
}
