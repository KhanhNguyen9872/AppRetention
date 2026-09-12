package com.hchen.appretention.hook.system.opt;

import static com.hchen.hooktool.core.CoreTool.findClassIfExists;
import static com.hchen.hooktool.core.CoreTool.hook;

import com.hchen.appretention.log.XposedLog;
import com.hchen.hooktool.hook.IHook;
import com.hchen.hooktool.utils.SystemPropTool;

import java.lang.reflect.Method;

/**
 * Bypasses OEM auto-start restrictions on boot and background wakeups across:
 * - Nubia / RedMagic (NeoPower, AutoRunController, CmpManager)
 * - Xiaomi / MIUI / HyperOS (SecurityManagerService, SmartPowerService, ProcessManagerService)
 * - ColorOS / Realme / OnePlus (ColorAppStartupManager, OplusAppStartupManager)
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
            // Nubia / RedMagic
            "cn.nubia.server.appmag.AutoRunController",
            "cn.nubia.server.appmag.ProcessManager",
            "com.android.server.am.NubiaProcessManager",
            "cn.nubia.server.appmag.CmpManager",
            "cn.nubia.server.appmag.NeoPowerController",

            // Xiaomi / MIUI / HyperOS
            "com.miui.server.SecurityManagerService",
            "com.miui.server.smartpower.SmartPowerService",
            "com.android.server.am.ProcessManagerService",

            // ColorOS / Realme / OnePlus (Oplus)
            "com.android.server.am.ColorAppStartupManager",
            "com.android.server.am.OplusAppStartupManager"
        };

        int hookedCount = 0;
        for (String className : targetClasses) {
            Class<?> clazz = findClassIfExists(className);
            if (clazz == null) continue;

            for (Method m : clazz.getDeclaredMethods()) {
                String name = m.getName().toLowerCase();
                Class<?> retType = m.getReturnType();
                boolean supportedReturn = retType == boolean.class || retType == Boolean.class
                    || retType == int.class || retType == Integer.class;
                boolean hasPackageArgument = false;
                for (Class<?> type : m.getParameterTypes()) {
                    if (type == String.class) {
                        hasPackageArgument = true;
                        break;
                    }
                }
                if (!supportedReturn || !hasPackageArgument) continue;

                // Methods that allow / grant auto-start
                if (name.contains("autorun") || name.contains("autostart") || name.contains("bootallow")
                        || name.contains("allowstart") || name.contains("isallow") || name.contains("canstart")) {
                    hook(m, new IHook() {
                        @Override
                        public void before() {
                            if (!isEnabled()) return;
                            // Check if target is a restricted app
                            Object[] args = getArgs();
                            if (args != null && args.length > 0) {
                                for (Object a : args) {
                                    if (a instanceof String && isExcludedTarget((String) a)) {
                                        return; // Do not bypass startup policy for restricted apps or this UI.
                                    }
                                }
                            }

                            if (retType == boolean.class || retType == Boolean.class) {
                                setResult(true);
                            } else if (retType == int.class || retType == Integer.class) {
                                setResult(1);
                            }
                        }
                    });
                    hookedCount++;
                }
                // Methods that intercept / prevent startup (e.g. shouldPreventRestart, shouldPreventStart)
                else if (name.contains("shouldprevent") || name.contains("preventstart") || name.contains("interceptstart")) {
                    hook(m, new IHook() {
                        @Override
                        public void before() {
                            if (!isEnabled()) return;
                            Object[] args = getArgs();
                            if (args != null && args.length > 0) {
                                for (Object a : args) {
                                    if (a instanceof String && isExcludedTarget((String) a)) {
                                        return; // Allow OEM prevention for restricted apps or this UI.
                                    }
                                }
                            }

                            if (retType == boolean.class || retType == Boolean.class) {
                                setResult(false);
                            } else if (retType == int.class || retType == Integer.class) {
                                setResult(0);
                            }
                        }
                    });
                    hookedCount++;
                }
            }
        }

        XposedLog.logI(TAG, "AutoStartOpt initialized successfully! Hooked " + hookedCount + " OEM methods.");
    }

    private static boolean isExcludedTarget(String packageName) {
        return BackgroundRestrictOpt.isRestricted(packageName)
            || BackgroundRestrictOpt.PACKAGE_APPRETENTION.equals(packageName);
    }

    private static boolean isEnabled() {
        return ForkFeatureGate.isEnabled()
            && SystemPropTool.getProp("persist.hchen.autostart.opt.enable", true);
    }
}
