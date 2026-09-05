package com.hchen.hooktool;

/**
 * Compatibility data holder for log level, target package, and classloader.
 *
 * @author HChenX
 */
public final class HCData {
    private static volatile int logLevel = HCInit.LOG_D;
    private static volatile String targetPackageName = "";
    private static volatile String tag = "AppRetention";

    private HCData() {}

    public static int getLogLevel() {
        return logLevel;
    }

    public static void setLogLevel(int level) {
        logLevel = level;
    }

    public static String getTargetPackageName() {
        return targetPackageName;
    }

    public static void setTargetPackageName(String pkg) {
        targetPackageName = pkg;
    }

    public static String getTag() {
        return tag;
    }

    public static void setTag(String t) {
        tag = t;
    }

    public static ClassLoader getClassLoader() {
        return ModuleData.getClassLoader();
    }

    public static void setClassLoader(ClassLoader cl) {
        ModuleData.setClassLoader(cl);
    }
}
