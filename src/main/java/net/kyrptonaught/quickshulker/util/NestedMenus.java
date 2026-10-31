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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Keeps, per server player, the screens a quick-opened container was opened from, so closing that container can
 * put the previous one back instead of dropping the player into the world.
 *
 * <p>Only the server decides here: re-opening a level either sends the "open your inventory" packet or opens a
 * block container again, which makes the client show the right screen on its own.
 */
public final class NestedMenus {

    private NestedMenus() {
    }

    /** What sits underneath a quick-opened container. */
    public static final class Frame {
        static final int KIND_INVENTORY = 0;
        static final int KIND_CONTAINER = 1;
        static final int KIND_BLOCK = 2;

        final int kind;
        final Container container;
        final int containerSlot;
        final BlockPos pos;

        private Frame(int kind, Container container, int containerSlot, BlockPos pos) {
            this.kind = kind;
            this.container = container;
            this.containerSlot = containerSlot;
            this.pos = pos;
        }

        static Frame inventory() {
            return new Frame(KIND_INVENTORY, null, -1, null);
        }

        static Frame container(Container container, int slot) {
            return new Frame(KIND_CONTAINER, container, slot, null);
        }

        static Frame block(BlockPos pos) {
            return new Frame(KIND_BLOCK, null, -1, pos);
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

    public static void pushInventory(ServerPlayer player) {
        push(player, Frame.inventory());
    }

    public static void pushContainer(ServerPlayer player, Container container, int containerSlot) {
        push(player, Frame.container(container, containerSlot));
    }

    public static void pushBlock(ServerPlayer player, BlockPos pos) {
        push(player, Frame.block(pos));
    }

    public static boolean isEmpty(ServerPlayer player) {
        Deque<Frame> stack = STACKS.get(player);
        return stack == null || stack.isEmpty();
    }

    public static void clear(ServerPlayer player) {
        STACKS.remove(player);
        RESTORING.remove(player);
    }

    /** Puts one level back. Returns true when a level was restored. */
    public static boolean restoreOne(ServerPlayer player) {
        Deque<Frame> stack = STACKS.get(player);
        if (stack == null || stack.isEmpty()) return false;
        Frame frame = stack.pop();
        RESTORING.add(player);
        try {
            switch (frame.kind) {
                case Frame.KIND_INVENTORY -> OpenInventoryPacket.send(player);
                case Frame.KIND_CONTAINER -> net.kyrptonaught.quickshulker.api.Util
                        .openItemFromContainer(player, frame.container, frame.containerSlot, true);
                case Frame.KIND_BLOCK -> {
                    BlockEntity blockEntity = player.level().getBlockEntity(frame.pos);
                    if (blockEntity instanceof MenuProvider provider) {
                        player.openMenu(provider);
                    } else {
                        OpenInventoryPacket.send(player);
                    }
                }
                default -> {
                }
            }
        } finally {
            RESTORING.remove(player);
        }
        return true;
    }
}
