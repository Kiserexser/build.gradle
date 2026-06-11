package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.stream.Stream;

public class SpeedMod implements ModInitializer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean enabled = false;
    private static boolean lastR = false;

    // Режимы (переключайте здесь, 0..6)
    private static final int MODE = 0; // 0=FunTime, 1=FunSky, 2=FunTimeFly, 3=SlimeBlock, 4=SpookyTime, 5=GriefCarpet, 6=IceWalk

    // Таймеры
    private static long jumpDelay = 0;
    private static long carpetDelay = 0;
    private static long iceDelay = 0;
    private static long waterDelay = 0;
    private static int slimeJumpCooldown = 0;

    // Состояния
    private static long lastWaterBucketUse = 0;
    private static boolean touchingWall = false;
    private static int lastSlot = -1;

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
                    mc.player.sendMessage(Text.literal(enabled ? "§aSpider [" + getModeName() + "] ON" : "§cSpider OFF"), true);
                    if (!enabled) resetState();
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
                lastR = currentR;
                if (enabled) tick();
            }
        }).start();
    }

    private static String getModeName() {
        return switch (MODE) {
            case 0 -> "FunTime";
            case 1 -> "FunSky";
            case 2 -> "FunTimeFly";
            case 3 -> "SlimeBlock";
            case 4 -> "SpookyTime";
            case 5 -> "GriefCarpet";
            case 6 -> "IceWalk";
            default -> "Unknown";
        };
    }

    private static void resetState() {
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        lastWaterBucketUse = 0;
        touchingWall = false;
        slimeJumpCooldown = 0;
        jumpDelay = 0;
        carpetDelay = 0;
        iceDelay = 0;
        waterDelay = 0;
        if (lastSlot != -1) {
            mc.player.getInventory().selectedSlot = lastSlot;
            lastSlot = -1;
        }
    }

    private static void tick() {
        if (mc.player == null || mc.world == null) return;

        switch (MODE) {
            case 0 -> handleFunTime();
            case 1 -> handleFunSky();
            case 2 -> handleFunTimeFly();
            case 3 -> handleSlimeBlock();
            case 4 -> handleSpookyTime();
            case 5 -> handleGriefCarpet();
            case 6 -> handleIceWalk();
        }
    }

    // ==================== РЕЖИМ FunTime (пшеница) ====================
    private static void handleFunTime() {
        mc.options.jumpKey.setPressed(mc.options.forwardKey.isPressed());
        mc.player.setPitch(75.0f);
        if (!mc.player.horizontalCollision) return;
        if (!hasElapsed(jumpDelay, 300)) return;

        mc.player.setOnGround(true);
        mc.player.jump();

        int wheatSlot = findHotbarSlot(Items.WHEAT);
        if (wheatSlot != -1) {
            useItemOnCrosshair(wheatSlot, 75.0f);
            mc.player.fallDistance = 0;
            jumpDelay = System.currentTimeMillis();
        } else {
            mc.player.sendMessage(Text.literal("§cНужна пшеница!"), true);
            enabled = false;
        }
    }

    // ==================== РЕЖИМ FunSky (ведро воды, прижатие к стене) ====================
    private static void handleFunSky() {
        touchingWall = mc.player.horizontalCollision;
        if (!touchingWall) {
            mc.options.sneakKey.setPressed(false);
            return;
        }
        int waterSlot = findHotbarSlot(Items.WATER_BUCKET);
        if (waterSlot == -1) return;

        if (mc.player.isTouchingWater()) {
            mc.player.setVelocity(mc.player.getVelocity().x, 0.46, mc.player.getVelocity().z);
            return;
        }
        if (mc.player.isOnGround()) {
            lastWaterBucketUse = 0;
            jumpDelay = 0;
            return;
        }
        if (!hasElapsed(jumpDelay, 120)) return;
        long now = System.currentTimeMillis();
        if (now - lastWaterBucketUse < getWaterDelay()) return;

        mc.options.jumpKey.setPressed(true);
        useWaterBucketLookingDown(waterSlot);
        mc.options.sneakKey.setPressed(true);
        lastWaterBucketUse = now;
        jumpDelay = now;
    }

    private static long getWaterDelay() {
        double dist = getDistanceToGround();
        if (dist < 5) return 450;
        if (dist < 20) return 550;
        return 650;
    }

    private static double getDistanceToGround() {
        double y = mc.player.getY();
        for (double i = y; i > mc.world.getBottomY(); i -= 0.1) {
            BlockPos pos = BlockPos.ofFloored(mc.player.getX(), i, mc.player.getZ());
            if (!mc.world.getBlockState(pos).isAir()) return Math.max(y - (i + 1), 0);
        }
        return 0;
    }

    // ==================== РЕЖИМ FunTime Fly (громоотводы) ====================
    private static void handleFunTimeFly() {
        if (!mc.player.horizontalCollision) return;
        if (!hasElapsed(jumpDelay, 1)) return;

        mc.player.setOnGround(true);
        mc.player.jump();

        int rodSlot = ensureHotbarSlot(Items.LIGHTNING_ROD);
        if (rodSlot != -1) {
            placeLightningRodsAbovePlayer(rodSlot);
            mc.player.fallDistance = 0;
            jumpDelay = System.currentTimeMillis();
        } else {
            mc.player.sendMessage(Text.literal("§cНужен громоотвод!"), true);
            enabled = false;
        }
    }

    // ==================== РЕЖИМ Slime Block (слизневые блоки) ====================
    private static void handleSlimeBlock() {
        BlockPos playerPos = mc.player.getBlockPos();
        boolean slimeNearby = Stream.of(playerPos.east(), playerPos.west(), playerPos.north(), playerPos.south())
                .anyMatch(pos -> mc.world.getBlockState(pos).getBlock() == Blocks.SLIME_BLOCK);
        if (!slimeNearby || !mc.player.horizontalCollision || mc.player.getVelocity().y <= -1.0) return;

        if (!(mc.crosshairTarget instanceof BlockHitResult)) return;
        BlockHitResult hit = (BlockHitResult) mc.crosshairTarget;
        if (mc.world.getBlockState(hit.getBlockPos()).isAir()) return;

        int slimeSlot = findHotbarSlot(Items.SLIME_BLOCK);
        if (slimeSlot == -1) return;

        int prevSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = slimeSlot;
        mc.player.setPitch(54.0f);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.getInventory().selectedSlot = prevSlot;

        if (slimeJumpCooldown >= 1) {
            mc.player.setVelocity(mc.player.getVelocity().x, 0.63, mc.player.getVelocity().z);
            slimeJumpCooldown = 0;
        } else {
            slimeJumpCooldown++;
        }
    }

    // ==================== РЕЖИМ SpookyTime (ведро воды без задержки) ====================
    private static void handleSpookyTime() {
        int waterSlot = findHotbarSlot(Items.WATER_BUCKET);
        if (waterSlot == -1) return;
        if (!mc.player.horizontalCollision) return;

        int prevSlot = mc.player.getInventory().selectedSlot;
        if (waterSlot != prevSlot) mc.player.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(waterSlot));
        mc.player.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, mc.player.getYaw(), mc.player.getPitch()));
        if (waterSlot != prevSlot) mc.player.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
        mc.player.setVelocity(mc.player.getVelocity().x, 0.29, mc.player.getVelocity().z);
    }

    // ==================== РЕЖИМ Grief Carpet (ковры) ====================
    private static void handleGriefCarpet() {
        int carpetSlot = findHotbarCarpetSlot();
        if (carpetSlot == -1) return;

        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos belowPos = playerPos.down();
        if (!mc.player.isOnGround() && mc.world.getBlockState(playerPos).isAir() && !mc.world.getBlockState(belowPos).isAir()
                && hasElapsed(carpetDelay, 1)) {
            int prevSlot = mc.player.getInventory().selectedSlot;
            float prevYaw = mc.player.getYaw();
            float prevPitch = mc.player.getPitch();
            mc.player.getInventory().selectedSlot = carpetSlot;
            interactWithBlockFace(belowPos, Direction.UP);
            mc.player.setYaw(prevYaw);
            mc.player.setPitch(prevPitch);
            mc.player.getInventory().selectedSlot = prevSlot;
            carpetDelay = System.currentTimeMillis();
        }

        BlockPos carpetPos = playerPos.down();
        if (mc.player.isOnGround() && mc.world.getBlockState(carpetPos).getBlock() instanceof CarpetBlock) {
            mc.player.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, carpetPos, Direction.UP));
            mc.player.jump();
            Vec3d vel = mc.player.getVelocity();
            mc.player.setVelocity(vel.x, 0.4, vel.z);
        }
    }

    // ==================== РЕЖИМ Ice Walk (лёд / soul sand) ====================
    private static void handleIceWalk() {
        mc.player.setPitch(90.0f);
        int slot = findIceOrSoulSandSlot();
        if (slot == -1) return;
        placeBlockBelowPlayer(slot);
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================
    private static boolean hasElapsed(long lastTime, long millis) {
        return System.currentTimeMillis() - lastTime >= millis;
    }

    private static int findHotbarSlot(Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    private static int findHotbarCarpetSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                if (((BlockItem) stack.getItem()).getBlock() instanceof CarpetBlock) return i;
            }
        }
        return -1;
    }

    private static int findIceOrSoulSandSlot() {
        for (int i = 0; i < 9; i++) {
            Item item = mc.player.getInventory().getStack(i).getItem();
            if (item == Items.ICE || item == Items.PACKED_ICE || item == Items.BLUE_ICE || item == Items.SOUL_SAND) return i;
        }
        return -1;
    }

    private static int ensureHotbarSlot(Item item) {
        int slot = findHotbarSlot(item);
        if (slot != -1) return slot;
        int invSlot = -1;
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) {
                invSlot = i;
                break;
            }
        }
        if (invSlot == -1) return -1;
        int selected = mc.player.getInventory().selectedSlot;
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, invSlot, selected, SlotActionType.SWAP, mc.player);
        return selected;
    }

    private static void useItemOnCrosshair(int slot, float pitch) {
        int prevSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = slot;
        float prevPitch = mc.player.getPitch();
        mc.player.setPitch(pitch);
        BlockHitResult hit = raycastFromRotation(mc.player.getYaw(), pitch, 4.5);
        if (hit.getType() == HitResult.Type.BLOCK) {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        }
        mc.player.setPitch(prevPitch);
        mc.player.getInventory().selectedSlot = prevSlot;
    }

    private static void useWaterBucketLookingDown(int slot) {
        int prevSlot = mc.player.getInventory().selectedSlot;
        float prevPitch = mc.player.getPitch();
        mc.player.getInventory().selectedSlot = slot;
        mc.player.setPitch(-90.0f);
        mc.player.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, mc.player.getYaw(), mc.player.getPitch()));
        mc.player.setVelocity(mc.player.getVelocity().x, 0.45, mc.player.getVelocity().z);
        mc.player.setPitch(prevPitch);
        mc.player.getInventory().selectedSlot = prevSlot;
    }

    private static void placeLightningRodsAbovePlayer(int slot) {
        int prevSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = slot;
        BlockPos pos = mc.player.getBlockPos();
        for (int i = 1; i <= 2; i++) {
            BlockPos up = pos.up(i);
            if (mc.world.getBlockState(up).isAir()) {
                placeBlockAt(up);
            }
        }
        mc.player.getInventory().selectedSlot = prevSlot;
    }

    private static void placeBlockAt(BlockPos pos) {
        if (!mc.world.getBlockState(pos).isAir()) return;
        Vec3d hitPos = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, pos.down(), false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private static void placeBlockBelowPlayer(int blockSlot) {
        BlockPos replacePos = mc.player.getBlockPos().down();
        BlockPos supportPos = replacePos.down();
        if (!mc.world.getBlockState(replacePos).isReplaceable()) return;
        if (mc.world.getBlockState(supportPos).isAir()) return;

        int prevSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = blockSlot;
        mc.player.setPitch(90.0f);
        Vec3d hitPos = new Vec3d(supportPos.getX() + 0.5, supportPos.getY() + 1.0, supportPos.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, supportPos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.getInventory().selectedSlot = prevSlot;
    }

    private static void interactWithBlockFace(BlockPos pos, Direction face) {
        rotateToward(pos);
        Vec3d hitPos = Vec3d.ofCenter(pos);
        BlockHitResult hit = new BlockHitResult(hitPos, face, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private static void rotateToward(BlockPos pos) {
        Vec3d eyes = mc.player.getEyePos();
        Vec3d center = Vec3d.ofCenter(pos);
        double dx = center.x - eyes.x;
        double dy = center.y - eyes.y;
        double dz = center.z - eyes.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
    }

    private static BlockHitResult raycastFromRotation(float yaw, float pitch, double range) {
        Vec3d eyes = mc.player.getEyePos();
        Vec3d direction = Vec3d.fromPolar(pitch, yaw).normalize();
        Vec3d end = eyes.add(direction.multiply(range));
        RaycastContext ctx = new RaycastContext(eyes, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player);
        return mc.world.raycast(ctx);
    }
}
