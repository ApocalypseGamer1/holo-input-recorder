package holorecorder.menu;

import holorecorder.protocol.MenuEntry;
import holorecorder.protocol.MenuOpenPayload;
import holorecorder.protocol.MenuSection;
import holorecorder.protocol.MenuUpdatePayload;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.TreeMap;

/** What the server last told the client about the menu on screen. Pure data; the screen draws it. */
public final class MenuState {
    public final int id;
    public final Component title;
    public final int rows;
    public final int backSlot;
    public final List<MenuSection> sections;
    private final TreeMap<Integer, MenuEntry> tiles = new TreeMap<>();
    private int revision;

    private MenuState(MenuOpenPayload p) {
        this.id = p.menuId();
        this.title = p.title();
        this.rows = Math.max(1, Math.min(6, p.rows()));
        this.backSlot = p.backSlot();
        this.sections = p.sections();
        for (MenuEntry e : p.entries()) put(e);
    }

    /** The state for an open payload, or null when this client does not understand its version. */
    public static MenuState of(MenuOpenPayload p) {
        return p.version() == MenuOpenPayload.VERSION ? new MenuState(p) : null;
    }

    private void put(MenuEntry e) {
        if (e.slot() < 0 || e.slot() >= rows * 9) return;
        if (e.isEmpty()) tiles.remove(e.slot());
        else tiles.put(e.slot(), e);
    }

    /** Applies an update; false (and no change) when it is for another menu or another version. */
    public boolean apply(MenuUpdatePayload p) {
        if (p.version() != MenuOpenPayload.VERSION || p.menuId() != id) return false;
        for (MenuEntry e : p.changed()) put(e);
        revision++;
        return true;
    }

    public MenuEntry tile(int slot) {
        return tiles.get(slot);
    }

    /** The tiles in slot order. */
    public List<MenuEntry> tiles() {
        return List.copyOf(tiles.values());
    }

    /** Grows with every update, so the screen knows when to lay out again. */
    public int revision() {
        return revision;
    }

    public boolean hasBack() {
        return backSlot >= 0 && tiles.containsKey(backSlot);
    }

    /** The heading for chest row {@code row}, or null. */
    public Component sectionOf(int row) {
        for (MenuSection s : sections) if (s.row() == row) return s.label();
        return null;
    }
}
