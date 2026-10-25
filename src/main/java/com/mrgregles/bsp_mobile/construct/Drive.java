package com.mrgregles.bsp_mobile.construct;

/**
 * The driving maths (D37, D38), client-free and engine-free so the GameTests can check it: from the driver's input
 * and the construct's motion, the force and the torque to apply this physics tick. Everything is in the construct's
 * own frame: {@code forward} is the helm's facing as a unit vector in ship space, velocities are the construct's
 * velocity turned into ship space. Forces scale with mass so every construct accelerates alike.
 */
public final class Drive {
    private Drive() {}

    /**
     * What the helm asks for this tick. {@code forward} and {@code left} are the driver's impulses (-1 to 1), the
     * facing is the helm's in ship space (a unit vector on the horizontal plane), the figures are the config's with
     * the totem cap (D34) already applied to {@code maxSpeed}.
     */
    public record Command(double forward, double left, boolean sprint, double up, double facingX, double facingZ,
                          double accel, double maxSpeed, double turnRate, double drag, double buoyancy,
                          double liftCapacity, boolean hover, double climb, double gravity, double y, double ceilingY) {
        /** A command with no lift aboard. */
        public static Command ground(double forward, double left, boolean sprint, double facingX, double facingZ,
                                     double accel, double maxSpeed, double turnRate, double drag, double buoyancy) {
            return new Command(forward, left, sprint, 0, facingX, facingZ, accel, maxSpeed, turnRate, drag, buoyancy, 0, false, 0, 10, 0, Double.MAX_VALUE);
        }

        public static Command idle(double facingX, double facingZ, double drag, double buoyancy) {
            return ground(0, 0, false, facingX, facingZ, 0, 0, 0, drag, buoyancy);
        }

        public boolean driving() {
            return forward != 0 || left != 0 || up != 0;
        }
    }

    /** The result: a force in ship space on the plane ({@code fx}, {@code fz}), an upward force in the world ({@code fy}) and a torque about the ship's up axis. */
    public record Forces(double fx, double fy, double fz, double torqueY) {
        public static final Forces NONE = new Forces(0, 0, 0, 0);
    }

    /**
     * @param mass     the construct's mass
     * @param inertiaY its moment of inertia about its up axis
     * @param vx       velocity in ship space, x
     * @param vy       vertical velocity in the world (up positive)
     * @param vz       velocity in ship space, z
     * @param omegaY   angular velocity about the ship's up axis (radians a second)
     */
    public static Forces forces(double mass, double inertiaY, double vx, double vy, double vz, double omegaY, Command c) {
        if (mass <= 0) {
            return Forces.NONE;
        }
        double fxDir = c.facingX(), fzDir = c.facingZ();
        double along = vx * fxDir + vz * fzDir; // speed along the facing, forward positive
        double fx = 0, fz = 0;
        // thrust (W and S): never past the top speed forward, or half of it backward
        double thrust = c.forward() * c.accel() * mass * (c.sprint() ? 2 : 1);
        if (c.forward() > 0 && along >= c.maxSpeed()) {
            thrust = 0;
        } else if (c.forward() < 0 && along <= -c.maxSpeed() / 2) {
            thrust = 0;
        }
        fx += fxDir * thrust;
        fz += fzDir * thrust;
        // drag on the horizontal plane: a fraction of the velocity each second, so the construct coasts to a halt
        fx -= c.drag() * mass * vx;
        fz -= c.drag() * mass * vz;
        // the turn (A and D): a torque that seeks the turn rate, plus damping so the yaw stops with the key
        double wanted = c.left() * Math.toRadians(c.turnRate());
        double torque = inertiaY * (wanted - omegaY) * 2.0;
        return new Forces(fx, lift(mass, vy, c), fz, torque);
    }

    /**
     * The upward force from the lift aboard (D53, D55): none without lift; with less lift than the mass, a part of
     * the weight (the fall slows); with enough, the weight is cancelled and the construct climbs on space, descends
     * on sprint and space, holds its height with balloons and no input, or sinks slowly with jets alone; never
     * more than its weight at or above the ceiling.
     */
    public static double lift(double mass, double vy, Command c) {
        if (c.liftCapacity() <= 0 || c.gravity() <= 0) {
            return 0;
        }
        double g = c.gravity();
        double weight = mass * g;
        double carry = Math.min(1.0, c.liftCapacity() / mass);
        if (carry < 1.0) {
            return weight * carry;
        }
        boolean climbing = c.up() > 0 && !c.sprint(), descending = c.up() > 0 && c.sprint();
        double fy;
        if (climbing) {
            fy = mass * (g + c.climb());
        } else if (descending) {
            fy = mass * (g - c.climb());
        } else if (c.hover()) {
            fy = weight - mass * vy * 2.0;
        } else {
            fy = weight * 0.9;
        }
        if (c.y() >= c.ceilingY() && fy > weight) {
            fy = weight;
        }
        return fy;
    }
}
