package com.example.speed.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {
    private boolean flyEnabled = false;
    private double lastSentY = 0;
    private int tickCounter = 0;
    private boolean wasPressed = false;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        MinecraftClient mc = MinecraftClient.getInstance();

        long window = mc.getWindow().getHandle();
        boolean isPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_G) == GLFW.GLFW_PRESS;
        if (isPressed && !wasPressed) {
            flyEnabled = !flyEnabled;
            player.sendMessage(Text.literal("§6Fly Bypass §7» §a" + (flyEnabled ? "ON" : "OFF")), true);
            if (flyEnabled) {
                lastSentY = player.getY();
                tickCounter = 0;
            }
        }
        wasPressed = isPressed;

        if (!flyEnabled) return;

        // Управление полётом
        double forward = player.forwardSpeed;
        double strafe = player.sidewaysSpeed;
        double motionX = 0, motionZ = 0;
        if (forward != 0 || strafe != 0) {
            float yaw = player.getYaw() * 0.017453292f;
            motionX = (forward * Math.sin(yaw) - strafe * Math.cos(yaw)) * 0.6;
            motionZ = (forward * Math.cos(yaw) + strafe * Math.sin(yaw)) * 0.6;
        }
        double motionY = 0;
        if (mc.options.jumpKey.isPressed()) motionY = 0.4;
        if (mc.options.sneakKey.isPressed()) motionY = -0.4;
        player.setVelocity(motionX, motionY, motionZ);

        // Обход Polar (подделка пакетов)
        tickCounter++;
        boolean forceReal = (tickCounter % 20 == 0) && (Math.abs(player.getY() - lastSentY) > 0.3);
        double sendY = forceReal ? player.getY() : lastSentY;
        boolean sendOnGround = forceReal ? player.isOnGround() : true;

        PlayerMoveC2SPacket.PositionAndOnGround packet = new PlayerMoveC2SPacket.PositionAndOnGround(
                player.getX(), sendY, player.getZ(), sendOnGround
        );
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(packet);
        }

        if (!forceReal) {
            lastSentY = sendY;
        } else {
            lastSentY = player.getY();
        }
    }
}
