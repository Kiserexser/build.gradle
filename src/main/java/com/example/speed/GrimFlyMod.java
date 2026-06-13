package com.example.speed;

import com.example.speed.event.*;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.CEntityActionPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrimFlyMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("grimfly");
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean enabled = false;
    private static int ticks = 0;
    private static int groundTicks = 0;
    private static double lastRealY = 0;
    private static boolean wasPressed = false;

    @Override
    public void onInitialize() {
        LOGGER.info("Grim Fly Disabler loaded");
        EventManager.register(GrimFlyMod::onEvent);
    }

    private static void onEvent(Event event) {
        if (!enabled) {
            checkToggle();
            return;
        }

        if (event instanceof EventOnMovePost) handleMovePost();
        if (event instanceof EventUpdate) handleUpdate();
        if (event instanceof EventPostMotion) handlePostMotion();
        if (event instanceof EventPacket) handlePacket((EventPacket) event);
        checkToggle();
    }

    private static void checkToggle() {
        if (mc.player == null) return;
        long window = mc.getWindow().getHandle();
        boolean pressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_G) == GLFW.GLFW_PRESS;
        if (pressed && !wasPressed) {
            enabled = !enabled;
            mc.player.sendMessage(Text.literal("§6Grim Fly §7» §a" + (enabled ? "ON" : "OFF")), true);
            if (enabled) {
                lastRealY = mc.player.getY();
                ticks = 0;
                groundTicks = 0;
            }
        }
        wasPressed = pressed;
    }

    private static void handleMovePost() {
        if (ticks > 5) {
            if (Math.random() < 0.6) {
                if (Math.random() < 0.7) {
                    mc.player.setVelocity(mc.player.getVelocity().add(0, 0.02, 0));
                } else if (mc.player.getY() > lastRealY + 0.5) {
                    mc.player.setVelocity(mc.player.getVelocity().add(0, -0.01, 0));
                }
                double bstHor = 0.02 + Math.random() * 0.03;
                double yaw = Math.toRadians(getDirection(false));
                double xt = -Math.sin(yaw);
                double zt = Math.cos(yaw);
                if (mc.player.forwardSpeed == 0 && mc.player.sidewaysSpeed == 0) {
                    xt = 0; zt = 0; bstHor = 0;
                } else {
                    xt += (Math.random() - 0.5) * 0.1;
                    zt += (Math.random() - 0.5) * 0.1;
                    double len = Math.hypot(xt, zt);
                    if (len > 0.001) { xt /= len; zt /= len; }
                }
                mc.player.setVelocity(mc.player.getVelocity().add(xt * bstHor, 0, zt * bstHor));
            }
        }
        ticks++;
    }

    private static void handleUpdate() {
        boolean onGround = mc.player.isOnGround();
        if (onGround) groundTicks++; else groundTicks = 0;
        boolean hasBlockUnder = mc.world.getBlockState(mc.player.getBlockPos().down()).isSolid();
        if (hasBlockUnder && groundTicks < 2) {
            mc.player.jump();
            groundTicks += 2;
        }
    }

    private static void handlePostMotion() {
        if (ticks % 3 == 0 && Math.random() < 0.4) {
            mc.player.networkHandler.sendPacket(new CEntityActionPacket(mc.player, CEntityActionPacket.Action.START_FALL_FLYING));
        }
    }

    private static void handlePacket(EventPacket event) {
        if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            lastRealY = mc.player.getY();
            ticks = 0;
            groundTicks = 0;
            mc.player.setVelocity(0, 0, 0);
        }
    }

    private static double getDirection(boolean toRadians) {
        float yaw = mc.player.getYaw();
        float forward = mc.player.forwardSpeed;
        float strafe = mc.player.sidewaysSpeed;
        if (forward == 0 && strafe == 0) return toRadians ? Math.toRadians(yaw) : yaw;
        float bestYaw = yaw;
        if (forward != 0) {
            if (strafe > 0) bestYaw += (forward > 0 ? -45 : 45);
            else if (strafe < 0) bestYaw += (forward > 0 ? 45 : -45);
            else bestYaw += (forward > 0 ? 0 : 180);
        } else {
            bestYaw += (strafe > 0 ? -90 : 90);
        }
        return toRadians ? Math.toRadians(bestYaw) : bestYaw;
    }
}
