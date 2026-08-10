package com.garvverma.waypointslite;

public class Waypoint {
    public String name;
    public String world;
    public double x, y, z;
    public float yaw, pitch;

    public Waypoint() {}

    public Waypoint(String name, String world, double x, double y, double z, float yaw, float pitch) {
        this.name = name;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }
}
