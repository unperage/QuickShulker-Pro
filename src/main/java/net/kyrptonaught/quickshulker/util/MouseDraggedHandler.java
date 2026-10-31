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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

public class MouseDraggedHandler {
    private static final Set<Slot> DRAGGED_SLOTS = Sets.newLinkedHashSet();

    private static DragMode dragMode;
    /**
     * Slots the pointer has passed over but not applied yet. They are applied on the next rendered frame:
     * issuing the click from inside {@code mouseClicked}/{@code mouseDragged} runs against a menu vanilla is
     * still updating, so the click lands on the wrong slot and the one the pointer started on is lost.
     */
    private static final Deque<Slot> PENDING = new ArrayDeque<>();

    /** Opt-in tracing: start the client with -Dquickshulker.debug.drag=true. */
    public static final boolean DEBUG = Boolean.getBoolean("quickshulker.debug.drag");

    public static void dbg(String message) {
        if (DEBUG) System.out.println("[QS-DRAG] " + message);
    }

    /** TEMPORARY diagnostic helper. */
    public static String desc(ItemStack stack) {
        if (stack == null) return "null";
        return (stack.isEmpty() ? "empty" : stack.getItem().getDescriptionId()) + " x" + stack.getCount();
    }

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
        dbg("afterClick: isQuickCrafting=" + invoker.QS$isQuickCrafting()
                + " quickCraftSlots=" + (invoker.QS$getQuickCraftSlots() == null ? "null" : invoker.QS$getQuickCraftSlots().size())
                + " carried=" + desc(screen.getMenu().getCarried()));
        // Nothing to add here on purpose. Vanilla's quick-craft set means "spread the carried stack across these
        // slots", which is the opposite of what a QuickShulker drag does (move those slots' contents into the
        // carried box). Registering the starting slot there made vanilla hand the box itself to that slot on
        // release - the "it goes in and immediately comes back out" behaviour, and in creative it dropped the box.
    }

    /**
     * Applies the active drag to a single slot. Shared by the press that opens the drag and by every slot
     * the cursor passes over while the button is held, so the stack the drag started on is not skipped.
     */
    private static boolean executeSlot(AbstractContainerScreen<?> screen, Slot slot, ItemStack itemStack, int rawButton) {
        if (dragMode == null || slot == null) return false;
        Minecraft client = ((ScreenAccessor) screen).getClient();
        if (client.player == null) return false;
        AbstractContainerMenu handler = screen.getMenu();
        if (!handler.canDragTo(slot) && !slot.mayPickup(client.player)) {
            dbg("apply: slot " + slot.index + " cannot drag or pickup");
            return false;
        }
        boolean bundle = dragMode == DragMode.BUNDLE
                && slot.hasItem()
                && canInsertIntoContainer(client.player, itemStack, slot.getItem())
                && !ShulkerUtils.isShulkerItem(slot.getItem());
        boolean unbundle = dragMode == DragMode.UNBUNDLE
                && !slot.hasItem()
                && !isContainerEmpty(client.player, itemStack);
        if (!bundle && !unbundle) {
            dbg("apply: slot " + slot.index + " rejected mode=" + dragMode + " hasItem=" + slot.hasItem()
                    + " carried=" + desc(itemStack) + " slotItem=" + desc(slot.getItem()));
            return false;
        }
        DRAGGED_SLOTS.add(slot);
        AbstractContainerMenu playerMenu = client.player.containerMenu;
        dbg("execute: slot " + slot.index + " " + (bundle ? "BUNDLE" : "UNBUNDLE") + " raw=" + rawButton + " -> containerButton=" + toContainerButton(rawButton)
                + " screenMenuId=" + handler.containerId + " playerMenuId=" + playerMenu.containerId
                + " slotHasItem=" + slot.hasItem() + " carried=" + desc(itemStack));
        // Send through the player's live menu id. The screen's own menu can still carry the generic id 0, and a
        // click packet whose container id does not match the server menu is silently dropped - which is exactly
        // why the slot the drag started on never changed.
        //#if MC >= 26.1
        //$$ client.gameMode.handleContainerInput(playerMenu.containerId, slot.index, toContainerButton(rawButton), ContainerInput.PICKUP, client.player);
        //#elseif MC >= 1.21.10
        client.gameMode.handleInventoryMouseClick(playerMenu.containerId, slot.index, toContainerButton(rawButton), ClickType.PICKUP, client.player);
        //#else
        //$$ client.gameMode.handleInventoryMouseClick(playerMenu.containerId, slot.index, toContainerButton(rawButton), ClickType.PICKUP, client.player);
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
        // Set the mode here but let the press through: vanilla's own click handles the starting slot.
        if (!QuickShulkerMod.getConfig().supportsMouseDragged) return false;
        Minecraft client = ((ScreenAccessor) screen).getClient();
        if (client.player == null) return false;
        //#if MC >= 1.21.10
        if (click.button() != InputConstants.MOUSE_BUTTON_RIGHT) return false;
        Slot pressed = ((AbstractContainerScreenInvoker) screen).QS$getSlotAt(click.x(), click.y());
        //#else
        //$$ if (button != 1) return false;
        //$$ Slot pressed = ((AbstractContainerScreenInvoker) screen).QS$getSlotAt(mouseX, mouseY);
        //#endif
        if (pressed == null) return false;
        if (Util.getQuickItemInventory(client.player, screen.getMenu().getCarried()) == null) return false;
        dragMode = pressed.hasItem() ? DragMode.BUNDLE : DragMode.UNBUNDLE;
        DRAGGED_SLOTS.clear();
        PENDING.clear();
        dbg("press: mode=" + dragMode + " start=" + pressed.index);
        return false;
    }

    //#if MC >= 1.21.10
    public static boolean beforeMouseDragged(AbstractContainerScreen<?> screen, MouseButtonEvent click){
    //#else
    //$$ public static boolean beforeMouseDragged(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button) {
    //#endif
        //#if MC >= 1.21.10
        if (dragMode == null) return false;
        if (click.button() != InputConstants.MOUSE_BUTTON_RIGHT) {
            dragMode = null;
            DRAGGED_SLOTS.clear();
            return false;
        }
        AbstractContainerScreenInvoker invoker = (AbstractContainerScreenInvoker) screen;
        // Resolve the slot from the event coordinates, not from the screen's hoveredSlot field: vanilla only
        // refreshes that field on a move event, so during a drag it still points at the slot the press landed on -
        // which is why every dragged step was applied to the first slot instead of the one under the pointer.
        Slot slot = invoker.QS$getSlotAt(click.x(), click.y());
        dbg("drag: x=" + click.x() + " y=" + click.y() + " slot=" + (slot == null ? "null" : slot.index));
        if (slot != null && !DRAGGED_SLOTS.contains(slot) && screen.getMenu().canDragTo(slot)) {
            boolean applied = executeSlot(screen, slot, screen.getMenu().getCarried(), click.button());
            dbg("drag: applied slot=" + slot.index + " -> " + applied);
        }
        // Always swallow the motion while a QuickShulker drag is active. Returning false handed the event back to
        // vanilla, whose quick-craft pass then clicked the same slot a second time - by then the stack was already
        // inside the shulker, so that second click read as "take it back out". That is the insert/extract pair
        // seen for every dragged slot.
        return true;
        //#else
        //$$ return false;
        //#endif
    }

    //#if MC >= 1.21.10
    public static boolean beforeMouseReleased(AbstractContainerScreen<?> screen, MouseButtonEvent click){
    //#else
    //$$ public static boolean beforeMouseReleased(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button) {
    //#endif
        dragMode = null;
        PENDING.clear();
        DRAGGED_SLOTS.clear();
        BundleHelper.clearJustBundled();
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
