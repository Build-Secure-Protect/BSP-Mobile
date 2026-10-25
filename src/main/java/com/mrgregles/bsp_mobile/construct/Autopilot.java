package com.mrgregles.bsp_mobile.construct;

import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge.Inputs;
import net.minecraft.world.phys.Vec3;

/**
 * The autopilot's steering (D65), client-free: from where the construct is and faces and where it should go, the
 * inputs a driver would give: turn toward the waypoint, thrust once within a cone of it (half thrust when close),
 * climb or descend toward the cruise height with the lift aboard. Headings are compass degrees (0 north, 90 east).
 */
public final class Autopilot {
    public static final double REACH = 4.0;
    private static final double THRUST_CONE = 30.0, SLOW_WITHIN = 12.0, HEIGHT_BAND = 1.5;

    private Autopilot() {}

    /** The compass bearing from {@code from} to {@code to} on the horizontal plane. */
    public static double bearing(Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dz = to.z - from.z;
        double deg = Math.toDegrees(Math.atan2(dx, -dz)); // north is -z
        return deg < 0 ? deg + 360 : deg;
    }

    /** The horizontal distance between two points. */
    public static double distance(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static boolean reached(Vec3 pos, Vec3 target) {
        return distance(pos, target) <= REACH;
    }

    /**
     * @param pos        where the construct is (world)
     * @param heading    the compass heading the helm faces now
     * @param target     the waypoint's centre (world)
     * @param cruiseY    the height to fly at (absolute), or a negative number for "keep the height you have"
     * @param hasLift    whether anything aboard can climb
     */
    public static Inputs steer(Vec3 pos, double heading, Vec3 target, double cruiseY, boolean hasLift) {
        double diff = wrap(bearing(pos, target) - heading); // positive: the target is to the right
        float left = (float) Math.max(-1, Math.min(1, -diff / 45.0));
        float forward = 0;
        if (Math.abs(diff) <= THRUST_CONE) {
            forward = distance(pos, target) <= SLOW_WITHIN ? 0.5f : 1f;
        }
        float up = 0;
        boolean sprint = false;
        if (hasLift && cruiseY >= 0) {
            if (pos.y < cruiseY - HEIGHT_BAND) {
                up = 1;
            } else if (pos.y > cruiseY + HEIGHT_BAND) {
                up = 1;
                sprint = true;
            }
        }
        return new Inputs(forward, left, up, sprint);
    }

    /** An angle into -180 to 180. */
    public static double wrap(double deg) {
        double d = deg % 360;
        if (d > 180) {
            d -= 360;
        } else if (d < -180) {
            d += 360;
        }
        return d;
    }
}
