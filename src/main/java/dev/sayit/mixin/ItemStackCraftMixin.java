package dev.sayit.mixin;

import dev.sayit.server.ChallengeManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Crafting / smelting / stonecutting results: vanilla calls ItemStack#onCraftByPlayer when the player
 * takes a result out of an output slot (crafting table, 2x2 grid, furnace, stonecutter, ...).
 */
@Mixin(ItemStack.class)
public abstract class ItemStackCraftMixin {
    @Inject(method = "onCraftByPlayer", at = @At("HEAD"))
    private void sayit$onCraft(PlayerEntity player, int amount, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity sp) {
            ItemStack self = (ItemStack) (Object) this;
            if (!self.isEmpty()) ChallengeManager.onObtained(sp, self.getItem(), Math.max(1, amount), ChallengeManager.Source.CRAFT);
        }
    }
}
