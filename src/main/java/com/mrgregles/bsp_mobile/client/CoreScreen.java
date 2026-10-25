package com.mrgregles.bsp_mobile.client;

import com.mrgregles.bsp_mobile.construct.ConstructState;
import com.mrgregles.bsp_mobile.construct.Gather;
import com.mrgregles.bsp_mobile.construct.MobileCoreBlockEntity;
import com.mrgregles.bsp_mobile.construct.MobileCoreItem;
import com.mrgregles.bsp_mobile.construct.Prices;
import com.mrgregles.bsp_mobile.network.CoreActionPacket;
import com.mrgregles.bsp_mobile.network.CoreViewPacket;
import com.mrgregles.bsp_mobile.network.MobileNetwork;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Mobile Core's screen (D16, R4): a teal panel of 320 x 200 with the tabs CORE, BLOCKS and UPGRADE (HELM and PLAN
 * closed until their tasks), drawn as the confirmed page {@code tools/preview/core_refined.html}. CORE: the name box,
 * the state, the allowance and tier, what was left out, the Aboard box, ASSEMBLE or RELEASE (release needs a second
 * click within three seconds) and SHOW OUTLINE. BLOCKS: the ledger of kinds with counts, refused kinds first in red.
 * UPGRADE: the tier ladder with each step's price, the coins the viewer carries, and BUY (a second click within three
 * seconds confirms; the server takes the coins with change). A lock (D32, Layout A): a LOCKED chip beside the state chip,
 * UNLOCK or STEAL at the right end of the key row, the actions greyed "locked" for those the lock does not admit, and a
 * thin red bar with the thief's name while a steal runs. The server decides; this screen only shows and asks.
 */
public class CoreScreen extends Screen {
    static final int W = 320, H = 200;
    private static final int PAD = 8, TAB_W = 40, TAB_H = 13, KEY_H = 14, ROW_H = 12;
    private static final float SMALL = 0.75f, TINY = 0.65f;
    private static final long SURE_MS = 3000;
    private static final int REFRESH_TICKS = 40, SAVE_DELAY_TICKS = 20;
    private static final int CORE = 0, BLOCKS = 1, UPGRADE = 2, HELM = 3, PLAN = 4;
    private static final int SURE_RELEASE = 1, SURE_LOCK = 2, SURE_BUY = 3;

    private CoreViewPacket view;
    private int tab = CORE;
    private boolean outlineOn = true;
    private long sureUntil;
    private int sureKey;
    private int refreshTicks = REFRESH_TICKS;
    private int listScroll;
    private final List<Ui.Hit> hits = new ArrayList<>();
    @Nullable
    private EditBox nameBox;
    @Nullable
    private EditBox cruiseBox;
    private String cruiseTyped = "";
    private String typed = "";
    private boolean dirty;
    private int dirtyTicks;
    private String note = "";
    private long noteUntil;

    public CoreScreen(CoreViewPacket view) {
        super(Component.literal(Tr.t("core.title")));
        this.view = view;
        this.typed = view.name();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    boolean outlineOn() {
        return outlineOn;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    /** A fresh view from the server. */
    void accept(CoreViewPacket next) {
        view = next;
        if (!dirty && nameBox != null && !nameBox.isFocused() && !nameBox.getValue().equals(next.name())) {
            nameBox.setValue(next.name());
            typed = next.name();
        }
        if (nameBox != null) {
            nameBox.setEditable(next.admitted());
        }
        if (cruiseBox != null) {
            cruiseBox.setEditable(next.admitted());
            if (!cruiseBox.isFocused() && !Integer.toString(next.cruise()).equals(cruiseTyped)) {
                cruiseTyped = Integer.toString(next.cruise());
                cruiseBox.setValue(cruiseTyped);
            }
        }
        if (!next.note().isEmpty()) {
            note = next.note();
            noteUntil = System.currentTimeMillis() + SURE_MS;
        }
    }

    @Override
    protected void init() {
        super.init();
        nameBox = new EditBox(font, left() + PAD + 32, top() + 22, 120, 12, Component.literal(Tr.t("name")));
        nameBox.setMaxLength(MobileCoreBlockEntity.NAME_MAX);
        nameBox.setBordered(false);
        nameBox.setTextColor(Ui.TEXT);
        nameBox.setValue(typed);
        nameBox.setEditable(view.admitted());
        nameBox.setResponder(s -> {
            if (!s.equals(typed)) {
                typed = s;
                dirty = true;
                dirtyTicks = SAVE_DELAY_TICKS;
            }
        });
        nameBox.visible = tab == CORE;
        addRenderableWidget(nameBox);
        cruiseBox = new EditBox(font, left() + W - PAD - 40, top() + 22, 36, 12, Component.literal(Tr.t("plan.cruise")));
        cruiseBox.setMaxLength(3);
        cruiseBox.setBordered(false);
        cruiseBox.setTextColor(Ui.TEXT);
        cruiseBox.setFilter(s -> s.matches("[0-9]{0,3}"));
        cruiseTyped = Integer.toString(view.cruise());
        cruiseBox.setValue(cruiseTyped);
        cruiseBox.setEditable(view.admitted());
        cruiseBox.setResponder(s -> {
            if (!s.equals(cruiseTyped) && !s.isEmpty()) {
                cruiseTyped = s;
                send(CoreActionPacket.Action.PLAN_CRUISE, s);
            }
        });
        cruiseBox.visible = tab == PLAN;
        addRenderableWidget(cruiseBox);
    }

    @Override
    public void tick() {
        super.tick();
        if (dirty && --dirtyTicks <= 0) {
            dirty = false;
            send(CoreActionPacket.Action.RENAME, typed);
        }
        if (--refreshTicks <= 0) {
            refreshTicks = REFRESH_TICKS;
            send(CoreActionPacket.Action.REFRESH, "");
        }
    }

    private void send(CoreActionPacket.Action action, String text) {
        MobileNetwork.CHANNEL.sendToServer(new CoreActionPacket(view.pos(), action, text));
    }

    @Override
    public void onClose() {
        if (dirty) {
            dirty = false;
            send(CoreActionPacket.Action.RENAME, typed);
        }
        ClientCore.clearOutline();
        super.onClose();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        hits.clear();
        int x = left(), y = top();
        g.fill(x, y, x + W, y + H, Ui.PANEL);
        Ui.frame(g, x - 1, y - 1, W + 2, H + 2, Ui.TEAL);
        // the header: title and tabs
        Ui.text(g, font, Tr.t("core.title").toUpperCase(Locale.ROOT), x + PAD, y + 6, Ui.TEAL, 1f);
        String[] tabs = {Tr.t("tab.core"), Tr.t("tab.blocks"), Tr.t("tab.upgrade"), Tr.t("tab.helm"), Tr.t("tab.plan")};
        int tx = x + W - PAD - tabs.length * TAB_W - (tabs.length - 1) * 3;
        for (int i = 0; i < tabs.length; i++) {
            boolean closed = i > PLAN, on = i == tab;
            int bx = tx + i * (TAB_W + 3);
            g.fill(bx, y + 5, bx + TAB_W, y + 5 + TAB_H, on ? Ui.TEAL : Ui.DIM);
            String s = Ui.fit(font, tabs[i].toUpperCase(Locale.ROOT), TAB_W - 4, TINY);
            int tw = Ui.width(font, s, TINY);
            Ui.text(g, font, s, bx + (TAB_W - tw) / 2, y + 9, on ? Ui.INK : closed ? 0xFF5A6270 : Ui.MUTED, TINY);
            if (!closed) {
                int which = i;
                hits.add(new Ui.Hit(bx, y + 5, TAB_W, TAB_H, () -> openTab(which)));
            }
        }
        if (nameBox != null) {
            nameBox.visible = tab == CORE;
        }
        if (cruiseBox != null) {
            cruiseBox.visible = tab == PLAN;
        }
        switch (tab) {
            case CORE -> drawCore(g, x, y);
            case BLOCKS -> drawBlocks(g, x, y);
            case HELM -> drawHelm(g, x, y);
            case PLAN -> drawPlan(g, x, y);
            default -> drawUpgrade(g, x, y);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void openTab(int which) {
        tab = which;
        listScroll = 0;
        sureUntil = 0;
    }

    /** Arms a key's SURE? step for three seconds. */
    private void arm(int which) {
        sureKey = which;
        sureUntil = System.currentTimeMillis() + SURE_MS;
    }

    private boolean armed(int which) {
        return sureKey == which && System.currentTimeMillis() < sureUntil;
    }

    /** m:ss from ticks. */
    private static String clock(int ticks) {
        int s = Math.max(0, ticks) / 20;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** A greyed action key with the "locked" hint beside it (D30). */
    private void lockedKey(GuiGraphics g, String label, int kx, int ky) {
        Ui.key(g, font, label, kx, ky, 64, KEY_H, Ui.DIM, 0xFF5A6270, SMALL);
        Ui.text(g, font, Tr.t("locked_hint"), kx + 68, ky + 4, Ui.VIOLET, TINY);
    }

    /** The server's answer to the last action, for a few seconds above the keys. */
    private void drawNote(GuiGraphics g, int x, int ky) {
        if (System.currentTimeMillis() < noteUntil && Tr.has("note." + note)) {
            Ui.text(g, font, Ui.fit(font, Tr.t("note." + note), W - 2 * PAD, TINY), x + PAD, ky - 9, note.startsWith("fail") ? Ui.BAD : Ui.OK, TINY);
        }
    }

    private ConstructState state() {
        return ConstructState.values()[Mth.clamp(view.state(), 0, ConstructState.values().length - 1)];
    }

    private void drawCore(GuiGraphics g, int x, int y) {
        ConstructState state = state();
        // the name row and the state chip
        Ui.text(g, font, Tr.t("name").toUpperCase(Locale.ROOT), x + PAD, y + 24, Ui.MUTED, TINY);
        Ui.inset(g, x + PAD + 30, y + 20, 124, 15);
        String chip = switch (state) {
            case NOT_ASSEMBLED -> Tr.t("state.not_assembled");
            case PARKED -> Tr.t("state.parked");
            case MOVING -> view.locked() ? Tr.t("state.moving_short") : Tr.t("state.moving", String.format(Locale.ROOT, "%.1f", view.speed()));
        };
        int chipColour = switch (state) {
            case NOT_ASSEMBLED -> Ui.MUTED;
            case PARKED -> Ui.TEAL;
            case MOVING -> Ui.GOLD;
        };
        int right = x + W - PAD;
        if (view.locked()) {
            // the LOCKED chip (D32): violet, red while a steal runs
            int lockColour = view.stealLeft() > 0 ? Ui.BAD : Ui.VIOLET;
            String lockText = Ui.fit(font, Tr.t("state.locked", view.ownerName().isEmpty() ? "?" : view.ownerName()), 84, SMALL);
            int lw = Ui.width(font, lockText, SMALL) + 8;
            Ui.frame(g, right - lw, y + 21, lw, 13, lockColour);
            Ui.text(g, font, lockText, right - lw + 4, y + 24, lockColour, SMALL);
            right -= lw + 4;
        }
        String chipText = Ui.fit(font, chip, 130, SMALL);
        int cw = Ui.width(font, chipText, SMALL) + 8;
        Ui.frame(g, right - cw, y + 21, cw, 13, chipColour);
        Ui.text(g, font, chipText, right - cw + 4, y + 24, chipColour, SMALL);
        // the allowance box
        int bx = x + PAD, by = y + 40, bw = 194, bh = 108;
        Ui.inset(g, bx, by, bw, bh);
        Ui.text(g, font, Tr.t("allowance", MobileCoreItem.roman(view.tier())).toUpperCase(Locale.ROOT), bx + 6, by + 6, Ui.MUTED, TINY);
        String big = Integer.toString(view.count());
        Ui.text(g, font, big, bx + 6, by + 15, view.tooMany() ? Ui.BAD : Ui.TEXT, 1.6f);
        Ui.text(g, font, Ui.fit(font, Tr.t("of_blocks", view.allowance()), bw - 12 - Ui.width(font, big, 1.6f) - 4, SMALL),
                bx + 6 + Ui.width(font, big, 1.6f) + 4, by + 21, Ui.MUTED, SMALL);
        int barW = bw - 12;
        g.fill(bx + 6, by + 34, bx + 6 + barW, by + 39, Ui.DIM);
        float frac = view.allowance() == 0 ? 0 : Math.min(1f, view.count() / (float) view.allowance());
        g.fill(bx + 6, by + 34, bx + 6 + Math.round(barW * frac), by + 39, view.tooMany() ? Ui.BAD : Ui.TEAL);
        int ly = by + 45;
        if (view.tooMany()) {
            for (String line : Ui.wrap(font, Tr.t("too_many", view.allowance()), bw - 12, SMALL)) {
                Ui.text(g, font, line, bx + 6, ly, Ui.BAD, SMALL);
                ly += 8;
            }
        } else if (!view.refused().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (CoreViewPacket.Refused r : view.refused()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(blockName(r.block())).append(' ').append(r.count());
            }
            for (String line : Ui.wrap(font, Tr.t("left_out", sb), bw - 12, SMALL)) {
                Ui.text(g, font, line, bx + 6, ly, Ui.BAD, SMALL);
                ly += 8;
            }
        } else {
            Ui.text(g, font, Tr.t("left_out_none"), bx + 6, ly, Ui.MUTED, SMALL);
            ly += 8;
        }
        String noteKey = switch (state) {
            case NOT_ASSEMBLED -> "note.idle";
            case PARKED -> "note.parked";
            case MOVING -> "note.moving";
        };
        for (String line : Ui.wrap(font, Tr.t(noteKey), bw - 12, TINY)) {
            Ui.text(g, font, line, bx + 6, ly + 2, Ui.MUTED, TINY);
            ly += 7;
        }
        // the Aboard box
        int ax = bx + bw + 6, aw = W - PAD - (ax - x);
        Ui.inset(g, ax, by, aw, bh);
        Ui.text(g, font, Tr.t("aboard").toUpperCase(Locale.ROOT), ax + 6, by + 6, Ui.MUTED, TINY);
        int ay = by + 15;
        String[] aboardLines = {Tr.t("aboard.totems", view.totems()), Tr.t("aboard.chests", view.chests(), view.machines()),
                Tr.t("owner", view.ownerName().isEmpty() ? "?" : view.ownerName()), Tr.t(view.locked() ? "helm.open_locked" : "helm.open")};
        for (int i = 0; i < aboardLines.length; i++) {
            Ui.text(g, font, Ui.fit(font, aboardLines[i], aw - 12, SMALL), ax + 6, ay, i < 2 ? Ui.TEXT : Ui.MUTED, SMALL);
            ay += 9;
        }
        if (view.totems() > 0) {
            // D34: a totem aboard caps the construct (enforced by the driving task)
            for (String line : Ui.wrap(font, Tr.t("aboard.totem_capped"), aw - 12, TINY)) {
                Ui.text(g, font, line, ax + 6, ay + 1, Ui.GOLD, TINY);
                ay += 7;
            }
        }
        // the keys
        int ky = y + H - PAD - KEY_H;
        int kx = x + PAD;
        boolean admitted = view.admitted();
        int mainW = 64;
        switch (state) {
            case NOT_ASSEMBLED -> {
                if (admitted) {
                    Ui.key(g, font, Tr.t("key.assemble"), kx, ky, 64, KEY_H, Ui.TEAL, Ui.INK, SMALL);
                    hits.add(new Ui.Hit(kx, ky, 64, KEY_H, () -> send(CoreActionPacket.Action.ASSEMBLE, "")));
                } else {
                    lockedKey(g, Tr.t("key.assemble"), kx, ky);
                    mainW = 104;
                }
            }
            case PARKED -> {
                if (!admitted) {
                    lockedKey(g, Tr.t("key.release"), kx, ky);
                    mainW = 104;
                } else if (armed(SURE_RELEASE)) {
                    mainW = 80;
                    Ui.key(g, font, Tr.t("key.sure"), kx, ky, 80, KEY_H, Ui.SURE_BG, Ui.SURE_FG, SMALL);
                    Ui.frame(g, kx, ky, 80, KEY_H, Ui.SURE_LINE);
                    hits.add(new Ui.Hit(kx, ky, 80, KEY_H, () -> {
                        sureUntil = 0;
                        send(CoreActionPacket.Action.RELEASE, "");
                    }));
                } else {
                    Ui.key(g, font, Tr.t("key.release"), kx, ky, 64, KEY_H, Ui.DIM, Ui.TEXT, SMALL);
                    hits.add(new Ui.Hit(kx, ky, 64, KEY_H, () -> arm(SURE_RELEASE)));
                }
            }
            case MOVING -> {
                Ui.key(g, font, Tr.t("key.release"), kx, ky, 64, KEY_H, Ui.DIM, 0xFF5A6270, SMALL);
                Ui.text(g, font, Tr.t(admitted ? "stop_first" : "locked_hint"), kx + 68, ky + 4, admitted ? Ui.MUTED : Ui.VIOLET, TINY);
                mainW = 104;
            }
        }
        int ox = kx + mainW + 14;
        Ui.ghostKey(g, font, Tr.t("key.outline"), ox, ky, 72, KEY_H, outlineOn ? Ui.TEAL : Ui.MUTED, SMALL);
        hits.add(new Ui.Hit(ox, ky, 72, KEY_H, () -> outlineOn = !outlineOn));
        // the lock's own key at the right end (D29 UNLOCK for those admitted, D31 STEAL for anyone else)
        if (view.locked()) {
            int lw = 64, lx = x + W - PAD - lw;
            if (view.stealLeft() > 0) {
                Ui.key(g, font, Tr.t("key.stealing"), lx, ky, lw, KEY_H, Ui.SURE_BG, Ui.SURE_FG, SMALL);
                Ui.frame(g, lx, ky, lw, KEY_H, Ui.SURE_LINE);
            } else if (admitted) {
                if (armed(SURE_LOCK)) {
                    lw = 84;
                    lx = x + W - PAD - lw;
                    Ui.key(g, font, Tr.t("key.sure_unlock"), lx, ky, lw, KEY_H, Ui.SURE_BG, Ui.SURE_FG, SMALL);
                    Ui.frame(g, lx, ky, lw, KEY_H, Ui.SURE_LINE);
                    hits.add(new Ui.Hit(lx, ky, lw, KEY_H, () -> {
                        sureUntil = 0;
                        send(CoreActionPacket.Action.UNLOCK, "");
                    }));
                } else {
                    Ui.ghostKey(g, font, Tr.t("key.unlock"), lx, ky, lw, KEY_H, Ui.VIOLET, SMALL);
                    hits.add(new Ui.Hit(lx, ky, lw, KEY_H, () -> arm(SURE_LOCK)));
                }
            } else {
                if (armed(SURE_LOCK)) {
                    lw = 84;
                    lx = x + W - PAD - lw;
                    Ui.key(g, font, Tr.t("key.sure_steal"), lx, ky, lw, KEY_H, Ui.SURE_BG, Ui.SURE_FG, SMALL);
                    Ui.frame(g, lx, ky, lw, KEY_H, Ui.SURE_LINE);
                    hits.add(new Ui.Hit(lx, ky, lw, KEY_H, () -> {
                        sureUntil = 0;
                        send(CoreActionPacket.Action.STEAL, "");
                    }));
                } else {
                    Ui.key(g, font, Tr.t("key.steal"), lx, ky, lw, KEY_H, Ui.DIM, Ui.BAD, SMALL);
                    hits.add(new Ui.Hit(lx, ky, lw, KEY_H, () -> arm(SURE_LOCK)));
                }
            }
        }
        // the steal's progress, seen by everyone with the screen open
        if (view.locked() && view.stealLeft() > 0 && view.stealTotal() > 0) {
            int sw = W - 2 * PAD;
            g.fill(x + PAD, ky - 23, x + PAD + sw, ky - 20, Ui.DIM);
            float done = 1f - view.stealLeft() / (float) view.stealTotal();
            g.fill(x + PAD, ky - 23, x + PAD + Math.round(sw * done), ky - 20, Ui.BAD);
            String left = clock(view.stealLeft());
            String line = view.stealOutside() ? Tr.t("steal.paused", view.thiefName(), left) : Tr.t("steal.line", view.thiefName(), left);
            Ui.text(g, font, Ui.fit(font, line, sw, TINY), x + PAD, ky - 18, Ui.BAD, TINY);
        }
        drawNote(g, x, ky);
    }

    private void drawBlocks(GuiGraphics g, int x, int y) {
        Ui.text(g, font, Ui.fit(font, Tr.t("blocks.header", view.count(), view.allowance(), view.kinds().size()).toUpperCase(Locale.ROOT), 220, TINY),
                x + PAD, y + 24, Ui.MUTED, TINY);
        int refusedCount = view.refused().stream().mapToInt(CoreViewPacket.Refused::count).sum();
        if (refusedCount > 0) {
            String s = Tr.t("blocks.left_out", refusedCount).toUpperCase(Locale.ROOT);
            Ui.text(g, font, s, x + W - PAD - Ui.width(font, s, TINY), y + 24, Ui.BAD, TINY);
        }
        int lx = x + PAD, ly = y + 34, lw = W - 2 * PAD - 6, lh = H - 34 - PAD - 22;
        Ui.inset(g, lx, ly, lw, lh);
        List<String[]> rows = new ArrayList<>();
        for (CoreViewPacket.Refused r : view.refused()) {
            rows.add(new String[]{blockName(r.block()) + " · " + reason(r.reason()), Integer.toString(r.count()), "bad"});
        }
        for (CoreViewPacket.Kind k : view.kinds()) {
            rows.add(new String[]{blockName(k.block()), Integer.toString(k.count()), ""});
        }
        int visible = lh / ROW_H;
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, rows.size() - visible));
        Ui.scissor(g, lx, ly, lx + lw, ly + lh);
        for (int i = listScroll; i < Math.min(rows.size(), listScroll + visible); i++) {
            int ry = ly + (i - listScroll) * ROW_H;
            if (i > listScroll) {
                g.fill(lx + 1, ry, lx + lw - 1, ry + 1, Ui.ROW_LINE);
            }
            boolean bad = rows.get(i)[2].equals("bad");
            Ui.text(g, font, Ui.fit(font, rows.get(i)[0], lw - 40, SMALL), lx + 6, ry + 3, bad ? Ui.SURE_FG : Ui.TEXT, SMALL);
            String n = rows.get(i)[1];
            Ui.text(g, font, n, lx + lw - 6 - Ui.width(font, n, SMALL), ry + 3, bad ? Ui.BAD : Ui.TEAL, SMALL);
        }
        g.disableScissor();
        // the scroll bar
        int sx = lx + lw + 2;
        g.fill(sx, ly, sx + 4, ly + lh, Ui.DIM);
        if (rows.size() > visible) {
            int th = Math.max(8, lh * visible / rows.size());
            int ty = ly + (lh - th) * listScroll / Math.max(1, rows.size() - visible);
            g.fill(sx, ty, sx + 4, ty + th, Ui.MUTED);
        }
        int fy = ly + lh + 4;
        for (String line : Ui.wrap(font, Tr.t("blocks.footer"), W - 2 * PAD, TINY)) {
            Ui.text(g, font, line, x + PAD, fy, Ui.MUTED, TINY);
            fy += 7;
        }
    }

    private void drawUpgrade(GuiGraphics g, int x, int y) {
        Ui.text(g, font, Tr.t("upgrade.header").toUpperCase(Locale.ROOT), x + PAD, y + 24, Ui.MUTED, TINY);
        int lx = x + PAD, ly = y + 34, lw = W - 2 * PAD, rowH = 16;
        int tiers = Math.max(1, view.tiers());
        Ui.inset(g, lx, ly, lw, tiers * rowH + 2);
        for (int t = 1; t <= tiers; t++) {
            int ry = ly + 1 + (t - 1) * rowH;
            boolean cur = t == view.tier();
            if (cur) {
                g.fill(lx + 1, ry, lx + lw - 1, ry + rowH, 0xFF132A28);
                g.fill(lx + 1, ry, lx + 3, ry + rowH, Ui.TEAL);
            } else if (t > 1) {
                g.fill(lx + 1, ry, lx + lw - 1, ry + 1, Ui.ROW_LINE);
            }
            for (int p = 0; p < tiers; p++) {
                g.fill(lx + 8 + p * 9, ry + 5, lx + 15 + p * 9, ry + 10, p < t ? Ui.TEAL : Ui.DIM);
            }
            Ui.text(g, font, Tr.t("upgrade.tier", MobileCoreItem.roman(t)), lx + 8 + tiers * 9 + 6, ry + 4, Ui.TEXT, SMALL);
            String blocks = t == view.tier() ? Tr.t("upgrade.blocks", view.allowance()) : t == view.tier() + 1 ? Tr.t("upgrade.blocks", view.nextAllowance()) : "";
            Ui.text(g, font, blocks, lx + 8 + tiers * 9 + 60, ry + 4, Ui.MUTED, SMALL);
            if (cur) {
                String s = Tr.t("upgrade.this");
                Ui.text(g, font, s, lx + lw - 8 - Ui.width(font, s, SMALL), ry + 4, Ui.TEAL, SMALL);
            } else if (t > view.tier()) {
                Prices.Coins p = price(t);
                if (!p.isNone()) {
                    String s = Ui.fit(font, coinsText(p), 120, SMALL);
                    Ui.text(g, font, s, lx + lw - 8 - Ui.width(font, s, SMALL), ry + 4, t == view.tier() + 1 ? Ui.TEXT : Ui.MUTED, SMALL);
                }
            }
        }
        // the coins the viewer carries, and what the next step needs
        boolean top = view.tier() >= tiers;
        Prices.Coins next = top ? Prices.Coins.NONE : price(view.tier() + 1);
        boolean enough = view.admitted() && view.coins() && !next.isNone() && view.worth() >= next.value();
        int fy = ly + tiers * rowH + 8;
        String carried = view.carried().isEmpty() ? Tr.t("upgrade.carried_none") : Tr.t("upgrade.carried", carriedText(view.carried()));
        Ui.text(g, font, Ui.fit(font, carried, lw, SMALL), lx, fy, enough ? Ui.TEAL : Ui.TEXT, SMALL);
        fy += 10;
        String hint = !view.admitted() ? Tr.t("upgrade.locked") : !view.coins() ? Tr.t("upgrade.no_coins") : top ? Tr.t("upgrade.at_top")
                : enough ? Tr.t("upgrade.enough") : Tr.t("upgrade.short");
        for (String line : Ui.wrap(font, hint, lw, TINY)) {
            Ui.text(g, font, line, lx, fy, Ui.MUTED, TINY);
            fy += 7;
        }
        // BUY, with SURE? (two clicks within three seconds, as RELEASE)
        int ky = y + H - PAD - KEY_H;
        String roman = MobileCoreItem.roman(Math.min(tiers, view.tier() + 1));
        if (top) {
            Ui.key(g, font, Tr.t("upgrade.top"), lx, ky, 100, KEY_H, Ui.DIM, 0xFF5A6270, SMALL);
        } else if (!enough) {
            Ui.key(g, font, Tr.t("upgrade.key", roman), lx, ky, 100, KEY_H, Ui.DIM, 0xFF5A6270, SMALL);
        } else if (armed(SURE_BUY)) {
            Ui.key(g, font, Tr.t("upgrade.sure", roman), lx, ky, 120, KEY_H, Ui.SURE_BG, Ui.SURE_FG, SMALL);
            Ui.frame(g, lx, ky, 120, KEY_H, Ui.SURE_LINE);
            hits.add(new Ui.Hit(lx, ky, 120, KEY_H, () -> {
                sureUntil = 0;
                send(CoreActionPacket.Action.UPGRADE, "");
            }));
        } else {
            Ui.key(g, font, Tr.t("upgrade.key", roman), lx, ky, 100, KEY_H, Ui.TEAL, Ui.INK, SMALL);
            hits.add(new Ui.Hit(lx, ky, 100, KEY_H, () -> arm(SURE_BUY)));
        }
        drawNote(g, x, ky);
    }

    /** The price of reaching {@code tier}, from the view's ladder (Tier I to II first). */
    private Prices.Coins price(int tier) {
        int i = tier - 2;
        return i < 0 || i >= view.prices().size() ? Prices.Coins.NONE : view.prices().get(i);
    }

    /** "6 Gold Shatter Coins", "1 Illyrium Shatter Coin". */
    private static String coinsText(Prices.Coins c) {
        String name = Tr.t("coin." + c.tier());
        return c.count() == 1 ? Tr.t("coins.one", c.count(), name) : Tr.t("coins.many", c.count(), name);
    }

    /** "1 Diamond, 2 Gold" for the coins-carried line. */
    private static String carriedText(List<Prices.Coins> list) {
        StringBuilder sb = new StringBuilder();
        for (Prices.Coins c : list) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(c.count()).append(' ').append(Tr.t("coin." + c.tier()));
        }
        return sb.toString();
    }

    /** The HELM tab (D42, Layout 1): speed, heading and driver readouts, the helms and the anchor, one key. */
    private void drawHelm(GuiGraphics g, int x, int y) {
        ConstructState state = state();
        boolean assembled = state != ConstructState.NOT_ASSEMBLED;
        String header = Tr.t(state == ConstructState.MOVING ? "helm.header_way" : "helm.header_still", view.name());
        Ui.text(g, font, Ui.fit(font, header.toUpperCase(Locale.ROOT), W - 2 * PAD, TINY), x + PAD, y + 24, Ui.MUTED, TINY);
        int bx = x + PAD, by = y + 34, bw = (W - 2 * PAD - 12) / 3, bh = 44;
        Ui.inset(g, bx, by, bw, bh);
        Ui.text(g, font, Tr.t("helm.speed").toUpperCase(Locale.ROOT), bx + 6, by + 5, Ui.MUTED, TINY);
        Ui.text(g, font, String.format(Locale.ROOT, "%.1f", view.speed()), bx + 6, by + 13, state == ConstructState.MOVING ? Ui.GOLD : Ui.TEXT, 1.6f);
        Ui.text(g, font, Ui.fit(font, Tr.t("helm.speed_unit", String.format(Locale.ROOT, "%.0f", view.topSpeed())), bw - 12, TINY), bx + 6, by + 33, Ui.MUTED, TINY);
        int hx = bx + bw + 6;
        Ui.inset(g, hx, by, bw, bh);
        Ui.text(g, font, Tr.t("helm.heading").toUpperCase(Locale.ROOT), hx + 6, by + 5, Ui.MUTED, TINY);
        Ui.text(g, font, view.heading() < 0 ? "-" : Tr.t("helm.degrees", view.heading()), hx + 6, by + 13, Ui.TEXT, 1.6f);
        if (view.heading() >= 0) {
            Ui.text(g, font, Ui.fit(font, Tr.t("compass." + compass(view.heading())), bw - 12, TINY), hx + 6, by + 33, Ui.MUTED, TINY);
        }
        int dx = hx + bw + 6;
        Ui.inset(g, dx, by, bw, bh);
        Ui.text(g, font, Tr.t("helm.driver").toUpperCase(Locale.ROOT), dx + 6, by + 5, Ui.MUTED, TINY);
        boolean driven = !view.driverName().isEmpty();
        Ui.text(g, font, Ui.fit(font, driven ? view.driverName() : Tr.t("helm.driver_none"), bw - 12, 1f), dx + 6, by + 16, driven ? Ui.TEXT : Ui.MUTED, 1f);
        if (driven) {
            Ui.text(g, font, Ui.fit(font, Tr.t("helm.at_helm"), bw - 12, TINY), dx + 6, by + 33, Ui.MUTED, TINY);
        }
        int ly = by + bh + 6, lw = W - 2 * PAD, ky = y + H - PAD - KEY_H, lh = ky - 10 - ly;
        Ui.inset(g, bx, ly, lw, lh);
        List<String> lines = new ArrayList<>();
        if (!assembled) {
            lines.add(Tr.t("helm.not_assembled"));
        }
        if (view.helms() == 0) {
            lines.add(Tr.t("helm.helms_none"));
        } else {
            lines.add(Tr.t("helm.helms", view.helms()) + " · " + Tr.t(view.locked() ? "helm.locked_drive" : "helm.anyone"));
        }
        lines.add(view.anchored() ? Tr.t("helm.anchor_down") : Tr.t("helm.anchor_up"));
        if (view.closedRegion()) {
            lines.add(Tr.t("helm.closed_region"));
        }
        if (view.engines() + view.floaters() + view.balloons() + view.wings() + view.jets() > 0) {
            lines.add(Tr.t("helm.parts", view.engines(), view.floaters(), view.balloons(), view.wings(), view.jets()));
        } else {
            lines.add(Tr.t("helm.parts_none"));
        }
        if (view.liftCapacity() > 0 && assembled) {
            lines.add(Tr.t("helm.lift", String.format(Locale.ROOT, "%.0f", Math.min(view.liftCapacity(), view.mass())), String.format(Locale.ROOT, "%.0f", view.mass())));
            lines.add(view.ceiling() >= 0 ? Tr.t("helm.height", view.height(), view.ceiling()) : Tr.t("helm.height_free", view.height()));
        }
        if (view.totems() > 0) {
            lines.add(Tr.t("helm.totem_cap", String.format(Locale.ROOT, "%.0f", view.topSpeed())));
        }
        int ty = ly + 6;
        for (String s : lines) {
            for (String line : Ui.wrap(font, s, lw - 12, SMALL)) {
                Ui.text(g, font, line, bx + 6, ty, Ui.TEXT, SMALL);
                ty += 9;
            }
        }
        String label = view.anchored() ? Tr.t("key.anchor_weigh") : Tr.t("key.anchor_drop");
        if (assembled && view.admitted()) {
            Ui.key(g, font, label, bx, ky, 84, KEY_H, Ui.DIM, Ui.TEXT, SMALL);
            hits.add(new Ui.Hit(bx, ky, 84, KEY_H, () -> send(CoreActionPacket.Action.ANCHOR, view.anchored() ? "up" : "down")));
        } else {
            Ui.key(g, font, label, bx, ky, 84, KEY_H, Ui.DIM, 0xFF5A6270, SMALL);
            if (assembled) {
                Ui.text(g, font, Tr.t("locked_hint"), bx + 88, ky + 4, Ui.VIOLET, TINY);
            }
        }
        drawNote(g, x, ky);
    }

    /** The PLAN tab (D67, Layout 1): the waypoints, the cruise height, ADD HERE, REMOVE, GO or STOP. */
    private void drawPlan(GuiGraphics g, int x, int y) {
        int n = view.plan().length;
        String header = view.planRunning() ? Tr.t("plan.header_way", view.planNext() + 1, n) : Tr.t("plan.header", n);
        Ui.text(g, font, Ui.fit(font, header.toUpperCase(Locale.ROOT), 200, TINY), x + PAD, y + 24, Ui.MUTED, TINY);
        String cl = Tr.t("plan.cruise").toUpperCase(Locale.ROOT);
        Ui.text(g, font, cl, x + W - PAD - 44 - Ui.width(font, cl, TINY), y + 24, Ui.MUTED, TINY);
        Ui.inset(g, x + W - PAD - 42, y + 20, 42, 15);
        int lx = x + PAD, ly = y + 38, lw = W - 2 * PAD, ky = y + H - PAD - KEY_H, lh = ky - 24 - ly;
        Ui.inset(g, lx, ly, lw, lh);
        if (n == 0) {
            int ty = ly + 6;
            for (String line : Ui.wrap(font, Tr.t("plan.empty"), lw - 12, SMALL)) {
                Ui.text(g, font, line, lx + 6, ty, Ui.MUTED, SMALL);
                ty += 9;
            }
        } else {
            int visible = lh / ROW_H;
            listScroll = Mth.clamp(listScroll, 0, Math.max(0, n - visible));
            Ui.scissor(g, lx, ly, lx + lw, ly + lh);
            for (int i = listScroll; i < Math.min(n, listScroll + visible); i++) {
                int ry = ly + (i - listScroll) * ROW_H;
                net.minecraft.core.BlockPos p = net.minecraft.core.BlockPos.of(view.plan()[i]);
                boolean cur = view.planRunning() && i == view.planNext(), passed = view.planRunning() && i < view.planNext();
                if (cur) {
                    g.fill(lx + 1, ry, lx + lw - 1, ry + ROW_H, 0xFF132A28);
                    g.fill(lx + 1, ry, lx + 3, ry + ROW_H, Ui.TEAL);
                } else if (i > listScroll) {
                    g.fill(lx + 1, ry, lx + lw - 1, ry + 1, Ui.ROW_LINE);
                }
                int colour = passed ? Ui.MUTED : Ui.TEXT;
                Ui.text(g, font, Integer.toString(i + 1), lx + 6, ry + 3, colour, SMALL);
                Ui.text(g, font, Tr.t("plan.row", p.getX(), p.getY(), p.getZ()), lx + 22, ry + 3, colour, SMALL);
                String right = passed ? Tr.t("plan.passed") : cur && view.planDistance() >= 0 ? Tr.t("plan.blocks", view.planDistance())
                        : i == n - 1 ? Tr.t("plan.landing") : "";
                Ui.text(g, font, right, lx + lw - 6 - Ui.width(font, right, SMALL), ry + 3, cur ? Ui.GOLD : Ui.MUTED, SMALL);
            }
            g.disableScissor();
        }
        int hy = ly + lh + 4;
        for (String line : Ui.wrap(font, Tr.t("plan.hint"), lw, TINY)) {
            Ui.text(g, font, line, lx, hy, Ui.MUTED, TINY);
            hy += 7;
        }
        boolean may = view.admitted();
        Ui.key(g, font, Tr.t("key.plan_add"), lx, ky, 64, KEY_H, may ? Ui.DIM : Ui.DIM, may ? Ui.TEXT : 0xFF5A6270, SMALL);
        Ui.ghostKey(g, font, Tr.t("key.plan_remove"), lx + 70, ky, 56, KEY_H, may && n > 0 ? Ui.MUTED : 0xFF5A6270, SMALL);
        if (may) {
            hits.add(new Ui.Hit(lx, ky, 64, KEY_H, () -> send(CoreActionPacket.Action.PLAN_ADD, "")));
            hits.add(new Ui.Hit(lx + 70, ky, 56, KEY_H, () -> send(CoreActionPacket.Action.PLAN_REMOVE, "")));
        }
        int gx = x + W - PAD - 64;
        if (view.planRunning()) {
            Ui.key(g, font, Tr.t("key.plan_stop"), gx, ky, 64, KEY_H, Ui.SURE_BG, Ui.SURE_FG, SMALL);
            Ui.frame(g, gx, ky, 64, KEY_H, Ui.SURE_LINE);
            if (may) {
                hits.add(new Ui.Hit(gx, ky, 64, KEY_H, () -> send(CoreActionPacket.Action.PLAN_STOP, "")));
            }
        } else {
            boolean can = may && n > 0 && view.helms() > 0 && state() != ConstructState.NOT_ASSEMBLED;
            Ui.key(g, font, Tr.t("key.plan_go"), gx, ky, 64, KEY_H, can ? Ui.TEAL : Ui.DIM, can ? Ui.INK : 0xFF5A6270, SMALL);
            if (can) {
                hits.add(new Ui.Hit(gx, ky, 64, KEY_H, () -> send(CoreActionPacket.Action.PLAN_GO, "")));
            }
        }
        drawNote(g, x, ky);
    }

    private static String compass(int deg) {
        String[] k = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
        return k[(int) Math.round(deg / 45.0) % 8];
    }

    private static String blockName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Block block = rl == null ? null : ForgeRegistries.BLOCKS.getValue(rl);
        return block == null ? id : block.getName().getString();
    }

    private static String reason(int ordinal) {
        Gather.Reason[] all = Gather.Reason.values();
        return Tr.t("reason." + all[Mth.clamp(ordinal, 0, all.length - 1)].name().toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (Ui.Hit hit : hits) {
                if (hit.contains(mx, my)) {
                    hit.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (tab == BLOCKS || tab == PLAN) {
            listScroll -= (int) Math.signum(delta);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }
}
