package com.mrgregles.bsp_mobile.construct;

/** What a core's construct is doing: nothing yet, standing still, or under way. */
public enum ConstructState {
    NOT_ASSEMBLED, PARKED, MOVING;

    public static ConstructState of(boolean assembled, double speed, double movingSpeed) {
        return !assembled ? NOT_ASSEMBLED : speed > movingSpeed ? MOVING : PARKED;
    }
}
