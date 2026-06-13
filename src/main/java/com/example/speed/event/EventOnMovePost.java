package com.example.speed.event;

import net.minecraft.util.math.Vec3d;

public class EventOnMovePost extends Event {
    private final float speed;
    private final Vec3d movementInput;
    public EventOnMovePost(float speed, Vec3d movementInput) {
        this.speed = speed;
        this.movementInput = movementInput;
    }
    public float getSpeed() { return speed; }
    public Vec3d getMovementInput() { return movementInput; }
}
