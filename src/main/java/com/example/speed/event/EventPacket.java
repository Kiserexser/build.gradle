package com.example.speed.event;

import net.minecraft.network.packet.Packet;

public class EventPacket extends Event {
    private final Packet<?> packet;
    private final Side side;
    public enum Side { SEND, RECEIVE }
    public EventPacket(Packet<?> packet, Side side) {
        this.packet = packet;
        this.side = side;
    }
    public Packet<?> getPacket() { return packet; }
    public Side getSide() { return side; }
}
