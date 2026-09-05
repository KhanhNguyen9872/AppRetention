package com.hchen.hooktool;

import androidx.annotation.NonNull;
import com.hchen.hooktool.core.CoreTool;

/**
 * Compatibility base class for AppRetention hooks.
 * Inherits all reflection & hooking utilities from CoreTool.
 *
 * @author HChenX
 */
public abstract class HCBase extends CoreTool {
    @NonNull
    public String TAG = getClass().getSimpleName();

    /**
     * Initialize hook rules.
     */
    protected abstract void init();

    /**
     * Called when the target package is loaded.
     */
    public void onLoadPackage() {
        if (isEnabled()) {
            init();
        }
    }

    /**
     * Whether this hook module is enabled.
     */
    public boolean isEnabled() {
        return true;
    }
}
