package dev.luxoru.tunnelvision.cache;

import dev.luxoru.tunnelvision.detection.Category;
import dev.luxoru.tunnelvision.detection.Confidence;
import dev.luxoru.tunnelvision.detection.Detection;
import dev.luxoru.tunnelvision.detection.Verdict;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A compact binary form of a {@link Verdict}, for caches that store bytes.
 * <p>
 * Bump {@link #VERSION} whenever the layout changes. Payloads with another version decode
 * to empty, a cache miss, so old entries are simply recomputed.
 * </p>
 */
public final class VerdictCodec {
    private static final byte VERSION = 2;

    private static final int MAX_UNSIGNED_SHORT = 0xFFFF;

    private VerdictCodec() {}

    public static byte[] encode(Verdict verdict) {
        int count = verdict.detections().size();
        if (count > MAX_UNSIGNED_SHORT) {
            throw new IllegalArgumentException("Too many detections to encode: " + count);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(64);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(VERSION);
            out.writeUTF(verdict.ip());
            out.writeDouble(verdict.risk());
            out.writeBoolean(verdict.blocked());
            // Enums by name, so reordering or adding values never changes what old payloads mean
            out.writeUTF(verdict.status().name());
            out.writeShort(verdict.categories().size());
            for (Category category : verdict.categories()) {
                out.writeUTF(category.name());
            }
            out.writeBoolean(verdict.provider() != null);
            if (verdict.provider() != null) {
                out.writeUTF(verdict.provider());
            }
            out.writeShort(count);
            for (Detection detection : verdict.detections()) {
                out.writeUTF(detection.source());
                out.writeUTF(detection.name());
                out.writeUTF(detection.category().name());
                out.writeUTF(detection.confidence().name());
                out.writeUTF(detection.match());
            }
        } catch (IOException ex) {
            // Writing to memory only fails when writeUTF gets a string over 65,535 bytes
            throw new UncheckedIOException(ex);
        }
        return bytes.toByteArray();
    }

    public static Optional<Verdict> decode(byte[] bytes) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readByte() != VERSION) {
                return Optional.empty();
            }
            String ip = in.readUTF();
            double risk = in.readDouble();
            boolean blocked = in.readBoolean();
            Verdict.Status status = Verdict.Status.valueOf(in.readUTF());
            int categoryCount = in.readUnsignedShort();
            Set<Category> categories = EnumSet.noneOf(Category.class);
            for (int i = 0; i < categoryCount; i++) {
                categories.add(Category.valueOf(in.readUTF()));
            }
            String provider = in.readBoolean() ? in.readUTF() : null;
            int count = in.readUnsignedShort();
            List<Detection> detections = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                detections.add(new Detection(in.readUTF(), in.readUTF(), Category.valueOf(in.readUTF()),
                    Confidence.valueOf(in.readUTF()), in.readUTF()));
            }
            if (in.available() > 0) {
                return Optional.empty(); // Trailing bytes, so this ist a payload we wrote
            }
            return Optional.of(new Verdict(ip, risk, blocked, status, categories, provider, detections));
        } catch (IOException | RuntimeException ex) {
            return Optional.empty();
        }
    }
}
