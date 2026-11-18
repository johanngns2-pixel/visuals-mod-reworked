package dev.visuals.gui;

import dev.visuals.VisualsMod;
import dev.visuals.config.ConfigManager;
import dev.visuals.config.OptionDef;
import dev.visuals.config.OptionRegistry;
import dev.visuals.config.OptionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Zeichnet die Optionsliste eines Reiters und bedient alle Widget-Typen: Schalter, Regler mit Zahlenfeld
 * (Shift = Feineinstellung), Dropdown, Farbwähler (HSV und Hex), Textfeld. Dazu Tooltips, Zurücksetzen-Pfeil,
 * Rechtsklick-Menü (Zurücksetzen, Wert kopieren, Wert einfügen) und Markierung ungültiger Eingaben.
 *
 * <p>Immediate Mode wie {@link VisualsScreen}: beim Zeichnen werden die klickbaren Flächen gesammelt.
 */
public final class OptionPanel {
    public static final int ROW_H = 22;
    public static final int GROUP_H = 22;
    private static final int RESET_W = 16;
    private static final int POPUP_ROW_H = 16;

    @FunctionalInterface
    private interface Click {
        void on(double mx, double my, int button, boolean shift);
    }

    @FunctionalInterface
    private interface Drag {
        void on(double mx, boolean shift);
    }

    private record Hit(int x, int y, int w, int h, Click click) {
    }

    private final List<Hit> hits = new ArrayList<>();
    private final List<Hit> popupHits = new ArrayList<>();
    private final List<Hit> ctxHits = new ArrayList<>();
    private Drag activeDrag;
    private int clipTop;
    private int clipBottom;

    // Textbearbeitung
    private String editId;
    private OptionDef<?> editDef;
    private boolean editHex;
    private final StringBuilder editBuf = new StringBuilder();

    // Popups
    private String dropdownId;
    private int dropX;
    private int dropY;
    private int dropW;
    private String colorId;
    private int colorX;
    private int colorY;
    private final float[] hsv = new float[3];
    private String ctxId;
    private int ctxX;
    private int ctxY;

    private final Map<String, Long> invalid = new HashMap<>();
    private String hoverId;
    private long hoverSince;
    private final Map<String, Integer> rowOffsets = new HashMap<>();
    private String highlightId;
    private long highlightUntil;

    // ------------------------------------------------------------------ Zustand

    public boolean popupOpen() {
        return dropdownId != null || colorId != null || ctxId != null;
    }

    public boolean editing() {
        return editId != null;
    }

    /** True, wenn Esc und Pfeiltasten dem Panel gehören und nicht dem Screen. */
    public boolean blocksInput() {
        return popupOpen() || editing();
    }

    /** Abstand der Zeile vom Listenanfang (nach dem letzten Zeichnen), oder {@code null}. */
    public Integer offsetOf(String id) {
        return rowOffsets.get(id);
    }

    /** Hebt eine Option kurz hervor (Suche). */
    public void highlight(String id) {
        highlightId = id;
        highlightUntil = System.currentTimeMillis() + 1800;
    }

    public static String valueText(OptionDef<?> def, Object value) {
        return toText(def, value);
    }

    public void closeAll() {
        editId = null;
        dropdownId = null;
        colorId = null;
        ctxId = null;
        activeDrag = null;
    }

    // ------------------------------------------------------------------ Liste zeichnen

    /** Zeichnet alle Gruppen und Optionen ab {@code y} und liefert das y-Ende des Inhalts. */
    public int render(GuiGraphics g, Font font, int mx, int my, int x, int y, int w,
                      int clipTop, int clipBottom, String tabId) {
        hits.clear();
        ctxHits.clear();
        this.clipTop = clipTop;
        this.clipBottom = clipBottom;
        ConfigManager cfg = VisualsMod.config();
        OptionRegistry reg = VisualsMod.registry();
        boolean popup = popupOpen();
        String hovered = null;
        int originY = y;

        for (String group : reg.groupsOf(tabId)) {
            g.drawString(font, I18n.get("visuals.group." + tabId + "." + group), x, y + 7, Theme.accent());
            y += GROUP_H;
            for (OptionDef<?> def : reg.optionsOf(tabId)) {
                if (!def.groupId().equals(group)) {
                    continue;
                }
                rowOffsets.put(def.id(), y - originY);
                if (y + ROW_H > clipTop && y < clipBottom) {
                    if (row(g, font, mx, my, x, y, w, def, cfg, popup)) {
                        hovered = def.id();
                    }
                }
                y += ROW_H + 2;
            }
            y += 6;
        }
        if (!Objects.equals(hovered, hoverId)) {
            hoverId = hovered;
            hoverSince = System.currentTimeMillis();
        }
        return y;
    }

    private boolean row(GuiGraphics g, Font font, int mx, int my, int x, int y, int w, OptionDef<?> def,
                        ConfigManager cfg, boolean popup) {
        String id = def.id();
        boolean enabled = cfg.isOptionEnabled(id);
        boolean hovered = enabled && !popup && my >= clipTop && my < clipBottom && Theme.inside(mx, my, x, y, w, ROW_H);
        g.fill(x, y, x + w, y + ROW_H, hovered ? Theme.ROW_HOVER : Theme.ROW);
        boolean modified = cfg.isModified(id);
        if (modified) {
            g.fill(x, y, x + 2, y + ROW_H, Theme.accent());
        }
        if (id.equals(highlightId) && System.currentTimeMillis() < highlightUntil) {
            long left = highlightUntil - System.currentTimeMillis();
            int a = (int) (0x50 * Math.min(1.0, left / 600.0));
            g.fill(x, y, x + w, y + ROW_H, (a << 24) | (Theme.accent() & 0xFFFFFF));
            outline(g, x, y, w, ROW_H, Theme.accent());
        }
        if (invalid.getOrDefault(id, 0L) > System.currentTimeMillis()) {
            outline(g, x, y, w, ROW_H, 0xFFE04848);
        }
        Object value = cfg.getRaw(id);
        if (value == null) {
            value = def.defaultValue();
        }

        int controlRight = x + w - 8 - RESET_W;
        int controlLeft;
        int textColor = enabled ? Theme.TEXT : Theme.TEXT_DISABLED;
        int dimColor = enabled ? Theme.TEXT_DIM : Theme.TEXT_DISABLED;
        long now = System.currentTimeMillis();

        switch (def.type()) {
            case TOGGLE -> {
                boolean on = Boolean.TRUE.equals(value);
                controlLeft = controlRight - 30;
                drawToggle(g, controlLeft, y + 4, on ? 1.0 : 0.0, enabled);
                add(x, y, w, ROW_H, enabled, (cx, cy, b, s) -> {
                    if (b == 0) {
                        cfg.set(id, !on);
                    }
                });
            }
            case SLIDER, INT_SLIDER -> {
                double v = ((Number) value).doubleValue();
                int valueW = 50;
                int trackW = 76;
                int valueX = controlRight - valueW;
                int trackX = valueX - trackW - 6;
                controlLeft = trackX;
                double frac = (v - def.min()) / (def.max() - def.min());
                frac = Math.max(0, Math.min(1, frac));
                g.fill(trackX, y + 10, trackX + trackW, y + 12, Theme.BORDER);
                g.fill(trackX, y + 10, trackX + (int) Math.round(frac * trackW), y + 12,
                        enabled ? Theme.accent() : Theme.GRAY);
                int kx = trackX + (int) Math.round(frac * (trackW - 4));
                g.fill(kx, y + 6, kx + 4, y + 16, enabled ? 0xFFF4F4F6 : 0xFF8A8A90);
                add(trackX - 2, y + 3, trackW + 4, ROW_H - 6, enabled, (cx, cy, b, s) -> {
                    if (b == 0) {
                        Drag d = (m, sh) -> slide(def, trackX, trackW, m, sh);
                        activeDrag = d;
                        d.on(cx, s);
                    }
                });
                boolean editingThis = id.equals(editId) && !editHex;
                g.fill(valueX, y + 3, valueX + valueW, y + ROW_H - 3, editingThis ? 0xFF3A3A44 : Theme.BORDER);
                String shown = editingThis ? editText(now) : numberText(def, v) + (def.unit().isEmpty() ? "" : " " + def.unit());
                g.drawString(font, font.plainSubstrByWidth(shown, valueW - 6), valueX + 4, y + 7,
                        editingThis ? Theme.TEXT : dimColor);
                add(valueX, y + 3, valueW, ROW_H - 6, enabled, (cx, cy, b, s) -> {
                    if (b == 0) {
                        startEdit(def, false, numberText(def, v));
                    }
                });
            }
            case DROPDOWN -> {
                int boxW = 112;
                controlLeft = controlRight - boxW;
                g.fill(controlLeft, y + 3, controlLeft + boxW, y + ROW_H - 3, Theme.BORDER);
                String shown = I18n.get(def.choiceKey(String.valueOf(value)));
                g.drawString(font, font.plainSubstrByWidth(shown, boxW - 18), controlLeft + 5, y + 7, textColor);
                g.drawString(font, "v", controlLeft + boxW - 11, y + 7, dimColor);
                int boxX = controlLeft;
                add(controlLeft, y + 3, boxW, ROW_H - 6, enabled, (cx, cy, b, s) -> {
                    if (b == 0) {
                        closeAll();
                        dropdownId = id;
                        dropX = boxX;
                        dropY = y + ROW_H - 3;
                        dropW = boxW;
                    }
                });
            }
            case COLOR -> {
                int boxW = 96;
                controlLeft = controlRight - boxW;
                int argb = (Integer) value;
                g.fill(controlLeft, y + 3, controlLeft + boxW, y + ROW_H - 3, Theme.BORDER);
                g.fill(controlLeft + 3, y + 5, controlLeft + 25, y + ROW_H - 5, 0xFF000000 | argb);
                g.drawString(font, String.format("#%06X", argb & 0xFFFFFF), controlLeft + 30, y + 7, textColor);
                int boxX = controlLeft;
                add(controlLeft, y + 3, boxW, ROW_H - 6, enabled, (cx, cy, b, s) -> {
                    if (b == 0) {
                        closeAll();
                        colorId = id;
                        colorX = boxX;
                        colorY = y + ROW_H - 3;
                        rgbToHsv(argb, hsv);
                    }
                });
            }
            default -> { // TEXT
                int boxW = 120;
                controlLeft = controlRight - boxW;
                boolean editingThis = id.equals(editId);
                g.fill(controlLeft, y + 3, controlLeft + boxW, y + ROW_H - 3, editingThis ? 0xFF3A3A44 : Theme.BORDER);
                String shown = editingThis ? editText(now) : String.valueOf(value);
                g.drawString(font, font.plainSubstrByWidth(shown, boxW - 8), controlLeft + 4, y + 7,
                        editingThis ? Theme.TEXT : dimColor);
                add(controlLeft, y + 3, boxW, ROW_H - 6, enabled, (cx, cy, b, s) -> {
                    if (b == 0) {
                        startEdit(def, false, String.valueOf(cfg.getRaw(id)));
                    }
                });
            }
        }

        // Name
        String name = font.plainSubstrByWidth(I18n.get(def.nameKey()), Math.max(20, controlLeft - (x + 8) - 8));
        g.drawString(font, name, x + 8, y + 7, textColor);

        // Rechtsklick-Menü für die ganze Zeile (vor den Steuerelementen registriert, damit diese Vorrang haben)
        // wurde oben bereits über add() angelegt; hier nur der Zurücksetzen-Pfeil
        if (modified && enabled) {
            int rx = x + w - 8 - RESET_W + 2;
            boolean overReset = !popup && Theme.inside(mx, my, rx, y + 3, RESET_W - 2, ROW_H - 6);
            g.drawString(font, "\u21BA", rx + 2, y + 7, overReset ? Theme.accent() : Theme.TEXT_DIM);
            add(rx, y + 3, RESET_W - 2, ROW_H - 6, true, (cx, cy, b, s) -> {
                if (b == 0) {
                    cfg.reset(id);
                }
            });
        }
        // Rechtsklick auf die Zeile
        if (enabled) {
            if (y + ROW_H > clipTop && y < clipBottom) {
                ctxHits.add(new Hit(x, y, w, ROW_H, (cx, cy, b, s) -> {
                    closeAll();
                    ctxId = id;
                    ctxX = (int) cx;
                    ctxY = (int) cy;
                }));
            }
        }
        return hovered;
    }

    private void add(int x, int y, int w, int h, boolean enabled, Click click) {
        if (enabled && y + h > clipTop && y < clipBottom) {
            hits.add(new Hit(x, y, w, h, click));
        }
    }

    // ------------------------------------------------------------------ Overlays

    public void renderOverlay(GuiGraphics g, Font font, int mx, int my, int sw, int sh) {
        popupHits.clear();
        ConfigManager cfg = VisualsMod.config();
        OptionRegistry reg = VisualsMod.registry();

        if (dropdownId != null) {
            OptionDef<?> def = reg.get(dropdownId);
            if (def != null) {
                renderDropdown(g, font, mx, my, sh, def, cfg);
            }
        }
        if (colorId != null) {
            OptionDef<?> def = reg.get(colorId);
            if (def != null) {
                renderColor(g, font, mx, my, sw, sh, def, cfg);
            }
        }
        if (ctxId != null) {
            OptionDef<?> def = reg.get(ctxId);
            if (def != null) {
                renderContext(g, font, mx, my, sw, sh, def, cfg);
            }
        }
        renderTooltip(g, font, mx, my, sw, sh, reg);
    }

    private void renderDropdown(GuiGraphics g, Font font, int mx, int my, int sh, OptionDef<?> def, ConfigManager cfg) {
        List<String> choices = def.choices();
        int h = choices.size() * POPUP_ROW_H + 4;
        int y = Math.min(dropY, sh - h - 2);
        g.fill(dropX - 1, y - 1, dropX + dropW + 1, y + h + 1, Theme.BORDER);
        g.fill(dropX, y, dropX + dropW, y + h, Theme.PANEL_LIGHT);
        Object current = cfg.getRaw(def.id());
        for (int i = 0; i < choices.size(); i++) {
            String choice = choices.get(i);
            int ry = y + 2 + i * POPUP_ROW_H;
            boolean hov = Theme.inside(mx, my, dropX, ry, dropW, POPUP_ROW_H);
            boolean sel = choice.equals(current);
            if (hov || sel) {
                g.fill(dropX + 1, ry, dropX + dropW - 1, ry + POPUP_ROW_H, hov ? Theme.ROW_HOVER : Theme.ROW);
            }
            g.drawString(font, font.plainSubstrByWidth(I18n.get(def.choiceKey(choice)), dropW - 12), dropX + 6, ry + 4,
                    sel ? Theme.accent() : Theme.TEXT);
            popupHits.add(new Hit(dropX, ry, dropW, POPUP_ROW_H, (cx, cy, b, s) -> {
                if (b == 0) {
                    cfg.set(def.id(), choice);
                    closeAll();
                }
            }));
        }
    }

    private void renderColor(GuiGraphics g, Font font, int mx, int my, int sw, int sh, OptionDef<?> def, ConfigManager cfg) {
        int w = 140;
        int h = 104;
        int x = Math.max(4, Math.min(colorX, sw - w - 4));
        int y = Math.max(4, Math.min(colorY, sh - h - 4));
        String id = def.id();
        int argb = cfg.getRaw(id) instanceof Integer i ? i : 0xFFFFFFFF;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, Theme.BORDER);
        g.fill(x, y, x + w, y + h, Theme.PANEL_LIGHT);
        g.fill(x + 8, y + 8, x + w - 8, y + 22, 0xFF000000 | argb);

        int bx = x + 20;
        int bw = w - 28;
        String[] labels = {"H", "S", "V"};
        for (int k = 0; k < 3; k++) {
            int by = y + 30 + k * 16;
            g.drawString(font, labels[k], x + 8, by + 1, Theme.TEXT_DIM);
            for (int i = 0; i < bw; i += 2) {
                float t = i / (float) bw;
                int col = switch (k) {
                    case 0 -> hsvToRgb(t, 1f, 1f);
                    case 1 -> hsvToRgb(hsv[0], t, hsv[2]);
                    default -> hsvToRgb(hsv[0], hsv[1], t);
                };
                g.fill(bx + i, by, bx + Math.min(i + 2, bw), by + 10, 0xFF000000 | col);
            }
            int mxk = bx + Math.round(hsv[k] * (bw - 2));
            g.fill(mxk - 1, by - 2, mxk + 3, by + 12, 0xFFFFFFFF);
            final int kk = k;
            popupHits.add(new Hit(bx, by - 2, bw, 14, (cx, cy, b, s) -> {
                if (b == 0) {
                    Drag d = (m, sf) -> setHsv(id, kk, (float) ((m - bx) / (bw - 2.0)));
                    activeDrag = d;
                    d.on(cx, s);
                }
            }));
        }
        int hy = y + 80;
        boolean editingHex = editHex && id.equals(editId);
        g.fill(x + 8, hy, x + w - 8, hy + 16, editingHex ? 0xFF3A3A44 : Theme.BORDER);
        String shown = editingHex ? editText(System.currentTimeMillis()) : String.format("#%06X", argb & 0xFFFFFF);
        g.drawString(font, shown, x + 12, hy + 4, Theme.TEXT);
        popupHits.add(new Hit(x + 8, hy, w - 16, 16, (cx, cy, b, s) -> {
            if (b == 0) {
                startEdit(def, true, String.format("#%06X", argb & 0xFFFFFF));
            }
        }));
        // Klicks im Popup, die nichts treffen, sollen es nicht schließen
        popupHits.add(0, new Hit(x, y, w, h, (cx, cy, b, s) -> {
        }));
    }

    private void setHsv(String id, int component, float value) {
        hsv[component] = Math.max(0f, Math.min(1f, value));
        ConfigManager cfg = VisualsMod.config();
        int alpha = cfg.getRaw(id) instanceof Integer i ? (i & 0xFF000000) : 0xFF000000;
        cfg.set(id, alpha | hsvToRgb(hsv[0], hsv[1], hsv[2]));
    }

    private void renderContext(GuiGraphics g, Font font, int mx, int my, int sw, int sh, OptionDef<?> def, ConfigManager cfg) {
        String[] labels = {I18n.get("visuals.gui.ctx.reset"), I18n.get("visuals.gui.ctx.copy"), I18n.get("visuals.gui.ctx.paste")};
        int w = 130;
        int h = labels.length * POPUP_ROW_H + 4;
        int x = Math.min(ctxX, sw - w - 4);
        int y = Math.min(ctxY, sh - h - 4);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, Theme.BORDER);
        g.fill(x, y, x + w, y + h, Theme.PANEL_LIGHT);
        String id = def.id();
        for (int i = 0; i < labels.length; i++) {
            int ry = y + 2 + i * POPUP_ROW_H;
            boolean hov = Theme.inside(mx, my, x, ry, w, POPUP_ROW_H);
            if (hov) {
                g.fill(x + 1, ry, x + w - 1, ry + POPUP_ROW_H, Theme.ROW_HOVER);
            }
            g.drawString(font, labels[i], x + 8, ry + 4, Theme.TEXT);
            final int idx = i;
            popupHits.add(new Hit(x, ry, w, POPUP_ROW_H, (cx, cy, b, s) -> {
                if (b != 0) {
                    return;
                }
                switch (idx) {
                    case 0 -> cfg.reset(id);
                    case 1 -> Minecraft.getInstance().keyboardHandler.setClipboard(toText(def, cfg.getRaw(id)));
                    default -> parseAndSet(def, Minecraft.getInstance().keyboardHandler.getClipboard().trim());
                }
                closeAll();
            }));
        }
    }

    private void renderTooltip(GuiGraphics g, Font font, int mx, int my, int sw, int sh, OptionRegistry reg) {
        if (hoverId == null || popupOpen() || editing() || System.currentTimeMillis() - hoverSince < 400) {
            return;
        }
        OptionDef<?> def = reg.get(hoverId);
        if (def == null) {
            return;
        }
        String tip = I18n.get(def.tooltipKey());
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
        if (!tip.equals(def.tooltipKey())) {
            lines.addAll(font.split(Component.literal(tip), 190));
        }
        lines.addAll(font.split(Component.literal(I18n.get("visuals.gui.default") + ": " + toText(def, def.defaultValue())
                + (def.unit().isEmpty() ? "" : " " + def.unit())), 190));
        int w = 0;
        for (var l : lines) {
            w = Math.max(w, font.width(l));
        }
        w += 10;
        int h = lines.size() * 10 + 8;
        int x = Math.min(mx + 12, sw - w - 4);
        int y = Math.min(my + 14, sh - h - 4);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, Theme.accent());
        g.fill(x, y, x + w, y + h, 0xFF101014);
        int ty = y + 5;
        for (var l : lines) {
            g.drawString(font, l, x + 5, ty, Theme.TEXT);
            ty += 10;
        }
    }

    // ------------------------------------------------------------------ Eingaben

    public boolean mouseClicked(double mx, double my, int button, boolean shift) {
        if (editing()) {
            commitEdit();
        }
        if (popupOpen()) {
            for (int i = popupHits.size() - 1; i >= 0; i--) {
                Hit hit = popupHits.get(i);
                if (Theme.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
                    hit.click().on(mx, my, button, shift);
                    return true;
                }
            }
            closeAll();
            return true;
        }
        if (my < clipTop || my >= clipBottom) {
            return false;
        }
        if (button == 1) {
            for (Hit hit : ctxHits) {
                if (Theme.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
                    hit.click().on(mx, my, button, shift);
                    return true;
                }
            }
            return false;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (Theme.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
                hit.click().on(mx, my, button, shift);
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(double mx, boolean shift) {
        if (activeDrag == null) {
            return false;
        }
        activeDrag.on(mx, shift);
        return true;
    }

    public void mouseReleased() {
        activeDrag = null;
    }

    public boolean keyPressed(int key, boolean ctrl) {
        if (editing()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                commitEdit();
            } else if (key == GLFW.GLFW_KEY_ESCAPE) {
                editId = null;
            } else if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (editBuf.length() > 0) {
                    editBuf.setLength(editBuf.length() - 1);
                }
            } else if (ctrl && key == GLFW.GLFW_KEY_V) {
                for (char c : Minecraft.getInstance().keyboardHandler.getClipboard().toCharArray()) {
                    typeChar(c);
                }
            }
            return true;
        }
        if (popupOpen() && key == GLFW.GLFW_KEY_ESCAPE) {
            closeAll();
            return true;
        }
        return false;
    }

    public boolean charTyped(int codepoint) {
        if (!editing()) {
            return false;
        }
        for (char c : Character.toChars(codepoint)) {
            typeChar(c);
        }
        return true;
    }

    private void typeChar(char c) {
        if (editDef == null) {
            return;
        }
        boolean ok;
        int max;
        if (editHex || editDef.type() == OptionType.COLOR) {
            ok = c == '#' || Character.digit(c, 16) >= 0;
            max = 9;
        } else if (editDef.type() == OptionType.TEXT) {
            ok = c >= 32 && c != 127;
            max = (int) editDef.max();
        } else {
            ok = (c >= '0' && c <= '9') || c == '.' || c == ',' || c == '-';
            max = 12;
        }
        if (ok && editBuf.length() < max) {
            editBuf.append(c);
        }
    }

    // ------------------------------------------------------------------ Bearbeiten und Werte

    private void startEdit(OptionDef<?> def, boolean hex, String initial) {
        if (!hex) {
            closeAll();
        } else {
            editId = null;
        }
        editId = def.id();
        editDef = def;
        editHex = hex;
        editBuf.setLength(0);
        editBuf.append(initial);
    }

    private String editText(long now) {
        return editBuf + ((now / 500) % 2 == 0 ? "_" : "");
    }

    private void commitEdit() {
        if (editId == null || editDef == null) {
            return;
        }
        OptionDef<?> def = editDef;
        String text = editBuf.toString().trim();
        editId = null;
        if (parseAndSet(def, text) && def.type() == OptionType.COLOR && def.id().equals(colorId)
                && VisualsMod.config().getRaw(def.id()) instanceof Integer i) {
            rgbToHsv(i, hsv);
        }
    }

    /** Wandelt Text in einen Wert um und setzt ihn. Bei Fehlern oder Korrekturen wird die Zeile kurz rot markiert. */
    private boolean parseAndSet(OptionDef<?> def, String text) {
        ConfigManager cfg = VisualsMod.config();
        String id = def.id();
        boolean ok = false;
        try {
            switch (def.type()) {
                case TOGGLE -> {
                    String t = text.toLowerCase(Locale.ROOT);
                    if (t.equals("true") || t.equals("1") || t.equals("an")) {
                        ok = cfg.set(id, true);
                    } else if (t.equals("false") || t.equals("0") || t.equals("aus")) {
                        ok = cfg.set(id, false);
                    }
                }
                case SLIDER -> ok = cfg.set(id, Double.parseDouble(text.replace(',', '.')));
                case INT_SLIDER -> ok = cfg.set(id, (int) Math.round(Double.parseDouble(text.replace(',', '.'))));
                case DROPDOWN -> {
                    if (def.choices().contains(text)) {
                        ok = cfg.set(id, text);
                    }
                }
                case COLOR -> {
                    String hex = text.startsWith("#") ? text.substring(1) : text;
                    int alpha = cfg.getRaw(id) instanceof Integer i ? (i & 0xFF000000) : 0xFF000000;
                    if (hex.length() == 6) {
                        ok = cfg.set(id, alpha | Integer.parseInt(hex, 16));
                    } else if (hex.length() == 8) {
                        ok = cfg.set(id, (int) Long.parseLong(hex, 16));
                    }
                }
                default -> ok = cfg.set(id, text);
            }
        } catch (NumberFormatException e) {
            ok = false;
        }
        if (!ok) {
            invalid.put(id, System.currentTimeMillis() + 1200);
        }
        return ok;
    }

    private void slide(OptionDef<?> def, int trackX, int trackW, double mx, boolean shift) {
        double frac = Math.max(0, Math.min(1, (mx - trackX - 2) / (trackW - 4.0)));
        double v = def.min() + frac * (def.max() - def.min());
        double step = shift ? Math.max(def.step() / 10.0, 0.0001) : def.step();
        v = Math.round(v / step) * step;
        v = Math.round(v * 1e6) / 1e6;
        v = Math.max(def.min(), Math.min(def.max(), v));
        ConfigManager cfg = VisualsMod.config();
        if (def.type() == OptionType.INT_SLIDER) {
            cfg.set(def.id(), (int) Math.round(v));
        } else {
            cfg.set(def.id(), v);
        }
    }

    private static String numberText(OptionDef<?> def, double v) {
        if (def.type() == OptionType.INT_SLIDER) {
            return String.valueOf((int) Math.round(v));
        }
        double step = def.step();
        int decimals = step >= 1 ? 0 : step >= 0.1 ? 1 : step >= 0.01 ? 2 : 3;
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }

    private static String toText(OptionDef<?> def, Object value) {
        if (value == null) {
            return "";
        }
        return switch (def.type()) {
            case TOGGLE -> Boolean.TRUE.equals(value) ? I18n.get("visuals.gui.on") : I18n.get("visuals.gui.off");
            case SLIDER, INT_SLIDER -> numberText(def, ((Number) value).doubleValue());
            case COLOR -> String.format("#%06X", ((Integer) value) & 0xFFFFFF);
            case DROPDOWN -> I18n.get(def.choiceKey(String.valueOf(value)));
            default -> String.valueOf(value);
        };
    }

    // ------------------------------------------------------------------ Bausteine

    public static void drawToggle(GuiGraphics g, int x, int y, double t, boolean enabled) {
        int track = enabled ? Theme.mix(Theme.GRAY, Theme.accent(), t) : Theme.mix(0xFF3A3A40, 0xFF55453A, t);
        g.fill(x, y, x + 30, y + 14, track);
        int kx = x + 2 + (int) Math.round(t * 16);
        g.fill(kx, y + 2, kx + 10, y + 12, enabled ? 0xFFF4F4F6 : 0xFF8A8A90);
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    static int hsvToRgb(float h, float s, float v) {
        h = Math.max(0f, Math.min(1f, h)) * 6f;
        float c = v * s;
        float x = c * (1 - Math.abs(h % 2 - 1));
        float m = v - c;
        float r;
        float gr;
        float b;
        switch ((int) h % 6) {
            case 0 -> { r = c; gr = x; b = 0; }
            case 1 -> { r = x; gr = c; b = 0; }
            case 2 -> { r = 0; gr = c; b = x; }
            case 3 -> { r = 0; gr = x; b = c; }
            case 4 -> { r = x; gr = 0; b = c; }
            default -> { r = c; gr = 0; b = x; }
        }
        int ri = Math.round((r + m) * 255);
        int gi = Math.round((gr + m) * 255);
        int bi = Math.round((b + m) * 255);
        return (ri << 16) | (gi << 8) | bi;
    }

    static void rgbToHsv(int rgb, float[] out) {
        float r = ((rgb >> 16) & 255) / 255f;
        float g = ((rgb >> 8) & 255) / 255f;
        float b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0) {
            h = 0;
        } else if (max == r) {
            h = ((g - b) / d) % 6;
        } else if (max == g) {
            h = (b - r) / d + 2;
        } else {
            h = (r - g) / d + 4;
        }
        h /= 6f;
        if (h < 0) {
            h += 1f;
        }
        out[0] = h;
        out[1] = max == 0 ? 0 : d / max;
        out[2] = max;
    }
}
