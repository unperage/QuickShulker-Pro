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

public interface ItemInventoryContainer {

    /** No container is associated with this menu. */
    int QS_NO_SLOT = -1;

    int QS$getUsedSlotInPlayerInv();

    default boolean hasItem() {
        return QS$getUsedSlotInPlayerInv() != QS_NO_SLOT;
    }

    void QS$setUsedSlot(int playerInvSlotID);

    /**
     * True when this menu was opened by QuickShulker. Menus for blocks (crafting table, anvil, ...) validate the
     * player's position against a real block, so a menu opened from an item has to be allowed through explicitly.
     */
    boolean QS$isQuickOpened();

    void QS$setQuickOpened(boolean opened);

    /**
     * A slot inside the player inventory is stored as-is (>= 0). A slot that lives in another container -
     * a chest, an ender chest, a shulker the player already has open - cannot collide with those, so it is
     * stored as a negative number: {@code -(menuSlotIndex + 2)}. {@code -1} stays reserved for "no container".
     */
    static int encodeMenuSlot(int menuSlotIndex) {
        return -(menuSlotIndex + 2);
    }

    static boolean isMenuSlot(int usedSlot) {
        return usedSlot <= -2;
    }

    static int menuSlotOf(int usedSlot) {
        return -usedSlot - 2;
    }

}
