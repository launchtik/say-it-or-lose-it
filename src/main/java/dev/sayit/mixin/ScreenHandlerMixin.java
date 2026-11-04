package dev.sayit.mixin;

import dev.sayit.server.ChallengeManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Items taken out of a container (chest, barrel, shulker box, hopper, furnace, villager trade, ...).
 * Every server-side inventory click is measured: what left the container's slots AND arrived in the
 * player's inventory or cursor counts as obtained. This covers click-take, shift-click, number-key swaps
 * and double-click collect. Crafting grids and result slots are excluded (crafting has its own trigger),
 * so moving your own items around, or in and out of a crafting grid, never asks.
 */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {
    @Shadow @Final public net.minecraft.util.collection.DefaultedList<Slot> slots;
    @Shadow public abstract ItemStack getCursorStack();

    @Unique private Map<Item, Integer> sayit$playerBefore;
    @Unique private Map<Item, Integer> sayit$containerBefore;

    @Inject(method = "onSlotClick", at = @At("HEAD"))
    private void sayit$beforeClick(int slot, int button, SlotActionType action, PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity)) return;
        sayit$playerBefore = sayit$countPlayer(player);
        sayit$containerBefore = sayit$countContainer(player);
    }

    @Inject(method = "onSlotClick", at = @At("RETURN"))
    private void sayit$afterClick(int slot, int button, SlotActionType action, PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity sp) || sayit$playerBefore == null) return;
        Map<Item, Integer> playerAfter = sayit$countPlayer(player);
        Map<Item, Integer> containerAfter = sayit$countContainer(player);
        for (Map.Entry<Item, Integer> e : playerAfter.entrySet()) {
            int gained = e.getValue() - sayit$playerBefore.getOrDefault(e.getKey(), 0);
            int left = sayit$containerBefore.getOrDefault(e.getKey(), 0) - containerAfter.getOrDefault(e.getKey(), 0);
            int taken = Math.min(gained, left);
            if (taken > 0) ChallengeManager.onObtained(sp, e.getKey(), taken, ChallengeManager.Source.CONTAINER);
        }
        sayit$playerBefore = null;
        sayit$containerBefore = null;
    }

    /** Player's own inventory (main, armor, offhand) plus the cursor. */
    @Unique
    private Map<Item, Integer> sayit$countPlayer(PlayerEntity player) {
        Map<Item, Integer> m = new HashMap<>();
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) sayit$add(m, inv.getStack(i));
        sayit$add(m, getCursorStack());
        return m;
    }

    /** Every slot of this screen that is not the player's inventory, a crafting grid or a crafting result. */
    @Unique
    private Map<Item, Integer> sayit$countContainer(PlayerEntity player) {
        Map<Item, Integer> m = new HashMap<>();
        for (Slot s : slots) {
            if (s.inventory instanceof PlayerInventory || s.inventory instanceof RecipeInputInventory
                    || s.inventory instanceof CraftingResultInventory) continue;
            sayit$add(m, s.getStack());
        }
        return m;
    }

    @Unique
    private static void sayit$add(Map<Item, Integer> m, ItemStack s) {
        if (!s.isEmpty()) m.merge(s.getItem(), s.getCount(), Integer::sum);
    }
}
