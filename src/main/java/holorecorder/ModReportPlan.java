package holorecorder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Splits the mod report into packets that each stay under Minecraft's serverbound custom-payload limit (32767
 * bytes), keeping the mods in order. Sizes are the exact encoded sizes of the wire format (VarInt-prefixed UTF-8
 * strings), so no part is ever refused by the encoder.
 */
final class ModReportPlan {
    /** Bytes per part for the mod entries, leaving room for the header and the channel name. */
    static final int PART_BUDGET = 30_000;

    private ModReportPlan() {}

    /** Bytes a VarInt takes. */
    static int varInt(int value) {
        int bytes = 1;
        while ((value & ~0x7F) != 0) {
            value >>>= 7;
            bytes++;
        }
        return bytes;
    }

    /** Bytes a VarLong takes. */
    static int varLong(long value) {
        int bytes = 1;
        while ((value & ~0x7FL) != 0) {
            value >>>= 7;
            bytes++;
        }
        return bytes;
    }

    /** Bytes {@code FriendlyByteBuf.writeUtf} writes for this string. */
    static int utf(String s) {
        int n = (s == null ? "" : s).getBytes(StandardCharsets.UTF_8).length;
        return varInt(n) + n;
    }

    /**
     * Consecutive groups of {@code items} whose sizes add up to at most {@code budget} and that hold at most
     * {@code maxItems} each; at most {@code maxParts} groups (items that do not fit are dropped from the end, as the
     * old report dropped entries past its cap).
     */
    static <T> List<List<T>> split(List<T> items, ToIntFunction<T> size, int budget, int maxItems, int maxParts) {
        List<List<T>> parts = new ArrayList<>();
        List<T> current = new ArrayList<>();
        int used = 0;
        for (T item : items) {
            int bytes = size.applyAsInt(item);
            if (bytes > budget) continue;   // cannot happen with the capped strings; never send an oversized part
            if (used + bytes > budget || current.size() == maxItems) {
                parts.add(current);
                if (parts.size() == maxParts) return parts;
                current = new ArrayList<>();
                used = 0;
            }
            current.add(item);
            used += bytes;
        }
        if (!current.isEmpty() || parts.isEmpty()) parts.add(current);
        return parts;
    }
}
