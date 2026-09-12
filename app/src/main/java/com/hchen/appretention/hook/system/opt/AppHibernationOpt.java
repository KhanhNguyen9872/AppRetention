package com.hchen.appretention.hook.system.opt;

import static com.hchen.hooktool.core.CoreTool.findClassIfExists;
import static com.hchen.hooktool.core.CoreTool.hook;

import com.hchen.appretention.log.XposedLog;
import com.hchen.hooktool.hook.IHook;
import com.hchen.hooktool.utils.SystemPropTool;

import java.lang.reflect.Method;

/**
 * Disables Android App Hibernation (Android 12-16) to prevent the system
 * from automatically freezing unused apps or revoking permissions.
 *
 * @author Antigravity
 */
public final class AppHibernationOpt {
    private static final String TAG = "AppHibernationOpt";

    public static void init() {
        if (!isEnabled()) {
            XposedLog.logD(TAG, "AppHibernationOpt disabled by property.");
            return;
        }

        Class<?> serviceClass = findClassIfExists("com.android.server.apphibernation.AppHibernationService");
        if (serviceClass == null) {
            XposedLog.logD(TAG, "AppHibernationService not found, skipping.");
            return;
        }

        for (Method method : serviceClass.getDeclaredMethods()) {
            String name = method.getName();
            Class<?> retType = method.getReturnType();
            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean packageFirst = parameterTypes.length > 0 && parameterTypes[0] == String.class;
            boolean booleanLast = parameterTypes.length > 0
                && (parameterTypes[parameterTypes.length - 1] == boolean.class
                || parameterTypes[parameterTypes.length - 1] == Boolean.class);

            if (packageFirst && booleanLast && (retType == void.class || retType == boolean.class
                || retType == Boolean.class) && ("setHibernatingGlobally".equals(name)
                || "setHibernatingForUser".equals(name))) {
                hook(method, new IHook() {
                    @Override
                    public void before() {
                        if (!isEnabled()) return;
                        Object[] args = getArgs();
                        if (args != null && args.length > 0) {
                            Object pkgArg = getArg(0);
                            String pkg = pkgArg != null ? pkgArg.toString() : null;
                            if (pkg != null && BackgroundRestrictOpt.isRestricted(pkg)) {
                                return; // Bỏ qua app bị giới hạn nền, cho phép ngủ đông
                            }
                            Object val = args[args.length - 1];
                            if (Boolean.TRUE.equals(val)) {
                                XposedLog.logI(TAG, "Prevented app hibernation for: " + getArg(0));
                                if (retType == void.class) {
                                    returnNull();
                                } else if (retType == boolean.class || retType == Boolean.class) {
                                    setResult(false);
                                }
                            }
                        }
                    }
                });
            } else if (packageFirst && (retType == boolean.class || retType == Boolean.class)
                && ("isHibernatingGlobally".equals(name) || "isHibernatingForUser".equals(name))) {
                hook(method, new IHook() {
                    @Override
                    public void before() {
                        if (!isEnabled()) return;
                        setResult(false);
                    }
                });
            }
        }

        XposedLog.logI(TAG, "AppHibernationOpt initialized successfully!");
    }

    private static boolean isEnabled() {
        return ForkFeatureGate.isEnabled()
            && SystemPropTool.getProp("persist.hchen.hibernation.opt.enable", false);
    }
}
