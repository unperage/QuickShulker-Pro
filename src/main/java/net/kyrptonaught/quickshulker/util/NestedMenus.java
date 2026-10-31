/*
 * MIT License
 *
 * Copyright (c) 2019 kyrptonaught
 * Copyright (c) 2024 Haocen2004
 * Copyright (c) 2025 MoRanpcy
 * Copyright (c) 2025 EnderPhantomWing
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.kyrptonaught.quickshulker.util;

import net.kyrptonaught.quickshulker.QuickShulkerMod;
import net.kyrptonaught.quickshulker.network.OpenInventoryPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Remembers, per server player, the container screens a quick-opened container was opened from, so closing it can
 * put the previous one back instead of dropping the player into the world.
 *
 * <p>Vanilla allows a single open menu per player, so "several containers at once" is emulated: opening the next
 * container closes the current one as usual, and that level is re-opened once the new one is gone. Every level is
 * a {@link ChestMenu} over some {@link Container}, which covers chests, ender chests and quick-opened shulkers
 * alike. The container itself is what survives, so entering a level again never loses its contents.
 */
public final class NestedMenus {

    private NestedMenus() {
    }

    /** What sat underneath the container that is open right now. */
    private static final class Frame {
        static final int KIND_INVENTORY = 0;
        static final int KIND_CHEST = 1;
        static final int KIND_SHULKER = 2;

        final int kind;
        final Container container;
        final int rows;

        Frame(int kind, Container container, int rows) {
            this.kind = kind;
            this.container = container;
            this.rows = rows;
        }
    }

    private static final Map<ServerPlayer, Deque<Frame>> STACKS = new WeakHashMap<>();
    private static final Set<ServerPlayer> RESTORING = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<ServerPlayer> OPENING = Collections.newSetFromMap(new WeakHashMap<>());
    private static final int MAX_DEPTH = 16;

    public static boolean enabled() {
        // The switch lives on the client and travels with each open request; the server never reads it.
        return true;
    }

    public static boolean isRestoring(ServerPlayer player) {
        return RESTORING.contains(player);
    }

    /**
     * True while a quick-open is in flight. {@code openMenu} closes the current menu first, and that close must not
     * be mistaken for the player leaving the new container - otherwise the level captured a moment ago would be
     * restored immediately and the stack would be empty by the time the new container is closed.
     */
    public static boolean isOpening(ServerPlayer player) {
        return OPENING.contains(player);
    }

    public static void beginOpening(ServerPlayer player) {
        OPENING.add(player);
    }

    public static void endOpening(ServerPlayer player) {
        OPENING.remove(player);
    }

    private static void push(ServerPlayer player, Frame frame) {
        if (!enabled() || isRestoring(player)) return;
        Deque<Frame> stack = STACKS.computeIfAbsent(player, key -> new ArrayDeque<>());
        stack.push(frame);
        while (stack.size() > MAX_DEPTH) stack.removeLast();
    }

    /**
     * Called right before a container is quick-opened: records whatever the player is looking at now, so it can
     * come back once the new container is closed.
     */
    public static void captureCurrent(ServerPlayer player, boolean nest) {
        if (!nest || isRestoring(player)) {
            debug(player, "capture skipped nest=" + nest + " restoring=" + isRestoring(player));
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (menu instanceof ChestMenu chest && chest.getContainer() != null) {
            push(player, new Frame(Frame.KIND_CHEST, chest.getContainer(), chest.getRowCount()));
            debug(player, "capture chest rows=" + chest.getRowCount() + " depth=" + depth(player));
            return;
        }
        // A quick-opened shulker is a ShulkerBoxMenu, not a ChestMenu: it has to be recognised or the level
        // underneath it would be recorded as "player inventory" and closing would skip it.
        if (menu instanceof ShulkerBoxMenu) {
            Container container = containerOf(menu);
            if (container != null) {
                push(player, new Frame(Frame.KIND_SHULKER, container, 3));
                debug(player, "capture shulker size=" + container.getContainerSize() + " depth=" + depth(player));
                return;
            }
        }
        push(player, new Frame(Frame.KIND_INVENTORY, null, 0));
        debug(player, "capture inventory menu=" + menu.getClass().getSimpleName() + " depth=" + depth(player));
    }

    /** First non-player container backing this menu's slots, or null when there is none. */
    private static Container containerOf(AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot.container == null) continue;
            if (slot.container instanceof Inventory) continue;
            return slot.container;
        }
        return null;
    }

    private static int depth(ServerPlayer player) {
        Deque<Frame> stack = STACKS.get(player);
        return stack == null ? 0 : stack.size();
    }

    /** Opt-in tracing: start the client with -Dquickshulker.debug.nested=true. */
    private static final boolean DEBUG = Boolean.getBoolean("quickshulker.debug.nested");

    private static void debug(ServerPlayer player, String message) {
        if (DEBUG) System.out.println("[QS-NEST] " + player.getName().getString() + ": " + message);
    }

    public static boolean isEmpty(ServerPlayer player) {
        Deque<Frame> stack = STACKS.get(player);
        return stack == null || stack.isEmpty();
    }

    /** TEMPORARY diagnostics hook used by the mixin while the stack is being traced. */
    public static void trace(ServerPlayer player, String message) {
        debug(player, message + " depth=" + depth(player) + " opening=" + isOpening(player) + " restoring=" + isRestoring(player));
    }

    public static void clear(ServerPlayer player) {
        STACKS.remove(player);
        RESTORING.remove(player);
    }

    /** Puts the previous level back. Returns true when a level was restored. */
    public static boolean restoreOne(ServerPlayer player) {
        Deque<Frame> stack = STACKS.get(player);
        if (stack == null || stack.isEmpty()) {
            debug(player, "restore skipped: stack empty");
            return false;
        }
        Frame frame = stack.pop();
        debug(player, "restore kind=" + frame.kind + " rows=" + frame.rows + " depth=" + stack.size());
        RESTORING.add(player);
        try {
            if (frame.kind == Frame.KIND_INVENTORY || frame.container == null) {
                OpenInventoryPacket.send(player);
            } else if (frame.kind == Frame.KIND_SHULKER) {
                openShulker(player, frame.container);
            } else {
                openChest(player, frame.container, frame.rows);
            }
        } finally {
            RESTORING.remove(player);
        }
        return true;
    }

    private static void openShulker(ServerPlayer player, Container container) {
        player.openMenu(new SimpleMenuProvider(
                (windowId, inventory, owner) -> new ShulkerBoxMenu(windowId, inventory, container),
                Component.translatable("container.shulkerBox")));
    }

    private static void openChest(ServerPlayer player, Container container, int rows) {
        int clamped = Math.max(1, Math.min(6, rows <= 0 ? 3 : rows));
        MenuType<ChestMenu> type = switch (clamped) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
        player.openMenu(new SimpleMenuProvider(
                (windowId, inventory, owner) -> new ChestMenu(type, windowId, inventory, container, clamped),
                Component.translatable("container.chest")));
    }
}
