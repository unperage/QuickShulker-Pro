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
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

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
    private static final int MAX_DEPTH = 16;

    public static boolean enabled() {
        return QuickShulkerMod.getConfig().nestedScreens;
    }

    public static boolean isRestoring(ServerPlayer player) {
        return RESTORING.contains(player);
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
    public static void captureCurrent(ServerPlayer player) {
        if (!enabled() || isRestoring(player)) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu instanceof ChestMenu chest && chest.getContainer() != null) {
            push(player, new Frame(Frame.KIND_CHEST, chest.getContainer(), chest.getRowCount()));
            return;
        }
        push(player, new Frame(Frame.KIND_INVENTORY, null, 0));
    }

    public static boolean isEmpty(ServerPlayer player) {
        Deque<Frame> stack = STACKS.get(player);
        return stack == null || stack.isEmpty();
    }

    public static void clear(ServerPlayer player) {
        STACKS.remove(player);
        RESTORING.remove(player);
    }

    /** Puts the previous level back. Returns true when a level was restored. */
    public static boolean restoreOne(ServerPlayer player) {
        Deque<Frame> stack = STACKS.get(player);
        if (stack == null || stack.isEmpty()) return false;
        Frame frame = stack.pop();
        RESTORING.add(player);
        try {
            if (frame.kind == Frame.KIND_INVENTORY || frame.container == null) {
                OpenInventoryPacket.send(player);
            } else {
                openChest(player, frame.container, frame.rows);
            }
        } finally {
            RESTORING.remove(player);
        }
        return true;
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
