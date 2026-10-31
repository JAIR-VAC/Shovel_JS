package com.kve.client.gui.picker;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.ScrollBar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

public class RawNbtTextArea extends AbstractWidget {

    private static final int SB_RESERVE = 9;

    private final Font font;
    private String text = "";
    private int cursor = 0;
    private int maxLength = 8000;
    private final int lineHeight;
    private final int visibleLines;
    private final ScrollBar scrollBar;
    private Runnable onChange;

    private boolean dragScrolling = false;
    private double  dragAccumPx   = 0;

    public RawNbtTextArea(Font font, int x, int y, int w, int h) {
        super(x, y, w, h, Component.literal("SNBT"));
        this.font = font;
        this.lineHeight = font.lineHeight + 2;
        this.visibleLines = Math.max(1, (h - 4) / lineHeight);
        this.scrollBar = new ScrollBar(x + w - 7, y + 1, h - 2, 1, visibleLines);
    }

    public void setMaxLength(int n) { this.maxLength = Math.max(0, n); }
    public void setResponder(Runnable r) { this.onChange = r; }
    public void setEditable(boolean editable) { this.active = editable; }

    public String getValue() { return text; }

    public void setValue(String v) {
        this.text = v == null ? "" : v;
        if (this.text.length() > maxLength) this.text = this.text.substring(0, maxLength);
        this.cursor = Math.min(cursor, text.length());
        refreshScrollTotals();
        scrollBar.setScroll(0);
    }

    private List<String> lines() { return Arrays.asList(text.split("\n", -1)); }

    private void refreshScrollTotals() {
        scrollBar.setTotals(lines().size(), visibleLines);
    }

    private int lineOf(int idx) {
        int line = 0;
        for (int i = 0; i < idx && i < text.length(); i++) if (text.charAt(i) == '\n') line++;
        return line;
    }

    private int lineStart(int line) {
        List<String> ls = lines();
        int pos = 0;
        for (int i = 0; i < line && i < ls.size(); i++) pos += ls.get(i).length() + 1;
        return pos;
    }

    private int colOf(int idx) { return idx - lineStart(lineOf(idx)); }

    private static boolean isTypable(char c) {
        return c >= 0x20 && c != 0x7F;
    }

    private void insert(String s) {
        if (!active || text.length() >= maxLength) return;
        String filtered = s.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder clean = new StringBuilder();
        for (char c : filtered.toCharArray()) {
            if (c == '\n' || c == '\t' || isTypable(c)) clean.append(c);
        }
        int room = maxLength - text.length();
        String toInsert = clean.length() > room ? clean.substring(0, room) : clean.toString();
        if (toInsert.isEmpty()) return;
        text = text.substring(0, cursor) + toInsert + text.substring(cursor);
        cursor += toInsert.length();
        ensureVisible();
        if (onChange != null) onChange.run();
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (!isFocused() || !active) return false;
        if (c == '\r' || c == '\n') return false;
        insert(String.valueOf(c));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!isFocused() || !active) return false;
        boolean ctrl = Screen.hasControlDown();

        if (ctrl && key == 86) {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip != null) insert(clip);
            return true;
        }
        if (ctrl && key == 67) {
            Minecraft.getInstance().keyboardHandler.setClipboard(text);
            return true;
        }

        switch (key) {
            case 257, 335 -> { insert("\n"); return true; }
            case 259 -> {
                if (cursor > 0) {
                    text = text.substring(0, cursor - 1) + text.substring(cursor);
                    cursor--;
                    ensureVisible();
                    if (onChange != null) onChange.run();
                }
                return true;
            }
            case 261 -> {
                if (cursor < text.length()) {
                    text = text.substring(0, cursor) + text.substring(cursor + 1);
                    if (onChange != null) onChange.run();
                }
                return true;
            }
            case 263 -> { if (cursor > 0) cursor--; ensureVisible(); return true; }
            case 262 -> { if (cursor < text.length()) cursor++; ensureVisible(); return true; }
            case 265 -> { moveVertical(-1); return true; }
            case 264 -> { moveVertical(1); return true; }
            case 268 -> { cursor = lineStart(lineOf(cursor)); ensureVisible(); return true; }
            case 269 -> {
                int line = lineOf(cursor);
                cursor = lineStart(line) + lines().get(line).length();
                ensureVisible();
                return true;
            }
            default -> { return false; }
        }
    }

    private void moveVertical(int dir) {
        List<String> ls = lines();
        int line = lineOf(cursor);
        int col = colOf(cursor);
        int newLine = Math.max(0, Math.min(ls.size() - 1, line + dir));
        int newCol = Math.min(col, ls.get(newLine).length());
        cursor = lineStart(newLine) + newCol;
        ensureVisible();
    }

    private void ensureVisible() {
        refreshScrollTotals();
        int line = lineOf(cursor);
        int scroll = scrollBar.getScroll();
        if (line < scroll) scroll = line;
        if (line >= scroll + visibleLines) scroll = line - visibleLines + 1;
        scrollBar.setScroll(scroll);
    }

    @Override
    public void onClick(double mx, double my) {
        if (!active) return;
        refreshScrollTotals();
        List<String> ls = lines();
        int relY = (int) my - getY() - 2;
        int line = Math.max(0, Math.min(ls.size() - 1, scrollBar.getScroll() + relY / lineHeight));
        String ln = ls.get(line);
        int relX = Math.max(0, (int) mx - getX() - 2);
        int col = font.plainSubstrByWidth(ln, relX).length();
        cursor = lineStart(line) + Math.min(col, ln.length());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        if (!isMouseOver(mx, my)) return false;
        refreshScrollTotals();
        scrollBar.mouseScrolled(delta);
        return true;
    }

    /**
     * Click routing: the embedded {@link ScrollBar} gets first refusal (its
     * own {@code mouseClicked()} only returns true for clicks actually inside
     * its 6px track, so this is a no-op everywhere else). Failing that,
     * falls back to the old click-and-drag-anywhere gesture on top of
     * whatever {@code onClick()}/cursor-placement the vanilla
     * {@code mouseClicked()} already does when editable. Doesn't require
     * {@code active} for either path, since read-only previews (the main
     * motivation for both) are never active.
     */
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        refreshScrollTotals();
        if (btn == 0 && scrollBar.mouseClicked(mx, my, btn)) return true;

        boolean handled = super.mouseClicked(mx, my, btn);
        if (btn == 0 && isMouseOver(mx, my)) {
            dragScrolling = true;
            dragAccumPx = 0;
            handled = true;
        }
        return handled;
    }

    /** ScrollBar drag takes priority (only active once its own mouseClicked() started a drag on the thumb/track). Otherwise, converts vertical drag distance into whole-line scroll steps, carrying the leftover sub-line pixels in dragAccumPx so a drag doesn't need to cross a full lineHeight every single mouseDragged() callback to register. Dragging down (dy > 0) scrolls further down into the text, matching the direction the mouse moved. */
    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (scrollBar.mouseDragged(mx, my, btn, dx, dy)) return true;
        if (!dragScrolling || btn != 0) return false;
        refreshScrollTotals();
        dragAccumPx += dy;
        while (dragAccumPx >= lineHeight) { scrollBar.scrollBy(1);  dragAccumPx -= lineHeight; }
        while (dragAccumPx <= -lineHeight) { scrollBar.scrollBy(-1); dragAccumPx += lineHeight; }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        scrollBar.mouseReleased(mx, my, btn);
        dragScrolling = false;
        dragAccumPx = 0;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        refreshScrollTotals();
        g.fill(getX(), getY(), getX() + width, getY() + height, active ? KveGuiStyle.BG_FIELD : 0xFF080C14);
        int borderColor = isFocused() ? KveGuiStyle.BORDER_OUTER : KveGuiStyle.BORDER_FIELD;
        g.hLine(getX(), getX() + width - 1, getY(), borderColor);
        g.hLine(getX(), getX() + width - 1, getY() + height - 1, borderColor);
        g.vLine(getX(), getY(), getY() + height - 1, borderColor);
        g.vLine(getX() + width - 1, getY(), getY() + height - 1, borderColor);

        int scrollLine = scrollBar.getScroll();
        g.enableScissor(getX() + 1, getY() + 1, getX() + width - 1 - SB_RESERVE, getY() + height - 1);
        List<String> ls = lines();
        int cursorLine = lineOf(cursor);
        int cursorCol = colOf(cursor);
        boolean blink = (System.currentTimeMillis() / 500L) % 2 == 0;
        for (int i = 0; i < visibleLines; i++) {
            int line = scrollLine + i;
            if (line >= ls.size()) break;
            int ty = getY() + 2 + i * lineHeight;
            KveGuiStyle.renderInlineItemIcons(g, font, ls.get(line), getX() + 2, ty, KveGuiStyle.TEXT_PRIMARY, false);
            if (isFocused() && active && blink && line == cursorLine) {
                String upToCursor = ls.get(line).substring(0, Math.min(cursorCol, ls.get(line).length()));
                int cx = getX() + 2 + font.width(upToCursor);
                g.fill(cx, ty - 1, cx + 1, ty + font.lineHeight, KveGuiStyle.ACCENT_GOLD);
            }
        }
        g.disableScissor();

        scrollBar.render(g);

        if (ls.size() > visibleLines) {
            String pos = (scrollLine + 1) + "-" + Math.min(ls.size(), scrollLine + visibleLines) + "/" + ls.size();
            KveGuiStyle.drawTextSafe(g, pos, getX() + width - SB_RESERVE - font.width(pos) - 3, getY() + height - font.lineHeight - 2, width - SB_RESERVE, KveGuiStyle.TEXT_MUTED);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.literal("Raw SNBT text"));
    }
}
