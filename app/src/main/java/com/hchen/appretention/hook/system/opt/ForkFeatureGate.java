package com.hchen.appretention.hook.system.opt;

import com.hchen.hooktool.utils.SystemPropTool;

/**
 * Fail-safe master gate for behavior added by AppRetentionFork.
 *
 * <p>The upstream retention hooks remain available while fork-only system-server
 * extensions stay disabled until the user explicitly enables them and reboots.</p>
 */
public final class ForkFeatureGate {
    public static final String PROP_ENABLE = "persist.hchen.fork.extensions.enable";

    private ForkFeatureGate() {}

    public static boolean isEnabled() {
        return SystemPropTool.getProp(PROP_ENABLE, false);
    }
}
