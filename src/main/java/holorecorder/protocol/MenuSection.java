package holorecorder.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/** A heading drawn above chest row {@code row} (0-based) in a custom menu. */
public record MenuSection(int row, Component label) {
    public static final StreamCodec<ByteBuf, List<MenuSection>> LIST_CODEC = StreamCodec.of(
            (buf, list) -> {
                ByteBufCodecs.VAR_INT.encode(buf, list.size());
                for (MenuSection s : list) {
                    ByteBufCodecs.VAR_INT.encode(buf, s.row);
                    ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buf, s.label);
                }
            },
            buf -> {
                int n = ByteBufCodecs.VAR_INT.decode(buf);
                if (n < 0 || n > 6) throw new IllegalArgumentException("sections " + n);
                ArrayList<MenuSection> out = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    out.add(new MenuSection(ByteBufCodecs.VAR_INT.decode(buf), ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buf)));
                }
                return List.copyOf(out);
            });
}
