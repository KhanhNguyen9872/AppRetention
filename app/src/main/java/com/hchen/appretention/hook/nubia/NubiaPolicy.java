package com.hchen.appretention.hook.nubia;

import static com.hchen.hooktool.core.CoreTool.findClassIfExists;
import static com.hchen.hooktool.core.CoreTool.hook;

import com.hchen.appretention.hook.system.opt.BackgroundRestrictOpt;
import com.hchen.appretention.log.XposedLog;
import com.hchen.collect.HookEntrance;
import com.hchen.hooktool.HCBase;
import com.hchen.hooktool.hook.IHook;
import com.hchen.hooktool.utils.DeviceTool;
import com.hchen.hooktool.utils.SystemPropTool;

import java.lang.reflect.Method;

/**
 * Suppresses aggressive RedMagic (Nubia MyOS) background killers, NeoPower,
 * SmartEngine, and GameSpace background memory purgers on RedMagic 9 / NX769S and Nubia devices.
 *
 * Restricted apps are deliberately exempt from protection so they can be cleaned.
 *
 * @author Antigravity
 */
@HookEntrance(targetPackage = "android", targetBrand = "nubia")
public class NubiaPolicy extends HCBase {
    private static final String TAG = "NubiaPolicy";

    @Override
    public boolean isEnabled() {
        return SystemPropTool.getProp("persist.hchen.nubia.opt.enable", true);
    }

    @Override
    protected void init() {
        if (!isEnabled()) {
            XposedLog.logD(TAG, "NubiaPolicy is disabled.");
            return;
        }

        hookNubiaProcessManager();
        hookNubiaSmartEngine();
        hookNubiaFreezer();

        XposedLog.logI(TAG, "NubiaPolicy initialized on " + DeviceTool.getDeviceFingerprint());
    }

    public static void manualInit() {
        new NubiaPolicy().init();
    }

    private static boolean isRestrictedTarget(Object[] args) {
        if (args == null) return false;
        for (Object a : args) {
            if (a instanceof String && BackgroundRestrictOpt.isRestricted((String) a)) {
                return true;
            }
        }
        return false;
    }

    private static void handleSafeReturn(Method method, IHook hook) {
        Class<?> retType = method.getReturnType();
        if (retType == void.class) {
            hook.returnNull();
        } else if (retType == boolean.class || retType == Boolean.class) {
            hook.setResult(false);
        } else if (retType == int.class || retType == Integer.class) {
            hook.setResult(0);
        } else {
            hook.returnNull();
        }
    }

    private void hookNubiaProcessManager() {
        String[] candidateClasses = new String[]{
            "cn.nubia.server.appmag.ProcessManager",
            "com.android.server.am.NubiaProcessManager",
            "com.android.server.am.NubiaProcessManagerService"
        };

        for (String className : candidateClasses) {
            Class<?> clazz = findClassIfExists(className);
            if (clazz == null) continue;

            for (Method method : clazz.getDeclaredMethods()) {
                String name = method.getName().toLowerCase();
                if (name.contains("clean") || name.contains("kill") || name.contains("autoclean") || name.contains("purge")) {
                    hook(method, new IHook() {
                        @Override
                        public void before() {
                            if (isRestrictedTarget(getArgs())) {
                                return; // Allow Nubia to kill/clean restricted apps
                            }
                            XposedLog.logI(TAG, "Intercepted Nubia clean/kill method: " + method.getName());
                            handleSafeReturn(method, this);
                        }
                    });
                }
            }
        }
    }

    private void hookNubiaSmartEngine() {
        String[] candidateClasses = new String[]{
            "cn.nubia.server.policy.SmartEngine",
            "cn.nubia.server.policy.smartengine.SmartEngineService",
            "cn.nubia.server.policy.smartengine.SmartEnginePolicy"
        };

        for (String className : candidateClasses) {
            Class<?> clazz = findClassIfExists(className);
            if (clazz == null) continue;

            for (Method method : clazz.getDeclaredMethods()) {
                String name = method.getName().toLowerCase();
                if (name.contains("kill") || name.contains("clean") || name.contains("terminate")) {
                    hook(method, new IHook() {
                        @Override
                        public void before() {
                            if (isRestrictedTarget(getArgs())) {
                                return; // Allow Nubia to terminate restricted apps
                            }
                            XposedLog.logI(TAG, "Intercepted Nubia SmartEngine method: " + method.getName());
                            handleSafeReturn(method, this);
                        }
                    });
                }
            }
        }
    }

    private void hookNubiaFreezer() {
        String[] candidateClasses = new String[]{
            "cn.nubia.server.appmag.AppFreezeManager",
            "com.android.server.am.NubiaFreezerManager"
        };

        for (String className : candidateClasses) {
            Class<?> clazz = findClassIfExists(className);
            if (clazz == null) continue;

            for (Method method : clazz.getDeclaredMethods()) {
                String name = method.getName().toLowerCase();
                if (name.contains("killfrozen") || name.contains("timeoutkill")) {
                    hook(method, new IHook() {
                        @Override
                        public void before() {
                            if (isRestrictedTarget(getArgs())) {
                                return; // Allow Nubia to kill frozen restricted apps
                            }
                            XposedLog.logI(TAG, "Intercepted Nubia Freezer kill method: " + method.getName());
                            handleSafeReturn(method, this);
                        }
                    });
                }
            }
        }
    }
}
