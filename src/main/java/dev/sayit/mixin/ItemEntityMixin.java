package dev.sayit.mixin;

import dev.sayit.server.ChallengeManager;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ground pickups. The vanilla insert runs untouched (the item really goes into the inventory, as in
 * the video); we only measure how many were picked up in this event.
 *
 * <p>Careful: when a pickup empties the entity, vanilla calls {@code stack.setCount(original)} right after
 * {@code sendPickup} (for the pickup animation and stats), so comparing counts at RETURN sees no change.
 * We therefore measure at the {@code sendPickup} call (full pickups) and fall back to RETURN for partial
 * pickups into a nearly full inventory, where vanilla does not call sendPickup or restore the count.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Shadow public abstract ItemStack getStack();

    @Unique private Item sayit$item;
    @Unique private int sayit$countBefore;
    @Unique private boolean sayit$reported;

    @Inject(method = "onPlayerCollision", at = @At("HEAD"))
    private void sayit$before(PlayerEntity player, CallbackInfo ci) {
        ItemStack s = getStack();
        sayit$item = s.isEmpty() ? null : s.getItem();
        sayit$countBefore = s.getCount();
        sayit$reported = false;
    }

    @Inject(method = "onPlayerCollision", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerEntity;sendPickup(Lnet/minecraft/entity/Entity;I)V"))
    private void sayit$atPickup(PlayerEntity player, CallbackInfo ci) {
        sayit$report(player);
    }

    @Inject(method = "onPlayerCollision", at = @At("RETURN"))
    private void sayit$after(PlayerEntity player, CallbackInfo ci) {
        sayit$report(player);
        sayit$item = null;
    }

    @Unique
    private void sayit$report(PlayerEntity player) {
        if (sayit$reported || sayit$item == null || !(player instanceof ServerPlayerEntity sp)) return;
        ItemStack s = getStack();
        int picked = sayit$countBefore - (s.isEmpty() ? 0 : s.getCount());
        if (picked <= 0) return;
        sayit$reported = true;
        ChallengeManager.onObtained(sp, sayit$item, picked, ChallengeManager.Source.PICKUP);
    }
}
