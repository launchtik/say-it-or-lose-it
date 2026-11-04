package dev.sayit.server;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/** Deletes items of one type from everything the player personally holds. */
public final class InventoryRevoker {
    private InventoryRevoker() {}

    /** @return number of items deleted (up to {@code max}). */
    public static int revoke(ServerPlayerEntity player, Item item, int max) {
        int[] left = {max};
        int removed = 0;

        // Cursor first (e.g. a crafting result still held on the mouse).
        ItemStack cursor = player.currentScreenHandler.getCursorStack();
        removed += take(cursor, item, left);
        if (cursor.isEmpty()) player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);

        PlayerInventory inv = player.getInventory();
        removed += takeAll(inv, item, left);
        // The player's own 2x2 crafting grid.
        removed += takeAll(player.playerScreenHandler.getCraftingInput(), item, left);

        if (removed > 0) {
            inv.markDirty();
            player.currentScreenHandler.sendContentUpdates();
            player.playerScreenHandler.sendContentUpdates();
        }
        return removed;
    }

    private static int takeAll(Inventory inv, Item item, int[] left) {
        int removed = 0;
        for (int i = 0; i < inv.size() && left[0] > 0; i++) {
            ItemStack s = inv.getStack(i);
            int r = take(s, item, left);
            if (r > 0) {
                removed += r;
                if (s.isEmpty()) inv.setStack(i, ItemStack.EMPTY);
            }
        }
        return removed;
    }

    private static int take(ItemStack s, Item item, int[] left) {
        if (s.isEmpty() || !s.isOf(item) || left[0] <= 0) return 0;
        int n = Math.min(s.getCount(), left[0]);
        s.decrement(n);
        left[0] -= n;
        return n;
    }
}
