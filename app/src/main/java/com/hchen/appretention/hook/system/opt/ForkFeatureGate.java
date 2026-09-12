package com.hchen.appretention.hook.system.opt;

/**
 * Compatibility gate retained for existing hook call sites.
 * Fork extensions are an intrinsic part of this fork and are always available;
 * each feature remains independently controllable by its own property.
 */
public final class ForkFeatureGate {
    private ForkFeatureGate() {}

    public static boolean isEnabled() {
        return true;
    }
}
