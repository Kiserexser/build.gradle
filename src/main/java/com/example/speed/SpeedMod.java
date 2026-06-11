package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class SpeedMod implements ModInitializer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean enabled = false;
    private static boolean lastR = false;
    private static long lastClimbTime = 0;
    private static final long CLIMB_DELAY = 110; // мс

    @Override
    public void onInitialize() {
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                if (mc.player == null) continue;
                long window = mc.getWindow().getHandle();
                boolean currentR = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                if (currentR && !lastR) {
                    enabled = !enabled;
                    mc.player.sendMessage(Text.literal(enabled ? "§aWallClimb ON" : "§cWallClimb OFF"), true);
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
                lastR = currentR;
                if (enabled) tick();
            }
        }).start();
    }

    private static void tick() {
        if (mc.player == null) return;

        // Режим как в "FunTime"
        // При зажатой левой кнопке мыши имитируем приседание (необязательно, но оставим)
        if (mc.options.attackKey.isPressed()) {
            mc.options.sneakKey.setPressed(true);
        } else {
            mc.options.sneakKey.setPressed(false);
        }

        // Устанавливаем фиксированный угол поворота (по желанию)
        // mc.player.setYaw(75.0f); // закомментировано, так как мешает

        // Условия: касание стены, зажата атака, таймер прошел
        if (!mc.player.horizontalCollision) return;
        if (!mc.options.attackKey.isPressed()) return;
        long now = System.currentTimeMillis();
        if (now - lastClimbTime < CLIMB_DELAY) return;

        // Прыжок и спринт
        mc.player.setSprinting(true);
        mc.player.jump();

        // Поиск незерского кирпича в горячей панели
        int slot = findNetherBrickSlot();
        if (slot != -1) {
            // Ставим блок на стену
            placeBlockOnWall(slot);
            mc.player.fallDistance = 0.0f;
            lastClimbTime = now;
        } else {
            // Если нет кирпича, отключаем модуль и сообщаем в чат
            enabled = false;
            mc.player.sendMessage(Text.literal("§cНужен незерский кирпич в горячей панели!"), true);
        }
    }

    private static int findNetherBrickSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == Items.NETHER_BRICK) {
                return i;
            }
        }
        return -1;
    }

    private static void placeBlockOnWall(int slot) {
        // Сохраняем текущий слот
        int prevSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = slot;

        // Определяем блок перед игроком (на расстоянии 1 блок)
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVector();
        BlockPos targetPos = BlockPos.ofFloored(eye.add(look.multiply(1.0)));

        // Проверка, что блок можно поставить (воздух или замена)
        // Отправляем пакет установки блока (правый клик)
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(targetPos), Direction.UP, targetPos, false);
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hit, 0));

        // Возвращаем слот
        mc.player.getInventory().selectedSlot = prevSlot;
    }
}
