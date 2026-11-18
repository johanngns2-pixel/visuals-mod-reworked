package dev.visuals.gui;

import dev.visuals.VisualsMod;
import dev.visuals.config.ConfigManager;
import dev.visuals.config.OptionDef;
import dev.visuals.config.OptionRegistry;
import dev.visuals.config.Preset;
import dev.visuals.config.PresetIO;
import dev.visuals.config.PresetManager;
import dev.visuals.config.UndoStack;
import dev.visuals.config.TabDef;
import dev.visuals.config.def.GeneralOptions;
import dev.visuals.config.def.Tabs;
import dev.visuals.gui.preview.PreviewPanel;
import dev.visuals.gui.widgets.TabBar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Das Einstellungs-Menü. Layout: Reiter-Leiste links, Einstellungen in der Mitte, Vorschau rechts,
 * Suchleiste oben, Aktionsleiste unten.
 *
 * <p>Gezeichnet wird im "Immediate Mode": Beim Zeichnen sammelt {@link #hits} alle klickbaren Flächen,
 * {@link #mouseClicked} arbeitet sie von oben nach unten ab. Dadurch gibt es keine Widget-Klassen,
 * die zwischen Minecraft-Versionen brechen können.
 *
 * <p>Stand (Abschnitt 2): Layout, Reiter-Leiste, Kopfbereich mit Master-Schalter und Zurücksetzen,
 * Optionsliste mit Scrollen (Schalter sind bedienbar, andere Typen folgen mit den Widgets in Abschnitt 3),
 * Schließen mit Rückfrage. Suche, Vorschau, Presets und Rückgängig folgen in den Abschnitten 3 bis 5.
 */
public final class VisualsScreen extends Screen {
    private static final int TOP_H = 28;
    private static final int BOTTOM_H = 28;
    private static final int MIN_W_FOR_PREVIEW = 520;
    private static final int ROW_H = 20;
    private static final int GROUP_H = 22;

    private record Hit(int x, int y, int w, int h, Runnable action) {
    }

    private final Screen parent;
    private final List<Hit> hits = new ArrayList<>();
    private final Anim masterKnob = new Anim(0);
    private final OptionPanel panel = new OptionPanel();
    private final PreviewPanel preview = new PreviewPanel();
    private boolean previewFullscreen;
    private int listTop;
    private int listBottom;

    private TabBar tabBar;
    private String activeTab = Tabs.GENERAL;
    private int scroll;
    private int maxScroll;
    private boolean previewCollapsed;
    private boolean confirmClose;

    // Suche
    private record Result(String tabId, String optionId, String label) {
    }

    private final StringBuilder query = new StringBuilder();
    private boolean searchFocused;
    private int searchX;
    private int searchW;
    private final List<Hit> searchHits = new ArrayList<>();
    private String pendingJump;

    // Modales Fenster (Presets, Import/Export)
    private ModalDialog modal;

    public VisualsScreen(Screen parent) {
        super(Component.translatable("visuals.gui.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        OptionRegistry registry = VisualsMod.registry();
        tabBar = new TabBar(new ArrayList<>(registry.tabs()));
        tabBar.setHeight(this.height);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ Geometrie

    private boolean previewVisible() {
        return !previewCollapsed && this.width >= MIN_W_FOR_PREVIEW;
    }

    private int previewW() {
        return previewVisible() ? (int) (this.width * 0.35) : 0;
    }

    private int contentLeft() {
        return TabBar.COLLAPSED_W;
    }

    private int contentRight() {
        return this.width - previewW();
    }

    // ------------------------------------------------------------------ Zeichnen

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        hits.clear();
        searchHits.clear();
        VisualsMod.presets().tick();
        ConfigManager cfg = VisualsMod.config();
        OptionRegistry reg = VisualsMod.registry();
        TabDef tab = reg.tab(activeTab);

        this.renderBackground(g, mx, my, delta);
        if (previewFullscreen) {
            preview.renderSceneOnly(g, 0, 0, this.width, this.height);
            String hint = I18n.get("visuals.gui.preview.exitHint");
            g.fill(0, this.height - 16, this.width, this.height, 0x90000000);
            g.drawString(this.font, hint, this.width / 2 - this.font.width(hint) / 2, this.height - 12, Theme.TEXT);
            return;
        }
        g.fill(0, 0, this.width, this.height, Theme.OVERLAY);
        tabBar.setHeight(this.height);

        int left = contentLeft();
        int right = contentRight();
        int top = TOP_H;
        int bottom = this.height - BOTTOM_H;

        // Flächen
        g.fill(left, 0, this.width, this.height, Theme.PANEL);
        g.fill(left, top - 1, this.width, top, Theme.BORDER);
        g.fill(left, bottom, this.width, bottom + 1, Theme.BORDER);

        renderTopBar(g, mx, my, left, cfg);
        renderContent(g, mx, my, left, right, top, bottom, tab, cfg, reg);
        renderPreview(g, mx, my, right, top, bottom);
        renderBottomBar(g, mx, my, left, bottom, cfg);

        // Reiter-Leiste zuletzt, damit sie beim Aufklappen über dem Inhalt liegt
        tabBar.render(g, this.font, mx, my, activeTab);
        if (!confirmClose && modal == null) {
            panel.renderOverlay(g, this.font, mx, my, this.width, this.height);
            renderSearchResults(g, mx, my);
        }
        if (modal != null) {
            modal.render(g, this.font, mx, my, this.width, this.height);
        }

        if (confirmClose) {
            renderConfirm(g, mx, my);
        }
    }

    private void renderTopBar(GuiGraphics g, int mx, int my, int left, ConfigManager cfg) {
        // Notausschalter
        boolean on = cfg.get(GeneralOptions.MOD_ENABLED);
        String label = I18n.get(on ? "visuals.gui.killswitch.on" : "visuals.gui.killswitch.off");
        int bw = this.font.width(label) + 16;
        int bx = this.width - bw - 10;
        button(g, mx, my, bx, 4, bw, TOP_H - 8, label, on ? Theme.accent() : Theme.GRAY,
                () -> cfg.set(GeneralOptions.MOD_ENABLED, !on));

        // Aktives Preset
        PresetManager presets = VisualsMod.presets();
        String name = presets.activeName().isEmpty() ? "-" : presetLabel(presets.activeName(), presets.activeIsBuiltin());
        String presetText = String.format(I18n.get("visuals.gui.presetActive"), name);
        int px = bx - 12 - this.font.width(presetText);

        // Suchleiste
        searchX = left + 10;
        searchW = Math.max(80, Math.min(260, px - searchX - 10));
        g.fill(searchX, 5, searchX + searchW, TOP_H - 5, searchFocused ? 0xFF2C2C34 : Theme.ROW);
        if (searchFocused) {
            g.fill(searchX, TOP_H - 6, searchX + searchW, TOP_H - 5, Theme.accent());
        }
        String shown = query.length() == 0 && !searchFocused ? I18n.get("visuals.gui.search")
                : query + (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        g.drawString(this.font, this.font.plainSubstrByWidth(shown, searchW - 12), searchX + 6, 10,
                query.length() == 0 && !searchFocused ? Theme.TEXT_DISABLED : Theme.TEXT);

        if (px > searchX + searchW + 8) {
            g.drawString(this.font, presetText, px, 10, Theme.TEXT_DIM);
        }
    }

    private static String presetLabel(String name, boolean builtin) {
        return builtin ? I18n.get("visuals.preset." + name) : name;
    }

    // ------------------------------------------------------------------ Suche

    private List<Result> searchResults() {
        List<Result> out = new ArrayList<>();
        String q = query.toString().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return out;
        }
        OptionRegistry reg = VisualsMod.registry();
        for (TabDef tab : reg.tabs()) {
            String tabName = I18n.get(tab.nameKey());
            if (tabName.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(new Result(tab.id(), null, tabName));
            }
            for (OptionDef<?> def : reg.optionsOf(tab.id())) {
                if (def.isMaster()) {
                    continue;
                }
                String name = I18n.get(def.nameKey());
                String tip = I18n.get(def.tooltipKey());
                if (name.toLowerCase(Locale.ROOT).contains(q) || (!tip.equals(def.tooltipKey())
                        && tip.toLowerCase(Locale.ROOT).contains(q))) {
                    out.add(new Result(tab.id(), def.id(), tabName + " \u203A " + name));
                }
            }
        }
        return out.size() > 8 ? out.subList(0, 8) : out;
    }

    private void renderSearchResults(GuiGraphics g, int mx, int my) {
        if (!searchFocused || query.length() == 0) {
            return;
        }
        List<Result> results = searchResults();
        int rowH = 16;
        int h = Math.max(1, results.size()) * rowH + 4;
        int y = TOP_H - 2;
        g.fill(searchX - 1, y - 1, searchX + searchW + 1, y + h + 1, Theme.BORDER);
        g.fill(searchX, y, searchX + searchW, y + h, Theme.PANEL_LIGHT);
        if (results.isEmpty()) {
            g.drawString(this.font, I18n.get("visuals.gui.search.none"), searchX + 6, y + 6, Theme.TEXT_DISABLED);
            return;
        }
        for (int i = 0; i < results.size(); i++) {
            Result r = results.get(i);
            int ry = y + 2 + i * rowH;
            boolean hov = Theme.inside(mx, my, searchX, ry, searchW, rowH);
            if (hov) {
                g.fill(searchX + 1, ry, searchX + searchW - 1, ry + rowH, Theme.ROW_HOVER);
            }
            g.drawString(this.font, this.font.plainSubstrByWidth(r.label(), searchW - 12), searchX + 6, ry + 4, Theme.TEXT);
            searchHits.add(new Hit(searchX, ry, searchW, rowH, () -> jumpTo(r)));
        }
    }

    private void jumpTo(Result r) {
        selectTab(r.tabId());
        pendingJump = r.optionId();
        query.setLength(0);
        searchFocused = false;
    }

    private void renderContent(GuiGraphics g, int mx, int my, int left, int right, int top, int bottom,
                               TabDef tab, ConfigManager cfg, OptionRegistry reg) {
        int pad = 12;
        int x = left + pad;
        int w = right - left - pad * 2;

        // Kopf: Titel, Beschreibung, Master-Schalter, Zurücksetzen
        g.drawString(this.font, I18n.get(tab.nameKey()), x, top + 10, Theme.TEXT);
        List<net.minecraft.util.FormattedCharSequence> desc =
                this.font.split(Component.translatable(tab.descKey()), Math.max(60, w - 150));
        int dy = top + 24;
        for (var line : desc) {
            g.drawString(this.font, line, x, dy, Theme.TEXT_DIM);
            dy += 10;
        }
        int headerH = (dy - top) + 8;

        int rx = right - pad;
        String reset = I18n.get("visuals.gui.resetTab");
        int rw = this.font.width(reset) + 14;
        rx -= rw;
        button(g, mx, my, rx, top + 8, rw, 16, reset, Theme.GRAY,
                () -> VisualsMod.undo().group(() -> cfg.resetTab(activeTab)));
        if (tab.hasMaster()) {
            OptionDef<Boolean> master = reg.masterOf(tab.id()).orElseThrow();
            boolean on = cfg.get(master);
            int sw = 30;
            rx -= sw + 10;
            toggle(g, rx, top + 9, on, masterKnob, true);
            hits.add(new Hit(rx, top + 9, sw, 14, () -> cfg.set(master, !on)));
        }

        // Optionsliste
        listTop = top + headerH;
        listBottom = bottom;
        g.fill(x, listTop - 1, x + w, listTop, Theme.BORDER);
        g.enableScissor(left, listTop, right, bottom);
        int startY = listTop - scroll + 6;
        int endY = panel.render(g, this.font, mx, my, x, startY, w, listTop, bottom, tab.id());
        g.disableScissor();
        maxScroll = Math.max(0, (endY - startY) + 12 - (bottom - listTop));
        if (pendingJump != null) {
            Integer off = panel.offsetOf(pendingJump);
            if (off != null) {
                scroll = off - 30;
                panel.highlight(pendingJump);
                pendingJump = null;
            }
        }
        scroll = Math.max(0, Math.min(maxScroll, scroll));
    }

    private void renderPreview(GuiGraphics g, int mx, int my, int right, int top, int bottom) {
        if (!previewVisible()) {
            return;
        }
        g.fill(right, top, this.width, bottom, Theme.PANEL_LIGHT);
        g.fill(right, top, right + 1, bottom, Theme.BORDER);
        preview.render(g, this.font, mx, my, right + 1, top, this.width - right - 1, bottom - top);
        if (preview.consumeFullscreenRequest()) {
            previewFullscreen = true;
        }
    }

    private void renderBottomBar(GuiGraphics g, int mx, int my, int left, int bottom, ConfigManager cfg) {
        int y = bottom + 4;
        int h = BOTTOM_H - 8;
        int x = left + 10;
        PresetManager presets = VisualsMod.presets();

        String presetBtn = I18n.get("visuals.gui.preset") + " \u25BE";
        x = bar(g, mx, my, x, y, h, presetBtn, Theme.accent(), this::openPresetMenu);
        x = bar(g, mx, my, x, y, h, I18n.get("visuals.gui.preset.save"), Theme.GRAY, this::openSavePreset);
        x = bar(g, mx, my, x, y, h, I18n.get("visuals.gui.io"), Theme.GRAY, this::openIoMenu);
        bar(g, mx, my, x, y, h, I18n.get("visuals.gui.resetAll"), Theme.GRAY,
                () -> VisualsMod.undo().group(cfg::resetAll));

        String close = I18n.get("visuals.gui.close");
        String apply = I18n.get("visuals.gui.apply");
        int cw = this.font.width(close) + 20;
        int aw = this.font.width(apply) + 20;
        int cx = this.width - cw - 10;
        int ax = cx - aw - 8;
        button(g, mx, my, cx, y, cw, h, close, Theme.GRAY, this::requestClose);
        button(g, mx, my, ax, y, aw, h, apply, Theme.accent(), cfg::commit);

        if (this.width >= MIN_W_FOR_PREVIEW) {
            String label = I18n.get(previewCollapsed ? "visuals.gui.previewShow" : "visuals.gui.previewHide");
            int pw = this.font.width(label) + 16;
            int px = ax - pw - 8;
            if (px > x + 20) {
                button(g, mx, my, px, y, pw, h, label, Theme.GRAY, () -> previewCollapsed = !previewCollapsed);
            }
        }
    }

    /** Zeichnet einen Knopf der unteren Leiste und liefert das x für den nächsten. */
    private int bar(GuiGraphics g, int mx, int my, int x, int y, int h, String label, int color, Runnable action) {
        int w = this.font.width(label) + 16;
        button(g, mx, my, x, y, w, h, label, color, action);
        return x + w + 6;
    }

    // ------------------------------------------------------------------ Presets, Import und Export

    private void openPresetMenu() {
        PresetManager presets = VisualsMod.presets();
        List<ModalDialog.Button> entries = new ArrayList<>();
        for (Preset p : presets.all()) {
            boolean active = p.name().equals(presets.activeName()) && p.builtin() == presets.activeIsBuiltin();
            String label = (active ? "\u2713 " : "") + presetLabel(p.name(), p.builtin());
            entries.add(new ModalDialog.Button(label, active ? Theme.accent() : Theme.GRAY, () -> presets.apply(p)));
        }
        entries.add(new ModalDialog.Button(I18n.get("visuals.gui.cancel"), Theme.GRAY, () -> {
        }));
        modal = ModalDialog.menu(I18n.get("visuals.gui.preset.choose"), entries, () -> {
        });
    }

    private void openSavePreset() {
        modal = ModalDialog.input(I18n.get("visuals.gui.preset.save"), I18n.get("visuals.gui.preset.save.prompt"), 32,
                I18n.get("visuals.gui.confirm.save"), name -> {
                    boolean ok = VisualsMod.presets().saveCurrent(name);
                    info(ok ? I18n.get("visuals.gui.preset.saved") : I18n.get("visuals.gui.preset.saveFailed"));
                }, I18n.get("visuals.gui.cancel"), () -> {
                });
    }

    private void info(String... lines) {
        modal = ModalDialog.message(I18n.get("visuals.gui.title"), List.of(lines), I18n.get("visuals.gui.ok"), () -> {
        });
    }

    private Path dataDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(VisualsMod.MOD_ID);
    }

    private void openIoMenu() {
        List<ModalDialog.Button> entries = new ArrayList<>();
        entries.add(new ModalDialog.Button(I18n.get("visuals.gui.io.copyCode"), Theme.GRAY, this::exportCode));
        entries.add(new ModalDialog.Button(I18n.get("visuals.gui.io.exportFile"), Theme.GRAY, this::exportFile));
        entries.add(new ModalDialog.Button(I18n.get("visuals.gui.io.importCode"), Theme.GRAY, () ->
                importText(Minecraft.getInstance().keyboardHandler.getClipboard())));
        entries.add(new ModalDialog.Button(I18n.get("visuals.gui.io.importFile"), Theme.GRAY, this::importFile));
        entries.add(new ModalDialog.Button(I18n.get("visuals.gui.cancel"), Theme.GRAY, () -> {
        }));
        modal = ModalDialog.menu(I18n.get("visuals.gui.io.title"), entries, () -> {
        });
    }

    private JsonObject currentExport() {
        return PresetIO.toJson("", VisualsMod.presets().currentModified(), VisualsMod.registry());
    }

    private void exportCode() {
        Minecraft.getInstance().keyboardHandler.setClipboard(PresetIO.toCode(currentExport()));
        info(I18n.get("visuals.gui.io.copied"));
    }

    private void exportFile() {
        Path dir = dataDir().resolve("exports");
        Path file = dir.resolve("visuals-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json");
        try {
            Files.createDirectories(dir);
            Files.writeString(file, PresetIO.toPrettyJson(currentExport()), StandardCharsets.UTF_8);
            info(String.format(I18n.get("visuals.gui.io.exported"), file.toAbsolutePath()));
        } catch (IOException e) {
            info(I18n.get("visuals.gui.io.writeFailed"));
        }
    }

    private void importFile() {
        Path file = dataDir().resolve("import.json");
        if (!Files.isRegularFile(file)) {
            info(String.format(I18n.get("visuals.gui.io.noFile"), file.toAbsolutePath()));
            return;
        }
        try {
            importText(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            info(I18n.get("visuals.gui.io.invalid"));
        }
    }

    /** Prüft den Text und zeigt eine Vorschau der Änderungen, bevor etwas angewendet wird. */
    private void importText(String text) {
        OptionRegistry reg = VisualsMod.registry();
        PresetIO.Parsed parsed;
        try {
            parsed = PresetIO.fromJson(PresetIO.parseText(text), reg);
        } catch (IllegalArgumentException e) {
            info(I18n.get("visuals.gui.io.invalid"));
            return;
        }
        List<UndoStack.Change> diff = VisualsMod.presets().diff(parsed.values());
        if (diff.isEmpty()) {
            info(I18n.get("visuals.gui.io.nothing"));
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add(String.format(I18n.get("visuals.gui.io.preview"), diff.size()));
        int shown = Math.min(8, diff.size());
        for (int i = 0; i < shown; i++) {
            UndoStack.Change c = diff.get(i);
            OptionDef<?> def = reg.get(c.id());
            lines.add(I18n.get(def.nameKey()) + ": " + OptionPanel.valueText(def, c.oldValue()) + " \u2192 "
                    + OptionPanel.valueText(def, c.newValue()));
        }
        if (diff.size() > shown) {
            lines.add(String.format(I18n.get("visuals.gui.io.more"), diff.size() - shown));
        }
        if (parsed.skipped() > 0) {
            lines.add(String.format(I18n.get("visuals.gui.io.skipped"), parsed.skipped()));
        }
        if (parsed.corrected() > 0) {
            lines.add(String.format(I18n.get("visuals.gui.io.corrected"), parsed.corrected()));
        }
        Map<String, Object> values = parsed.values();
        modal = ModalDialog.confirm(I18n.get("visuals.gui.io.title"), lines, I18n.get("visuals.gui.io.apply"),
                () -> VisualsMod.presets().applyValues(values), I18n.get("visuals.gui.cancel"), () -> {
                });
    }

    private void renderConfirm(GuiGraphics g, int mx, int my) {
        hits.clear(); // modal: nur noch die Rückfrage ist klickbar
        g.fill(0, 0, this.width, this.height, 0xB0000000);
        int w = 260;
        int h = 80;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        g.fill(x, y, x + w, y + h, Theme.PANEL_LIGHT);
        g.fill(x, y, x + w, y + 1, Theme.accent());
        String title = I18n.get("visuals.gui.confirm.title");
        g.drawString(this.font, title, x + w / 2 - this.font.width(title) / 2, y + 14, Theme.TEXT);

        ConfigManager cfg = VisualsMod.config();
        String save = I18n.get("visuals.gui.confirm.save");
        String discard = I18n.get("visuals.gui.confirm.discard");
        String cancel = I18n.get("visuals.gui.confirm.cancel");
        int bw = 76;
        int by = y + h - 28;
        button(g, mx, my, x + 8, by, bw, 18, save, Theme.accent(), () -> {
            cfg.commit();
            closeNow();
        });
        button(g, mx, my, x + 8 + bw + 6, by, bw, 18, discard, Theme.GRAY, () -> {
            cfg.revert();
            closeNow();
        });
        button(g, mx, my, x + 8 + (bw + 6) * 2, by, bw - 4, 18, cancel, Theme.GRAY, () -> confirmClose = false);
    }

    // ------------------------------------------------------------------ Bausteine

    private void button(GuiGraphics g, int mx, int my, int x, int y, int w, int h, String label, int color,
                        Runnable action) {
        boolean hovered = Theme.inside(mx, my, x, y, w, h);
        int bg = Theme.mix(Theme.ROW, color, hovered ? 0.55 : 0.25);
        g.fill(x, y, x + w, y + h, bg);
        g.drawString(this.font, label, x + (w - this.font.width(label)) / 2, y + (h - 8) / 2, Theme.TEXT);
        hits.add(new Hit(x, y, w, h, action));
    }

    /** Gleitender Schalter (30 x 14). {@code knob} darf null sein, dann springt er. */
    private void toggle(GuiGraphics g, int x, int y, boolean on, Anim knob, boolean enabled) {
        double t = knob != null ? knob.step(on ? 1 : 0) : (on ? 1 : 0);
        int track = enabled ? Theme.mix(Theme.GRAY, Theme.accent(), t) : Theme.mix(0xFF3A3A40, 0xFF55453A, t);
        g.fill(x, y, x + 30, y + 14, track);
        int kx = x + 2 + (int) Math.round(t * 16);
        g.fill(kx, y + 2, kx + 10, y + 12, enabled ? 0xFFF4F4F6 : 0xFF8A8A90);
    }

    // ------------------------------------------------------------------ Eingaben

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int button = event.button();
        double mx = event.x();
        double my = event.y();
        boolean shift = event.hasShiftDown();
        if (modal != null) {
            if (button == 0) {
                runModal(modal.click(mx, my));
            }
            return true;
        }
        if (previewFullscreen) {
            previewFullscreen = false;
            return true;
        }
        if (confirmClose) {
            if (button == 0) {
                runHit(mx, my);
            }
            return true;
        }
        if (button == 0) {
            for (int i = searchHits.size() - 1; i >= 0; i--) {
                Hit hit = searchHits.get(i);
                if (Theme.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
                    hit.action().run();
                    return true;
                }
            }
            if (Theme.inside(mx, my, searchX, 5, searchW, TOP_H - 10)) {
                panel.closeAll();
                searchFocused = true;
                return true;
            }
            searchFocused = false;
        }
        if (panel.popupOpen() || panel.editing()) {
            if (panel.mouseClicked(mx, my, button, shift)) {
                return true;
            }
        }
        if (button == 0) {
            if (tabBar.contains(mx, my)) {
                String id = tabBar.clicked(mx, my);
                if (id != null) {
                    selectTab(id);
                }
                return true;
            }
            if (runHit(mx, my)) {
                return true;
            }
        }
        if (button == 0 && previewVisible() && mx >= contentRight() && preview.mouseClicked(mx, my)) {
            return true;
        }
        if (my >= listTop && my < listBottom && mx >= contentLeft() && mx < contentRight()) {
            if (panel.mouseClicked(mx, my, button, shift)) {
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void runModal(Runnable action) {
        if (action != null) {
            modal = null;
            action.run();
        }
    }

    private boolean runHit(double mx, double my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (Theme.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
                hit.action().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (panel.mouseDragged(event.x(), event.hasShiftDown()) || preview.mouseDragged(event.x())) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        panel.mouseReleased();
        preview.mouseReleased();
        return super.mouseReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (modal != null) {
            modal.charTyped(event.codepoint());
            return true;
        }
        if (searchFocused) {
            if (query.length() < 40 && event.codepoint() >= 32) {
                query.append(Character.toChars(event.codepoint()));
            }
            return true;
        }
        if (!confirmClose && panel.charTyped(event.codepoint())) {
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (confirmClose || modal != null) {
            return true;
        }
        if (tabBar.contains(mx, my)) {
            tabBar.scrolled(scrollY);
        } else {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (scrollY * 18)));
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        boolean ctrl = event.hasControlDown();
        if (modal != null) {
            runModal(modal.key(key, ctrl));
            return true;
        }
        if (previewFullscreen) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                previewFullscreen = false;
            }
            return true;
        }
        if (searchFocused && !confirmClose) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                query.setLength(0);
            } else if (key == GLFW.GLFW_KEY_BACKSPACE && query.length() > 0) {
                query.setLength(query.length() - 1);
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                List<Result> results = searchResults();
                if (!results.isEmpty()) {
                    jumpTo(results.get(0));
                }
            }
            return true;
        }
        if (!confirmClose && panel.keyPressed(key, ctrl)) {
            return true;
        }
        if (!confirmClose && ctrl && key == GLFW.GLFW_KEY_F) {
            panel.closeAll();
            searchFocused = true;
            return true;
        }
        if (!confirmClose && ctrl && (key == GLFW.GLFW_KEY_Z || key == GLFW.GLFW_KEY_Y)) {
            UndoStack undo = VisualsMod.undo();
            VisualsMod.presets().finishNow();
            if (key == GLFW.GLFW_KEY_Y || event.hasShiftDown()) {
                undo.redo();
            } else {
                undo.undo();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (confirmClose) {
                confirmClose = false;
            } else {
                requestClose();
            }
            return true;
        }
        if (confirmClose) {
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) {
            selectTab(tabBar.neighbour(activeTab, key == GLFW.GLFW_KEY_DOWN ? 1 : -1));
            return true;
        }
        return super.keyPressed(event);
    }

    private void selectTab(String id) {
        if (!id.equals(activeTab)) {
            activeTab = id;
            scroll = 0;
            panel.closeAll();
        }
    }

    // ------------------------------------------------------------------ Schließen

    private void requestClose() {
        if (VisualsMod.config().isDirty()) {
            confirmClose = true;
        } else {
            closeNow();
        }
    }

    private void closeNow() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void removed() {
        VisualsMod.presets().finishNow();
        super.removed();
    }

    @Override
    public void onClose() {
        requestClose();
    }
}
