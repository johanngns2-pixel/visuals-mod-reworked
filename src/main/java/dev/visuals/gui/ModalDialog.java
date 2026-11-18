package dev.visuals.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Kleines modales Fenster: Meldung, Texteingabe, Auswahlmenü oder Bestätigung mit Textzeilen.
 * Der Screen hält höchstens eines; ein Klick auf einen Knopf liefert dessen Aktion zurück.
 */
public final class ModalDialog {
    public record Button(String label, int color, Runnable action) {
    }

    private static final int W = 320;

    private final String title;
    private final List<String> lines = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();
    private final boolean vertical;
    private final boolean hasInput;
    private final int maxInput;
    private final StringBuilder input = new StringBuilder();
    private Runnable onEscape = () -> {
    };

    private final List<int[]> buttonRects = new ArrayList<>();
    private int boxX;
    private int boxY;
    private int boxW;
    private int boxH;

    private ModalDialog(String title, boolean vertical, boolean hasInput, int maxInput) {
        this.title = title;
        this.vertical = vertical;
        this.hasInput = hasInput;
        this.maxInput = maxInput;
    }

    // ------------------------------------------------------------------ Fabriken

    public static ModalDialog message(String title, List<String> lines, String okLabel, Runnable onClose) {
        ModalDialog d = new ModalDialog(title, false, false, 0);
        d.lines.addAll(lines);
        d.buttons.add(new Button(okLabel, Theme.accent(), onClose));
        d.onEscape = onClose;
        return d;
    }

    public static ModalDialog confirm(String title, List<String> lines, String okLabel, Runnable ok,
                                      String cancelLabel, Runnable cancel) {
        ModalDialog d = new ModalDialog(title, false, false, 0);
        d.lines.addAll(lines);
        d.buttons.add(new Button(okLabel, Theme.accent(), ok));
        d.buttons.add(new Button(cancelLabel, Theme.GRAY, cancel));
        d.onEscape = cancel;
        return d;
    }

    public static ModalDialog input(String title, String prompt, int max, String okLabel, Consumer<String> ok,
                                    String cancelLabel, Runnable cancel) {
        ModalDialog d = new ModalDialog(title, false, true, max);
        d.lines.add(prompt);
        d.buttons.add(new Button(okLabel, Theme.accent(), () -> ok.accept(d.input.toString().trim())));
        d.buttons.add(new Button(cancelLabel, Theme.GRAY, cancel));
        d.onEscape = cancel;
        return d;
    }

    public static ModalDialog menu(String title, List<Button> entries, Runnable onEscape) {
        ModalDialog d = new ModalDialog(title, true, false, 0);
        d.buttons.addAll(entries);
        d.onEscape = onEscape;
        return d;
    }

    // ------------------------------------------------------------------ Zeichnen

    public void render(GuiGraphics g, Font font, int mx, int my, int sw, int sh) {
        List<String> wrapped = new ArrayList<>();
        for (String line : lines) {
            wrapped.addAll(wrap(font, line, W - 24));
        }
        int inputH = hasInput ? 24 : 0;
        int buttonsH = vertical ? buttons.size() * 20 : 24;
        boxW = W;
        boxH = 30 + wrapped.size() * 10 + inputH + buttonsH + 12;
        boxX = (sw - boxW) / 2;
        boxY = Math.max(8, (sh - boxH) / 2);

        g.fill(0, 0, sw, sh, 0xB0000000);
        g.fill(boxX - 1, boxY - 1, boxX + boxW + 1, boxY + boxH + 1, Theme.BORDER);
        g.fill(boxX, boxY, boxX + boxW, boxY + boxH, Theme.PANEL_LIGHT);
        g.fill(boxX, boxY, boxX + boxW, boxY + 1, Theme.accent());
        g.drawString(font, title, boxX + 12, boxY + 12, Theme.TEXT);

        int y = boxY + 28;
        for (String line : wrapped) {
            g.drawString(font, line, boxX + 12, y, Theme.TEXT_DIM);
            y += 10;
        }
        if (hasInput) {
            y += 4;
            g.fill(boxX + 12, y, boxX + boxW - 12, y + 16, Theme.ROW);
            String shown = input + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
            g.drawString(font, font.plainSubstrByWidth(shown, boxW - 32), boxX + 16, y + 4, Theme.TEXT);
            y += 22;
        }
        y += 4;

        buttonRects.clear();
        if (vertical) {
            for (int i = 0; i < buttons.size(); i++) {
                drawButton(g, font, mx, my, boxX + 12, y + i * 20, boxW - 24, 18, buttons.get(i));
            }
        } else {
            int n = buttons.size();
            int bw = (boxW - 24 - (n - 1) * 6) / n;
            for (int i = 0; i < n; i++) {
                drawButton(g, font, mx, my, boxX + 12 + i * (bw + 6), y, bw, 18, buttons.get(i));
            }
        }
    }

    private void drawButton(GuiGraphics g, Font font, int mx, int my, int x, int y, int w, int h, Button b) {
        boolean hov = Theme.inside(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, Theme.mix(Theme.ROW, b.color(), hov ? 0.6 : 0.3));
        String text = font.plainSubstrByWidth(b.label(), w - 8);
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, Theme.TEXT);
        buttonRects.add(new int[]{x, y, w, h});
    }

    private static List<String> wrap(Font font, String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (font.width(test) > width && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        out.add(line.toString());
        return out;
    }

    // ------------------------------------------------------------------ Eingaben

    /** Liefert die Aktion des getroffenen Knopfs oder {@code null}. Der Dialog schluckt alle Klicks. */
    public Runnable click(double mx, double my) {
        for (int i = 0; i < buttonRects.size(); i++) {
            int[] r = buttonRects.get(i);
            if (Theme.inside(mx, my, r[0], r[1], r[2], r[3])) {
                return buttons.get(i).action();
            }
        }
        return null;
    }

    /** Liefert eine Aktion, wenn die Taste den Dialog beendet (Enter = erster Knopf, Esc = Abbrechen). */
    public Runnable key(int key, boolean ctrl) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            return onEscape;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && !vertical && !buttons.isEmpty()) {
            return buttons.get(0).action();
        }
        if (hasInput) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && input.length() > 0) {
                input.setLength(input.length() - 1);
            } else if (ctrl && key == GLFW.GLFW_KEY_V) {
                for (char c : Minecraft.getInstance().keyboardHandler.getClipboard().toCharArray()) {
                    type(c);
                }
            }
        }
        return null;
    }

    public void charTyped(int codepoint) {
        if (hasInput) {
            for (char c : Character.toChars(codepoint)) {
                type(c);
            }
        }
    }

    private void type(char c) {
        if (c >= 32 && c != 127 && input.length() < maxInput) {
            input.append(c);
        }
    }
}
