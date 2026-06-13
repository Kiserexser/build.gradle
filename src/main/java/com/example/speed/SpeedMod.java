package com.example.speed;

import com.example.speed.event.*;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.CEntityActionPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean grimEnabled = false;
    private static int grimTicks = 0;
    private static int grimGroundTicks = 0;
    private static double lastRealY = 0;
    private static boolean wasGKeyPressed = false;

    @Override
    public void onInitialize() {
        LOGGER.info("Grim Fly Disabler loaded (замена Polar Fly)");

        // Регистрируем обработчик событий (если у тебя свой EventManager — замени на свой способ)
        EventManager.register(SpeedMod::onEvent);
    }

    private static void onEvent(Event event) {
        if (!grimEnabled) return;

        if (event instanceof EventOnMovePost) {
            handleMovePost();
        }
        if (event instanceof EventUpdate) {
            handleUpdate();
        }
        if (event instanceof EventPostMotion) {
            handlePostMotion();
        }
        if (event instanceof EventPacket) {
            handlePacket((EventPacket) event);
        }

        // Проверка нажатия клавиши G (в любом тике)
        checkToggle();
    }

    private static void checkToggle() {
        if (mc.player == null) return;
        long window = mc.getWindow().getHandle();
        boolean isPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_G) == GLFW.GLFW_PRESS;
        if (isPressed && !wasGKeyPressed) {
            grimEnabled = !grimEnabled;
            mc.player.sendMessage(Text.literal("§6Grim Fly §7» §a" + (grimEnabled ? "ON" : "OFF")), true);
            if (grimEnabled) {
                lastRealY = mc.player.getY();
                grimTicks = 0;
                grimGroundTicks = 0;
            }
        }
        wasGKeyPressed = isPressed;
    }

    private static void handleMovePost() {
        if (grimTicks > 5) {
            double bstHor = 0.025;
            double bstVer = 0.02;

            if (Math.random() < 0.6) {
                // Вертикаль
                if (Math.random() < 0.7) {
                    mc.player.setVelocity(mc.player.getVelocity().add(0, bstVer, 0));
                } else if (mc.player.getY() > lastRealY + 0.5) {
                    mc.player.setVelocity(mc.player.getVelocity().add(0, -bstVer * 0.5, 0));
                }

                // Горизонталь
                bstHor = 0.02 + Math.random() * 0.03;
                double yaw = Math.toRadians(PlayerUtil.getDirection(false));
                double xt = -Math.sin(yaw);
                double zt = Math.cos(yaw);
                if (!PlayerUtil.isMoving()) {
                    xt = 0; zt = 0; bstHor = 0;
                } else {
                    xt += (Math.random() - 0.5) * 0.1;
                    zt += (Math.random() - 0.5) * 0.1;
                    double len = Math.hypot(xt, zt);
                    if (len > 0.001) {
                        xt /= len; zt /= len;
                    }
                }
                mc.player.setVelocity(mc.player.getVelocity().add(xt * bstHor, 0, zt * bstHor));
            }
        }
        grimTicks++;
    }

    private static void handleUpdate() {
        boolean reallyOnGround = mc.player.isOnGround();
        if (reallyOnGround) grimGroundTicks++;
        else grimGroundTicks = 0;

        boolean hasBlockUnder = mc.world.getBlockState(mc.player.getBlockPos().down()).isSolid();
        if (hasBlockUnder && grimGroundTicks < 2) {
            mc.player.jump();
            grimGroundTicks += 2;
        }
    }

    private static void handlePostMotion() {
        if (grimTicks % 3 == 0 && Math.random() < 0.4) {
            mc.player.networkHandler.sendPacket(new CEntityActionPacket(mc.player, CEntityActionPacket.Action.START_FALL_FLYING));
        }
    }

    private static void handlePacket(EventPacket event) {
        if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            lastRealY = mc.player.getY();
            grimTicks = 0;
            grimGroundTicks = 0;
            mc.player.setVelocity(0, 0, 0);
        }
    }

    // Вспомогательные утилиты (если у тебя уже есть PlayerUtil — удали этот внутренний класс)
    public static class PlayerUtil {
        public static boolean isMoving() {
            return mc.player.forwardSpeed != 0 || mc.player.sidewaysSpeed != 0;
        }
        public static double getDirection(boolean toRadians) {
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
}
