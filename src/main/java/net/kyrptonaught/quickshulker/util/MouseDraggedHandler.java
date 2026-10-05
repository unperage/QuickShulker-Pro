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

import com.google.common.collect.Sets;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.mixin.screen.ScreenAccessor;
import net.kyrptonaught.quickshulker.QuickShulkerMod;
import net.kyrptonaught.quickshulker.api.Util;
import net.kyrptonaught.quickshulker.mixin.minecraft.AbstractContainerScreenInvoker;
import net.kyrptonaught.shulkerutils.ShulkerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
//#if MC >= 26.1
//$$ import net.minecraft.world.inventory.ContainerInput;
//#else
import net.minecraft.world.inventory.ClickType;
//#endif

//#if MC >= 1.21.10
import net.minecraft.client.input.MouseButtonEvent;
//#else
//#endif

import java.util.Set;

public class MouseDraggedHandler {
    private static final Set<Slot> DRAGGED_SLOTS = Sets.newHashSet();
    private static DragMode dragMode;

    public static boolean canInsertIntoContainer(Player player, ItemStack hostStack, ItemStack insertStack) {
        Container inv = Util.getQuickItemInventory(player, hostStack);
        if (inv == null) return false;
        for (int i = inv.getContainerSize() - 1; i >= 0; i--) {
            ItemStack pickStack = inv.getItem(i);
            if (pickStack.isEmpty() || (ItemStack.isSameItemSameComponents(pickStack, insertStack) && pickStack.getCount() < pickStack.getMaxStackSize()))
                return true;
        }
        return false;
    }

    public static boolean isContainerEmpty(Player player, ItemStack hostStack) {
        Container inv = Util.getQuickItemInventory(player, hostStack);
        if (inv != null) {
            return inv.isEmpty();
        }
        return true;
    }

    /**
     * Vanilla's own drag shortcut only ever collects slots from mouse-motion events: the press that opens a
     * drag clears {@code quickCraftSlots} and never registers the slot it landed on, so the stack the cursor
     * started from is left untouched until the pointer leaves it and comes back - "the first stack the
     * pointer passed over is always missed". Register that slot right after vanilla has set the drag up.
     */
    public static void afterMouseClick(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        AbstractContainerScreenInvoker invoker = (AbstractContainerScreenInvoker) screen;
        if (!invoker.QS$isQuickCrafting()) return;
        Set<Slot> quickCraftSlots = invoker.QS$getQuickCraftSlots();
        if (quickCraftSlots == null || !quickCraftSlots.isEmpty()) return;
        Slot slot = invoker.QS$getSlotAt(mouseX, mouseY);
        ItemStack carried = screen.getMenu().getCarried();
        if (slot == null || carried.isEmpty()) return;
        if (!AbstractContainerMenu.canItemQuickReplace(slot, carried, true) || !slot.mayPlace(carried) || !screen.getMenu().canDragTo(slot))
            return;
        if (quickCraftSlots.add(slot)) {
            invoker.QS$recalculateQuickCraftRemaining();
        }
    }

    /**
     * Applies the active drag to a single slot. Shared by the press that opens the drag and by every slot
     * the cursor passes over while the button is held, so the stack the drag started on is not skipped.
     */
    private static boolean applyDragAction(AbstractContainerScreen<?> screen, Slot slot, ItemStack itemStack, int rawButton) {
        if (dragMode == null || slot == null || DRAGGED_SLOTS.contains(slot)) return false;
        Minecraft client = ((ScreenAccessor) screen).getClient();
        if (client.player == null) return false;
        AbstractContainerMenu handler = screen.getMenu();
        if (!handler.canDragTo(slot) && !slot.mayPickup(client.player)) return false;
        boolean bundle = dragMode == DragMode.BUNDLE
                && slot.hasItem()
                && canInsertIntoContainer(client.player, itemStack, slot.getItem())
                && !ShulkerUtils.isShulkerItem(slot.getItem());
        boolean unbundle = dragMode == DragMode.UNBUNDLE
                && !slot.hasItem()
                && !isContainerEmpty(client.player, itemStack);
        if (!bundle && !unbundle) return false;
        DRAGGED_SLOTS.add(slot);
        //#if MC >= 26.1
        //$$ ((AbstractContainerScreenInvoker) screen).QS$onMouseClick(slot, slot.index, toContainerButton(rawButton), ContainerInput.PICKUP);
        //#elseif MC >= 1.21.10
        ((AbstractContainerScreenInvoker) screen).QS$onMouseClick(slot, slot.index, toContainerButton(rawButton), ClickType.PICKUP);
        //#else
        //$$ ((AbstractContainerScreenInvoker) screen).QS$onMouseClick(slot, slot.index, toContainerButton(rawButton), ClickType.PICKUP);
        //#endif
        return true;
    }

    /**
     * Translates a raw platform mouse button into the id the container click protocol uses (0 = left,
     * 1 = right). 26.3 moved the mouse layer from GLFW to SDL, which numbers the buttons differently
     * (left = 1, middle = 2, right = 3), and vanilla converts them back inside
     * {@code AbstractContainerScreen#getContainerClickButton} before it calls {@code slotClicked(...)}.
     * Anything calling that method directly has to translate as well: the raw SDL id is not a button the
     * PICKUP branch understands, so on 26.3 every dragged slot was silently a no-op.
     */
    //#if MC >= 26.3
    //$$ private static int toContainerButton(int rawButton) {
    //$$     if (rawButton == InputConstants.MOUSE_BUTTON_LEFT) return 0;
    //$$     if (rawButton == InputConstants.MOUSE_BUTTON_RIGHT) return 1;
    //$$     return rawButton;
    //$$ }
    //#else
    private static int toContainerButton(int rawButton) {
        return rawButton;
    }
    //#endif

    //#if MC >= 1.21.10
    public static boolean beforeMouseClick(AbstractContainerScreen<?> screen, MouseButtonEvent click){
    //#else
    //$$ public static boolean beforeMouseClick(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button) {
    //#endif
        if (!QuickShulkerMod.getConfig().supportsMouseDragged) return false;
        //#if MC >= 1.21.10
        Slot slot = ((AbstractContainerScreenInvoker) screen).QS$getSlotAt(click.x(), click.y());
        // Compare against the named constant rather than a literal 1: 26.3 moved the input layer to
        // SDL, where the right button is 3 (InputConstants.MOUSE_BUTTON_RIGHT) instead of GLFW's 1.
        // A hardcoded 1 therefore matched the LEFT button on 26.3 and let a plain left-click start a
        // bundle/unbundle drag, while the real right button could no longer start one at all.
        if(slot != null && click.button() == InputConstants.MOUSE_BUTTON_RIGHT){
        //#else
        //$$ Slot slot = ((AbstractContainerScreenInvoker) screen).QS$getSlotAt(mouseX, mouseY);
        //$$ if (slot != null && button == 1) {
        //#endif
            Minecraft client = ((ScreenAccessor) screen).getClient();
            ItemStack itemStack = screen.getMenu().getCarried();
            Container inv = Util.getQuickItemInventory(client.player, itemStack);
            if (inv == null) return false;
            if (slot.hasItem()) {
                dragMode = DragMode.BUNDLE;
            } else {
                dragMode = DragMode.UNBUNDLE;
            }
            DRAGGED_SLOTS.clear();
            // The press that opens the drag acts on its own slot right away. Slots are otherwise only
            // collected from mouse-motion events, so the stack the cursor started on would stay untouched
            // until the pointer left it and came back - the "first stack is always missed" behaviour.
            //#if MC >= 1.21.10
            applyDragAction(screen, slot, itemStack, click.button());
            //#else
            //$$ applyDragAction(screen, slot, itemStack, button);
            //#endif
            return true;
        }
        return false;
    }

    //#if MC >= 1.21.10
    public static boolean beforeMouseDragged(AbstractContainerScreen<?> screen, MouseButtonEvent click){
    //#else
    //$$ public static boolean beforeMouseDragged(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button) {
    //#endif
        if (!QuickShulkerMod.getConfig().supportsMouseDragged) return false;
        if (dragMode == null) return false;
        //#if MC >= 1.21.10
        if(click.button() != InputConstants.MOUSE_BUTTON_RIGHT){
        //#else
        //$$ if (button != 1) {
        //#endif
            dragMode = null;
            DRAGGED_SLOTS.clear();
            return false;
        }
        //#if MC >= 1.21.10
        Slot slot = ((AbstractContainerScreenInvoker) screen).QS$getSlotAt(click.x(), click.y());
        return applyDragAction(screen, slot, screen.getMenu().getCarried(), click.button());
        //#else
        //$$ Slot slot = ((AbstractContainerScreenInvoker) screen).QS$getSlotAt(mouseX, mouseY);
        //$$ return applyDragAction(screen, slot, screen.getMenu().getCarried(), button);
        //#endif
    }

    //#if MC >= 1.21.10
    public static boolean beforeMouseReleased(AbstractContainerScreen<?> screen, MouseButtonEvent click){
    //#else
    //$$ public static boolean beforeMouseReleased(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button) {
    //#endif
        if (!QuickShulkerMod.getConfig().supportsMouseDragged) return false;
        if (dragMode != null) {
            dragMode = null;
            //#if MC >= 1.21.10
            if(click.button() == InputConstants.MOUSE_BUTTON_RIGHT && !DRAGGED_SLOTS.isEmpty()){
            //#else
            //$$ if (button == 1 && !DRAGGED_SLOTS.isEmpty()) {
            //#endif
                DRAGGED_SLOTS.clear();
                return true;
            }
        }
        return false;
    }

    public static void beforeDrawForeground(AbstractContainerScreen<?> screen, GuiGraphics context, int mouseX, int mouseY) {
        AbstractContainerMenu handler = screen.getMenu();
        for (Slot slot : handler.slots) {
            if (DRAGGED_SLOTS.contains(slot)) {
                context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x80FFFFFF);
            }
        }
    }

    private enum DragMode {
        BUNDLE,
        UNBUNDLE
    }
}
