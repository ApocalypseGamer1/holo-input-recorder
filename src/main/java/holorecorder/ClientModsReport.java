package holorecorder;

import holorecorder.protocol.ClientModsPayload;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Mod list for data protection; sent only to a server that has declared the recorder protocol. */
final class ClientModsReport {
    private ClientModsReport() {}

    /**
     * Sends the report when {@code serverSpeaksProtocol} is true; otherwise builds nothing and sends nothing.
     * Returns whether it was sent.
     */
    static boolean sendIfSupported(BooleanSupplier serverSpeaksProtocol, Supplier<ClientModsPayload> report,
            Consumer<ClientModsPayload> sender) {
        if (!serverSpeaksProtocol.getAsBoolean()) return false;
        sender.accept(report.get());
        return true;
    }

    /** All loaded mods; top-level mods first so the entry cap drops bundled libraries before real mods. */
    static ClientModsPayload fromLoader() {
        FabricLoader loader = FabricLoader.getInstance();
        List<ModContainer> all = new ArrayList<>(loader.getAllMods());
        all.sort(Comparator.comparing((ModContainer m) -> m.getContainingMod().isPresent())
                .thenComparing(m -> m.getMetadata().getId()));
        List<ClientModsPayload.Mod> mods = new ArrayList<>(all.size());
        for (ModContainer m : all) {
            mods.add(new ClientModsPayload.Mod(m.getMetadata().getId(),
                    m.getMetadata().getVersion().getFriendlyString(), m.getMetadata().getName()));
        }
        String own = loader.getModContainer("holo_input_recorder")
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
        return ClientModsPayload.of(own, mods);
    }
}
