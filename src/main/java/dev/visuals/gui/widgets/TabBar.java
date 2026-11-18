package dev.visuals.gui.widgets;

import dev.visuals.VisualsMod;
import dev.visuals.config.ConfigManager;
import dev.visuals.config.TabDef;
import dev.visuals.config.def.Tabs;
import dev.visuals.gui.Anim;
import dev.visuals.gui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;

/**
 * Reiter-Leiste am linken Rand: zugeklappt nur Icons (40 px), aufgeklappt mit Namen (140 px).
 * Klappt beim Hover automatisch auf; der Pfeil unten hält sie dauerhaft offen.
 * Noch offen: Drag-and-drop der Reihenfolge, echte Icon-Texturen (bis dahin Buchstabe im Kästchen).
 */
public final class TabBar {
    public static final int COLLAPSED_W = 40;
    public static final int EXPANDED_W = 140;
    private static final int ROW_H = 28;
    private static final int ARROW_H = 22;

    private final List<TabDef> order = new ArrayList<>();
    private final Anim width = new Anim(COLLAPSED_W);
    private boolean pinnedOpen;
    private int scroll;
    private int height;

    public TabBar(List<TabDef> tabs) {
        order.addAll(tabs);
    }

    public List<TabDef> tabs() {
        return order;
    }

    public void setHeight(int height) {
        this.height = height;
        clampScroll();
    }

    /** Aktuelle Breite, auch während der Animation. */
    public int currentWidth() {
        return (int) Math.round(width.value());
    }

    private int listHeight() {
        return height - ARROW_H;
    }

    private void clampScroll() {
        int max = Math.max(0, order.size() * ROW_H - listHeight());
        scroll = Math.max(0, Math.min(max, scroll));
    }

    public boolean contains(double mx, double my) {
        return mx >= 0 && mx < currentWidth() && my >= 0 && my < height;
    }

    public void scrolled(double amount) {
        scroll -= (int) (amount * ROW_H);
        clampScroll();
    }

    /** Liefert die ID des angeklickten Reiters oder {@code null}. Der Pfeil klappt die Leiste fest auf oder zu. */
    public String clicked(double mx, double my) {
        if (!contains(mx, my)) {
            return null;
        }
        if (my >= listHeight()) {
            pinnedOpen = !pinnedOpen;
            return null;
        }
        int index = (int) ((my + scroll) / ROW_H);
        return index >= 0 && index < order.size() ? order.get(index).id() : null;
    }

    /** Springt zum nächsten oder vorherigen Reiter (Pfeiltasten), mit Umlauf. */
    public String neighbour(String activeId, int delta) {
        int i = 0;
        for (int k = 0; k < order.size(); k++) {
            if (order.get(k).id().equals(activeId)) {
                i = k;
            }
        }
        int next = Math.floorMod(i + delta, order.size());
        ensureVisible(next);
        return order.get(next).id();
    }

    private void ensureVisible(int index) {
        int top = index * ROW_H;
        if (top < scroll) {
            scroll = top;
        } else if (top + ROW_H > scroll + listHeight()) {
            scroll = top + ROW_H - listHeight();
        }
        clampScroll();
    }

    public void render(GuiGraphics g, Font font, int mx, int my, String activeId) {
        ConfigManager cfg = VisualsMod.config();
        boolean hover = contains(mx, my);
        width.step(pinnedOpen || hover ? EXPANDED_W : COLLAPSED_W);
        int w = currentWidth();
        double open = (w - COLLAPSED_W) / (double) (EXPANDED_W - COLLAPSED_W);

        g.fill(0, 0, w, height, Theme.PANEL);
        g.fill(w - 1, 0, w, height, Theme.BORDER);

        g.enableScissor(0, 0, w, listHeight());
        for (int i = 0; i < order.size(); i++) {
            TabDef tab = order.get(i);
            int y = i * ROW_H - scroll;
            if (y + ROW_H < 0 || y > listHeight()) {
                continue;
            }
            boolean active = tab.id().equals(activeId);
            boolean hovered = hover && Theme.inside(mx, my, 0, y, w, ROW_H) && my < listHeight();
            if (active) {
                g.fill(0, y, w - 1, y + ROW_H, Theme.PANEL_LIGHT);
                g.fill(0, y + 2, 3, y + ROW_H - 2, Theme.accent());
            } else if (hovered) {
                g.fill(0, y, w - 1, y + ROW_H, Theme.mix(Theme.PANEL, Theme.ROW_HOVER, 0.6));
            }
            // Icon-Platzhalter: Kästchen mit Anfangsbuchstabe
            String name = I18n.get(tab.nameKey());
            int boxColor = active ? Theme.accent() : Theme.mix(Theme.ROW, Theme.accent(), hovered ? 0.35 : 0.0);
            g.fill(10, y + 6, 26, y + 22, boxColor);
            String letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
            g.drawString(font, letter, 18 - font.width(letter) / 2, y + 10, active ? 0xFF111111 : Theme.TEXT);
            // Statuspünktchen
            boolean on = cfg != null && (tab.id().equals(Tabs.GENERAL) ? cfg.isEffectActive(Tabs.GENERAL) : cfg.isEffectActive(tab.id()));
            g.fill(24, y + 4, 28, y + 8, on ? Theme.GREEN : Theme.GRAY);
            if (open > 0.6) {
                String shown = font.plainSubstrByWidth(name, EXPANDED_W - 40 - 6);
                g.drawString(font, shown, 34, y + 10, active ? Theme.TEXT : Theme.TEXT_DIM);
            }
        }
        g.disableScissor();

        // Pfeil-Knopf
        int ay = height - ARROW_H;
        g.fill(0, ay, w - 1, height, Theme.PANEL_LIGHT);
        String arrow = pinnedOpen ? "<" : ">";
        g.drawString(font, arrow, w / 2 - font.width(arrow) / 2, ay + 7, Theme.TEXT_DIM);
    }
}
