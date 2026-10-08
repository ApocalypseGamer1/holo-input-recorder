package holorecorder;

import holorecorder.protocol.ClientModsPayload;
import holorecorder.protocol.ClientModsV2Payload;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Mod list for data protection; sent only to a server that has declared the channel. */
final class ClientModsReport {
    private static final SecureRandom IDS = new SecureRandom();

    private ClientModsReport() {}

    /**
     * Sends the report when {@code serverAcceptsChannel} is true; otherwise builds nothing and sends nothing.
     * Returns whether it was sent.
     */
    static boolean sendIfSupported(BooleanSupplier serverAcceptsChannel, Supplier<ClientModsPayload> report,
            Consumer<ClientModsPayload> sender) {
        if (!serverAcceptsChannel.getAsBoolean()) return false;
        sender.accept(report.get());
        return true;
    }

    /** All loaded mods, top-level mods first (so the entry caps drop bundled libraries before real mods), then by id. */
    private static List<ModContainer> loadedMods() {
        List<ModContainer> all = new ArrayList<>(FabricLoader.getInstance().getAllMods());
        all.sort(Comparator.comparing((ModContainer m) -> m.getContainingMod().isPresent())
                .thenComparing(m -> m.getMetadata().getId()));
        return all;
    }

    /** The per-install id from the mod's config dir (see {@link InstallIdentity}); "" if it can't be read. */
    private static String installId() {
        try {
            return InstallIdentity.get(FabricLoader.getInstance().getConfigDir()
                    .resolve("holo-input-recorder").resolve("install-id.txt"));
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String ownVersion() {
        return FabricLoader.getInstance().getModContainer("holo_input_recorder")
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
    }

    /** Version 1: id, version and name of each mod. */
    static ClientModsPayload fromLoader() {
        List<ClientModsPayload.Mod> mods = new ArrayList<>();
        for (ModContainer m : loadedMods()) {
            mods.add(new ClientModsPayload.Mod(m.getMetadata().getId(),
                    m.getMetadata().getVersion().getFriendlyString(), m.getMetadata().getName()));
        }
        return ClientModsPayload.of(ownVersion(), mods);
    }

    /**
     * Version 2: version 1 plus the bundling parent, file size and SHA-512 of each mod's own jar (top-level jars only;
     * a bundled mod's bytes are inside its parent's hash). Reads every mod jar: call off the render thread.
     */
    static List<ClientModsV2Payload> fromLoaderV2() {
        List<ClientModsV2Payload.Mod> mods = new ArrayList<>();
        for (ModContainer m : loadedMods()) {
            String parent = "", sha = "";
            long size = -1;
            try {
                ModOrigin origin = m.getOrigin();
                if (origin.getKind() == ModOrigin.Kind.NESTED) {
                    parent = origin.getParentModId();
                } else if (origin.getKind() == ModOrigin.Kind.PATH && origin.getPaths().size() == 1) {
                    Path jar = origin.getPaths().get(0);
                    sha = ModFileHashes.sha512(jar);
                    if (!sha.isEmpty()) size = Files.size(jar);
                }
            } catch (Exception e) {
                // Unknown origin (development folders, unusual loaders): reported without a fingerprint.
            }
            if (parent.isEmpty()) parent = m.getContainingMod().map(c -> c.getMetadata().getId()).orElse("");
            mods.add(ClientModsV2Payload.capped(new ClientModsV2Payload.Mod(m.getMetadata().getId(),
                    m.getMetadata().getVersion().getFriendlyString(), m.getMetadata().getName(), parent, size, sha)));
        }
        return parts(ownVersion(), installId(), IDS.nextLong(), mods);
    }

    /** The report split into parts under the payload limit (at most {@link ClientModsV2Payload#MAX_PARTS}); every part
     *  carries the same reportId and install id. */
    static List<ClientModsV2Payload> parts(String recorderVersion, String installId, long reportId,
            List<ClientModsV2Payload.Mod> mods) {
        List<List<ClientModsV2Payload.Mod>> groups = ModReportPlan.split(mods, ClientModsReport::wireSize,
                ModReportPlan.PART_BUDGET, ClientModsV2Payload.MAX_MODS_PER_PART, ClientModsV2Payload.MAX_PARTS);
        List<ClientModsV2Payload> out = new ArrayList<>(groups.size());
        for (int i = 0; i < groups.size(); i++) {
            out.add(ClientModsV2Payload.of(recorderVersion, installId, reportId, i, groups.size(), groups.get(i)));
        }
        return out;
    }

    /** Encoded bytes of one entry (strings already capped). */
    static int wireSize(ClientModsV2Payload.Mod m) {
        return ModReportPlan.utf(m.id()) + ModReportPlan.utf(m.version()) + ModReportPlan.utf(m.name())
                + ModReportPlan.utf(m.parent()) + ModReportPlan.varLong(m.size() + 1L) + ModReportPlan.utf(m.sha512());
    }
}
