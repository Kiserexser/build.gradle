package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Random;

public class SpeedMod implements ModInitializer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean killaura = false;
    private static boolean lastR = false;
    private static Entity target = null;
    private static long lastAttackTime = 0;
    private static final Random random = new Random();
    private static final TSAngle rotator = new TSAngle();

    // ========== НАСТРОЙКИ ==========
    private static final float RANGE = 4.2f;
    private static final long MIN_DELAY = 690L;   // изменено с 750 на 690
    private static final long MAX_DELAY = 760L;   // изменено с 850 на 760

    @Override
    public void onInitialize() {
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                if (mc.player == null) continue;
                long window = mc.getWindow().getHandle();
                boolean currentR = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                if (currentR && !lastR) {
                    killaura = !killaura;
                    mc.player.sendMessage(Text.literal(killaura ? "§aKillaura (TSAngle) ON" : "§cKillaura OFF"), true);
                    if (!killaura) target = null;
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
                lastR = currentR;
                if (killaura) tick();
            }
        }).start();
    }

    private void tick() {
        updateTarget();
        if (target == null) return;

        // Проверка дистанции и видимости
        if (mc.player.squaredDistanceTo(target) > RANGE * RANGE) return;
        if (!mc.player.canSee(target)) return;

        // Вычисляем идеальные углы на цель
        Vec3d eye = mc.player.getEyePos();
        Vec3d to = target.getBoundingBox().getCenter().subtract(eye);
        double hyp = Math.hypot(to.x, to.z);
        float idealYaw = (float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90);
        float idealPitch = (float) -Math.toDegrees(Math.atan2(to.y, hyp));
        idealYaw = wrap(idealYaw);
        idealPitch = clamp(idealPitch, -89, 89);

        // Текущие углы игрока
        Turns current = new Turns(mc.player.getYaw(), mc.player.getPitch());
        Turns targetAngles = new Turns(idealYaw, idealPitch);

        // Применяем ротацию TSAngle
        Turns newAngles = rotator.limitAngleChange(current, targetAngles, to, target);
        mc.player.setYaw(newAngles.getYaw());
        mc.player.setPitch(newAngles.getPitch());
        mc.player.headYaw = newAngles.getYaw();
        mc.player.bodyYaw = newAngles.getYaw();

        // Атака с задержкой
        long now = System.currentTimeMillis();
        long delay = MIN_DELAY + (long)(random.nextDouble() * (MAX_DELAY - MIN_DELAY));
        // Проверка, достаточно ли близко к цели (угол)
        float deltaYaw = wrap(idealYaw - mc.player.getYaw());
        float deltaPitch = idealPitch - mc.player.getPitch();
        boolean canAttack = Math.abs(deltaYaw) < 15f && Math.abs(deltaPitch) < 15f;
        if (now - lastAttackTime >= delay && canAttack) {
            boolean wasSprinting = mc.player.isSprinting();
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            if (wasSprinting) mc.player.setSprinting(true);
            mc.player.setSprinting(true);
            lastAttackTime = now;
        }
    }

    private void updateTarget() {
        if (target != null && target.isAlive() && mc.player.squaredDistanceTo(target) <= RANGE * RANGE) {
            return;
        }
        Entity best = null;
        double closest = RANGE * RANGE;
        Box box = mc.player.getBoundingBox().expand(RANGE);
        List<Entity> entities = mc.world.getOtherEntities(mc.player, box,
                e -> e instanceof LivingEntity && e != mc.player && ((LivingEntity) e).isAlive());
        for (Entity e : entities) {
            if (e instanceof PlayerEntity && mc.player.isTeammate((PlayerEntity) e)) continue;
            double dist = mc.player.squaredDistanceTo(e);
            if (dist < closest && mc.player.canSee(e)) {
                closest = dist;
                best = e;
            }
        }
        target = best;
    }

    private static float wrap(float v) { v %= 360f; if (v >= 180f) v -= 360f; if (v < -180f) v += 360f; return v; }
    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }

    // ========== ВСПОМОГАТЕЛЬНЫЕ КЛАССЫ ДЛЯ РОТАЦИИ ==========
    static class Turns {
        private float yaw, pitch;
        public Turns(float yaw, float pitch) { this.yaw = yaw; this.pitch = pitch; }
        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
        public void setYaw(float y) { yaw = y; }
        public void setPitch(float p) { pitch = p; }
        public Turns adjustSensitivity() { return this; }
    }

    static class MathAngle {
        public static Turns calculateDelta(Turns a, Turns b) {
            float dy = wrap(b.getYaw() - a.getYaw());
            float dp = b.getPitch() - a.getPitch();
            return new Turns(dy, dp);
        }
    }

    static class Calculate {
        private static final Random RAND = new Random();
        public static float getRandom(float min, float max) {
            return min + RAND.nextFloat() * (max - min);
        }
    }

    static class TSAngle {
        private static final float EPSILON = 1.0E-3F;

        public Turns limitAngleChange(Turns currentAngle, Turns targetAngle, Vec3d vec3d, Entity entity) {
            Turns delta = MathAngle.calculateDelta(currentAngle, targetAngle);
            float yawDelta = delta.getYaw();
            float pitchDelta = delta.getPitch();
            float length = (float) Math.hypot(yawDelta, pitchDelta);

            float yawSpeed = 25.0f + Calculate.getRandom(0.0f, 5.0f);
            float pitchSpeed = yawSpeed * (0.5f + (float) Math.random() * 0.5f);

            Turns moveAngle = new Turns(currentAngle.getYaw(), currentAngle.getPitch());

            if (length > EPSILON) {
                float yawStep = Math.min(Math.abs(yawDelta), yawSpeed);
                float pitchStep = Math.min(Math.abs(pitchDelta), pitchSpeed);

                float newYaw = currentAngle.getYaw() + Math.signum(yawDelta) * yawStep;
                float newPitch = MathHelper.clamp(currentAngle.getPitch() + Math.signum(pitchDelta) * pitchStep, -89.0F, 90.0F);

                moveAngle.setYaw(newYaw);
                moveAngle.setPitch(newPitch);
            }
            return moveAngle.adjustSensitivity();
        }

        public Vec3d randomValue() {
            return new Vec3d(0.1, 0.1, 0.1);
        }
    }
}
