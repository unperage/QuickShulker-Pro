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

package net.kyrptonaught.quickshulker.client;

import com.mojang.blaze3d.platform.Window;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kyrptonaught.quickshulker.QuickShulkerMod;
import net.kyrptonaught.quickshulker.mixin.minecraft.AbstractContainerScreenInvoker;
import net.kyrptonaught.quickshulker.mixin.minecraft.MouseHandlerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
//#if MC < 26.3
import org.lwjgl.glfw.GLFW;
//#else
//$$ import org.lwjgl.sdl.SDLMouse;
//#endif

/**
 * Puts the cursor back on the item the player was pointing at when a quick switch is triggered.
 * <p>
 * Restoring the raw screen position is not enough: the player inventory panel slides sideways when the recipe
 * book is open, so the same coordinates land on a neighbouring slot and the item has to be re-found by hand.
 * Instead the player-inventory slot index is remembered and re-resolved against the slots of whichever screen
 * opens next, so the cursor follows the item regardless of layout.
 * <p>
 * The move is applied on a rendered frame and its result is checked afterwards, because the panel of the new
 * screen is not final when {@code init} returns — {@code InventoryScreen} and {@code CraftingScreen} only
 * assign {@code leftPos} at the end of the {@code AbstractRecipeBookScreen} init chain, and the recipe book
 * can move the panel again after that.
 */
@Environment(EnvType.CLIENT)
public final class CursorFollower {

    /** Slots are 16x16; a remembered offset outside that came from reading the layout while it was moving. */
    private static final double SLOT_SIZE = 16.0;

    /** How many rendered frames one screen may take before the follow is dropped. */
    private static final int MAX_FRAMES = 30;

    /** How many moves one screen may get, so a layout that keeps shifting cannot loop. */
    private static final int MAX_ATTEMPTS = 3;

    private static AbstractContainerScreen<?> pendingScreen;
    private static Slot targetSlot;
    private static int frames;
    private static int attemptsLeft;
    private static boolean applied;
    private static double settledX, settledY;
    private static boolean settled;

    private CursorFollower() {
    }

    /**
     * Record the cursor position and which player-inventory slot it is over. Called from the click that
     * triggers a quick switch. The position is stored as an offset <em>inside</em> that slot, in GUI-scaled
     * units, so it survives the inventory panel moving sideways.
     */
    public static void remember(AbstractContainerScreen<?> screen, Slot slot) {
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();
        double scaleX = (double) window.getScreenWidth() / window.getGuiScaledWidth();
        double scaleY = (double) window.getScreenHeight() / window.getGuiScaledHeight();
        AbstractContainerScreenInvoker invoker = (AbstractContainerScreenInvoker) screen;

        QuickShulkerMod.lastMouseX = minecraft.mouseHandler.xpos();
        QuickShulkerMod.lastMouseY = minecraft.mouseHandler.ypos();
        QuickShulkerMod.lastCursorSlot = ClientUtil.getPlayerInvSlot(invoker.QS$getMenu(), slot);
        QuickShulkerMod.lastCursorOffsetX = QuickShulkerMod.lastMouseX / scaleX - (invoker.QS$getLeftPos() + slot.x);
        QuickShulkerMod.lastCursorOffsetY = QuickShulkerMod.lastMouseY / scaleY - (invoker.QS$getTopPos() + slot.y);
    }

    /**
     * Records where the pointer is without attaching it to a slot.
     * <p>
     * Used when the opened container came from another container: there is no matching slot on the screen that
     * is about to open, but the pointer should still stay where the player left it instead of being reset to the
     * centre of the window when the new screen grabs the mouse.
     */
    public static void rememberPosition() {
        if (!QuickShulkerMod.getConfig().cursorFollow) return;
        Minecraft minecraft = Minecraft.getInstance();
        QuickShulkerMod.lastMouseX = minecraft.mouseHandler.xpos();
        QuickShulkerMod.lastMouseY = minecraft.mouseHandler.ypos();
        QuickShulkerMod.lastCursorSlot = -1;
        QuickShulkerMod.lastCursorOffsetX = 0;
        QuickShulkerMod.lastCursorOffsetY = 0;
    }

    /** Marks the screen that just finished initialising as the one to move the cursor on. */
    public static void beginSnap(AbstractContainerScreen<?> screen) {
        if (!QuickShulkerMod.getConfig().cursorFollow) return;
        if (QuickShulkerMod.lastCursorSlot < 0
                && QuickShulkerMod.lastMouseX == 0 && QuickShulkerMod.lastMouseY == 0)
            return;
        pendingScreen = screen;
        targetSlot = null;
        frames = 0;
        attemptsLeft = MAX_ATTEMPTS;
        applied = false;
        settled = false;
    }

    /**
     * Runs once per rendered frame of the pending screen.
     *
     * @param hoveredSlot the slot the screen itself considers hovered, i.e. the result of the last move,
     *                    which is what decides whether another attempt is needed
     */
    public static void pump(AbstractContainerScreen<?> screen, Slot hoveredSlot) {
        if (pendingScreen != screen) return;

        if (applied) {
            // Check the outcome instead of trusting the coordinates: the screen computes its hover from the
            // position that was written, so this catches a panel that moved after the move was made.
            if (targetSlot == null || hoveredSlot == targetSlot) {
                reset();
                return;
            }
            if (attemptsLeft <= 0) {
                reset();
                return;
            }
            applied = false;
            settled = false;
        }

        if (++frames > MAX_FRAMES) {
            reset();
            return;
        }

        double[] target = targetOf(screen);
        if (target == null) return;   // nothing to follow (yet): try again on the next frame

        if (settled && target[0] == settledX && target[1] == settledY) {
            move(target[0], target[1]);
            return;
        }
        settledX = target[0];
        settledY = target[1];
        settled = true;
    }

    /**
     * Where the cursor belongs on this screen, in window coordinates, or null while the screen cannot answer.
     * <p>
     * The screen's own slot lookup decides whether a position is usable, so a panel that is not laid out yet
     * (or an offset that cannot be trusted) means "ask again next frame" instead of a move to a guessed spot.
     */
    private static double[] targetOf(AbstractContainerScreen<?> screen) {
        AbstractContainerScreenInvoker invoker = (AbstractContainerScreenInvoker) screen;

        if (QuickShulkerMod.lastCursorSlot >= 0) {
            int wanted = QuickShulkerMod.lastCursorSlot;
            for (Slot slot : invoker.QS$getMenu().slots) {
                if (slot.container instanceof Inventory && slot.getContainerSlot() == wanted) {
                    double left = invoker.QS$getLeftPos();
                    double top = invoker.QS$getTopPos();
                    // Re-apply the remembered in-slot offset rather than the slot centre, so going in and
                    // back does not move the pointer around. Clamped to the cell: an offset remembered while
                    // the layout was still moving can be far bigger than a slot.
                    double guiX = left + slot.x + clamp(QuickShulkerMod.lastCursorOffsetX);
                    double guiY = top + slot.y + clamp(QuickShulkerMod.lastCursorOffsetY);
                    if (invoker.QS$getSlotAt(guiX, guiY) != slot) {
                        guiX = left + slot.x + SLOT_SIZE / 2.0;
                        guiY = top + slot.y + SLOT_SIZE / 2.0;
                        if (invoker.QS$getSlotAt(guiX, guiY) != slot) return null;   // panel not laid out yet
                    }
                    targetSlot = slot;
                    Window window = Minecraft.getInstance().getWindow();
                    double scaleX = (double) window.getScreenWidth() / window.getGuiScaledWidth();
                    double scaleY = (double) window.getScreenHeight() / window.getGuiScaledHeight();
                    return new double[]{guiX * scaleX, guiY * scaleY};
                }
            }
            // No such slot here (the item is not in the player inventory on this screen), so let the raw
            // position below take over.
            QuickShulkerMod.lastCursorSlot = -1;
        }

        if (QuickShulkerMod.lastMouseX != 0 || QuickShulkerMod.lastMouseY != 0) {
            targetSlot = null;
            return new double[]{QuickShulkerMod.lastMouseX, QuickShulkerMod.lastMouseY};
        }
        return null;
    }

    /** Keeps a remembered offset inside its cell. */
    private static double clamp(double offset) {
        return Math.max(0.0, Math.min(SLOT_SIZE - 0.01, offset));
    }

    private static void move(double windowX, double windowY) {
        warp(windowX, windowY);
        applied = true;
        attemptsLeft--;
    }

    /** Forgets the follow, so nothing is applied to whatever screen comes next. */
    private static void reset() {
        pendingScreen = null;
        targetSlot = null;
        frames = 0;
        attemptsLeft = 0;
        applied = false;
        settled = false;
        QuickShulkerMod.lastCursorSlot = -1;
        QuickShulkerMod.lastCursorOffsetX = 0;
        QuickShulkerMod.lastCursorOffsetY = 0;
        QuickShulkerMod.lastMouseX = 0;
        QuickShulkerMod.lastMouseY = 0;
    }

    /**
     * Moves the cursor to a position given in window coordinates (not GUI-scaled ones).
     * <p>
     * The game's own cursor is written first, because that is what the GUI's slot hover is computed from, and
     * it takes effect on this frame (the window system call only lands on some later input event, if at all).
     * The platform call is kept as a best-effort extra so the real pointer stays in sync on desktop; where it
     * does nothing (touch launchers such as FCL / PojavLauncher emulate the pointer themselves) the write is
     * still enough to move the hover.
     */
    private static void warp(double windowX, double windowY) {
        MouseHandler mouseHandler = Minecraft.getInstance().mouseHandler;
        ((MouseHandlerAccessor) mouseHandler).QS$setXpos(windowX);
        ((MouseHandlerAccessor) mouseHandler).QS$setYpos(windowY);

        //#if MC >= 26.3
        //$$ SDLMouse.SDL_WarpMouseInWindow(Minecraft.getInstance().getWindow().handle(), (float) windowX, (float) windowY);
        //#elseif MC >= 1.21.10
        GLFW.glfwSetCursorPos(Minecraft.getInstance().getWindow().handle(), windowX, windowY);
        //#else
        //$$ GLFW.glfwSetCursorPos(Minecraft.getInstance().getWindow().getWindow(), windowX, windowY);
        //#endif
    }
}
