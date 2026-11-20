package com.elicitsoftware.diagnostics;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

/**
 * Outcome of one diagnostic probe, as rendered by the System screens.
 *
 * @param name       what was checked
 * @param status     whether it passed
 * @param detail     human-readable detail: a version, an address, or the failure reason
 * @param durationMs how long the probe took, or 0 when timing is meaningless
 */
public record CheckResult(String name, Status status, String detail, long durationMs) {

    /** Probe outcome. */
    public enum Status {
        /** The target answered as expected. */
        UP,
        /** The target failed, refused, or timed out. */
        DOWN,
        /** The target could not be checked, usually because nothing is configured. */
        UNKNOWN
    }

    /**
     * A result for a target that answered as expected.
     *
     * @param name       what was checked
     * @param detail     what the probe found
     * @param durationMs how long the probe took
     * @return an {@link Status#UP} result
     */
    public static CheckResult up(String name, String detail, long durationMs) {
        return new CheckResult(name, Status.UP, detail, durationMs);
    }

    /**
     * A result for a target that failed, refused, or timed out.
     *
     * @param name       what was checked
     * @param detail     why the probe failed
     * @param durationMs how long the probe took
     * @return a {@link Status#DOWN} result
     */
    public static CheckResult down(String name, String detail, long durationMs) {
        return new CheckResult(name, Status.DOWN, detail, durationMs);
    }

    /**
     * A result for a target that could not be checked; its duration is 0.
     *
     * @param name   what was checked
     * @param detail why it could not be checked
     * @return an {@link Status#UNKNOWN} result
     */
    public static CheckResult unknown(String name, String detail) {
        return new CheckResult(name, Status.UNKNOWN, detail, 0);
    }

    /**
     * Whether the target answered as expected.
     *
     * @return true when the status is {@link Status#UP}
     */
    public boolean isUp() {
        return status == Status.UP;
    }
}
