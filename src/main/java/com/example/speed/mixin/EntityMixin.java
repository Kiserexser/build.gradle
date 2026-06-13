package com.example.speed.mixin;

import com.example.speed.event.EventManager;
import com.example.speed.event.EventOnMovePost;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "updateVelocity", at = @At("TAIL"))
    private void onUpdateVelocity(float speed, Vec3d movementInput, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (MinecraftClient.getInstance().player != null && self == MinecraftClient.getInstance().player) {
            EventManager.post(new EventOnMovePost(speed, movementInput));
        }
    }
}
