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

package net.kyrptonaught.quickshulker.api;

import net.kyrptonaught.quickshulker.QuickShulkerMod;
import net.kyrptonaught.quickshulker.network.OpenInventoryPacket;
import net.kyrptonaught.quickshulker.util.NestedMenus;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.Slot;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

public class Util {

    public static void openItem(Player player, int invSlot) {
        if (ItemInventoryContainer.isMenuSlot(invSlot)) {
            openItemFromMenuSlot(player, ItemInventoryContainer.menuSlotOf(invSlot));
            return;
        }
        if (invSlot < 0 || invSlot >= player.containerMenu.slots.size()) {
            System.out.println("[QuickShulker Multi]: unknown slot opened");
            return;
        }
        openItem(player, invSlot, player.containerMenu.slots.get(invSlot).getContainerSlot());
    }

    /**
     * Opens an openable item that sits in any slot of the current menu instead of the player inventory: a
     * chest, an ender chest, or a container that was itself quick-opened. The stack is read from the menu
     * slot, and the used-slot marker stores that menu slot, so closing and the "item vanished" guard keep
     * working the same way they do for the inventory path.
     */
    public static void openItemFromMenuSlot(Player player, int menuSlotIndex) {
        if (menuSlotIndex < 0 || menuSlotIndex >= player.containerMenu.slots.size()) return;
        Slot sourceSlot = player.containerMenu.slots.get(menuSlotIndex);
        ItemStack stack = sourceSlot.getItem();
        if (stack.isEmpty()) return;
        int marker = ItemInventoryContainer.encodeMenuSlot(menuSlotIndex);
        if (QuickShulkerMod.getConfig().rightClickClose && marker == ((ItemInventoryContainer) player.containerMenu).QS$getUsedSlotInPlayerInv()) {
            ((ServerPlayer) player).closeContainer();
            OpenInventoryPacket.send((ServerPlayer) player);
            return;
        }
        QuickShulkerData qsData = QuickOpenableRegistry.getQuickie(stack.getItem());
        if (qsData == null) return;
        if (player instanceof ServerPlayer sp) NestedMenus.captureCurrent(sp);
        qsData.openConsumer.accept(player, stack);
        if (QuickShulkerMod.getConfig().playSound) playSound(player, qsData.getSound(stack, true));
        ((ItemInventoryContainer) player.containerMenu).QS$setUsedSlot(marker);
        player.containerMenu.addSlotListener(forceCloseScreenIfNotPresent(player, sourceSlot, stack));
    }

    /**
     * Re-opens a stack that sits in a plain {@link Container} - used when the nested screen stack restores a
     * previous level that was itself a quick-opened container.
     */
    public static void openItemFromContainer(ServerPlayer player, Container container, int containerSlot, boolean restoring) {
        if (container == null || containerSlot < 0 || containerSlot >= container.getContainerSize()) return;
        ItemStack stack = container.getItem(containerSlot);
        if (stack.isEmpty()) return;
        QuickShulkerData qsData = QuickOpenableRegistry.getQuickie(stack.getItem());
        if (qsData == null) return;
        if (!restoring) NestedMenus.captureCurrent(player);
        qsData.openConsumer.accept(player, stack);
        if (QuickShulkerMod.getConfig().playSound) playSound(player, qsData.getSound(stack, true));
    }

    public static void openItem(Player player, int invSlot, int playerInvIndex) {
        if (QuickShulkerMod.getConfig().rightClickClose && playerInvIndex == ((ItemInventoryContainer) player.containerMenu).QS$getUsedSlotInPlayerInv()) {
            ((ServerPlayer) player).closeContainer();
            OpenInventoryPacket.send((ServerPlayer) player);
            return;
        }
        ItemStack stack = player.getInventory().getItem(playerInvIndex);
        //stack.removeSubNbt(QuickShulkerMod.MOD_ID);
        QuickShulkerData qsData = QuickOpenableRegistry.getQuickie(stack.getItem());
        if (qsData != null) {
            if (player instanceof ServerPlayer sp) NestedMenus.captureCurrent(sp);
            qsData.openConsumer.accept(player, stack);
            if(QuickShulkerMod.getConfig().playSound) playSound(player, qsData.getSound(stack, true));
            ((ItemInventoryContainer) player.containerMenu).QS$setUsedSlot(playerInvIndex);
            player.containerMenu.addSlotListener(forceCloseScreenIfNotPresent(player, playerInvIndex, stack));
        }
    }

    public static Boolean isOpenableItem(ItemStack stack) {
        QuickShulkerData qsdata = QuickOpenableRegistry.getQuickie(stack.getItem());
        if (qsdata == null) return false;
        return qsdata.ignoreSingleStackCheck || stack.getCount() <= 1;
    }

    public static Container getQuickItemInventory(Player player, ItemStack stack) {
        QuickShulkerData qsData = QuickOpenableRegistry.getQuickie(stack.getItem());
        if (qsData != null) {
            if (qsData.supportsBundleing)
                return qsData.getInventory(player, stack);
        }
        return null;
    }

    public static boolean canOpenInHand(ItemStack stack) {
        QuickShulkerData qsData = QuickOpenableRegistry.getQuickie(stack.getItem());
        if (qsData != null) {
            return qsData.canOpenInHand;
        }
        return false;
    }

    public static void playOpenSound(Player player, int selectedInvIndex){
        ItemStack stack = player.getInventory().getItem(selectedInvIndex);
        QuickShulkerData qsData = QuickOpenableRegistry.getQuickie(stack.getItem());
        if (qsData != null) {
            playSound(player, qsData.getSound(stack, true));
        }
    }

    public static void playCloseSound(Player player, int usedSlot){
        ItemStack stack = getItemInUsedSlot(player, usedSlot);
        QuickShulkerData qsData = QuickOpenableRegistry.getQuickie(stack.getItem());
        if (qsData != null) {
            playSound(player, qsData.getSound(stack, false));
        }
    }

    /** Resolves a used-slot marker back to the stack it refers to (inventory slot or menu slot). */
    public static ItemStack getItemInUsedSlot(Player player, int usedSlot) {
        if (ItemInventoryContainer.isMenuSlot(usedSlot)) {
            int index = ItemInventoryContainer.menuSlotOf(usedSlot);
            if (index < 0 || index >= player.containerMenu.slots.size()) return ItemStack.EMPTY;
            return player.containerMenu.slots.get(index).getItem();
        }
        if (usedSlot < 0 || usedSlot >= player.getInventory().getContainerSize()) return ItemStack.EMPTY;
        return player.getInventory().getItem(usedSlot);
    }

    public static void playSound(Player player, SoundEvent sound){
        if(sound != null){
//            player.level().playSound(null, player.blockPosition(), sound, player.getSoundSource());
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.BLOCKS);
        }
    }

    public static boolean areItemsEqual(ItemStack stack1, ItemStack stack2) {
        return ItemStack.isSameItem(stack1, stack2) && ItemStack.matches(stack1, stack2) && stack1.getCount() == stack2.getCount();
    }

    /** Same guard as the inventory variant, but for a stack that lives in another container. */
    public static ContainerListener forceCloseScreenIfNotPresent(Player player, Slot sourceSlot, ItemStack stack) {
        return new ContainerListener() {
            @Override
            public void slotChanged(@NotNull AbstractContainerMenu handler, int slotId, @NotNull ItemStack changed) {
                isValid();
            }

            @Override
            public void dataChanged(@NotNull AbstractContainerMenu handler, int property, int value) {
                isValid();
            }

            public void isValid() {
                ItemStack stackInSlot = sourceSlot.getItem();
                if (stackInSlot.isEmpty() || !areItemsEqual(stack, stackInSlot)) {
                    ((ServerPlayer) player).closeContainer();
                }
            }
        };
    }

    public static ContainerListener forceCloseScreenIfNotPresent(Player player, int slotID, ItemStack stack) {        return new ContainerListener() {
            @Override
            public void slotChanged(@NotNull AbstractContainerMenu handler, int slotId, @NotNull ItemStack stack) {
                isValid();
            }

            @Override
            public void dataChanged(@NotNull AbstractContainerMenu handler, int property, int value) {
                isValid();
            }

            public void isValid() {
                ItemStack stackInSlot = player.getInventory().getItem(slotID);
                if (stackInSlot.isEmpty() || !areItemsEqual(stack, stackInSlot)) {
                    ((ServerPlayer) player).closeContainer();
                }
            }
        };
    }
}