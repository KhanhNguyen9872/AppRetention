package com.hchen.appretention.hook.system.opt;

import static com.hchen.hooktool.core.CoreTool.findClassIfExists;
import static com.hchen.hooktool.core.CoreTool.hook;

import com.hchen.appretention.log.XposedLog;
import com.hchen.hooktool.hook.IHook;
import com.hchen.hooktool.utils.SystemPropTool;

import java.lang.reflect.Method;

/**
 * Bypasses OEM auto-start restrictions on boot (Nubia, Xiaomi, ColorOS)
 * to ensure background apps receive BOOT_COMPLETED intents.
 *
 * @author Antigravity
 */
public final class AutoStartOpt {
    private static final String TAG = "AutoStartOpt";

    public static void init() {
        if (!isEnabled()) {
            XposedLog.logD(TAG, "AutoStartOpt disabled by property.");
            return;
        }

        String[] targetClasses = new String[]{
            "cn.nubia.server.appmag.AutoRunController",
            "cn.nubia.server.appmag.ProcessManager",
            "com.android.server.am.NubiaProcessManager"
        };

        for (String className : targetClasses) {
            Class<?> clazz = findClassIfExists(className);
            if (clazz == null) continue;

            for (Method m : clazz.getDeclaredMethods()) {
                String name = m.getName().toLowerCase();
                if (name.contains("autorun") || name.contains("autostart") || name.contains("bootallow")) {
                    Class<?> retType = m.getReturnType();
                    hook(m, new IHook() {
                        @Override
                        public void before() {
                            if (retType == boolean.class || retType == Boolean.class) {
                                setResult(true);
                            } else if (retType == int.class || retType == Integer.class) {
                                setResult(1);
                            }
                            XposedLog.logD(TAG, "Granted auto-start privilege via " + m.getName());
                        }
                    });
                }
            }
        }

        XposedLog.logI(TAG, "AutoStartOpt initialized successfully!");
    }

    private static boolean isEnabled() {
        return SystemPropTool.getProp("persist.hchen.autostart.opt.enable", true);
    }
}
