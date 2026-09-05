package com.hchen.hooktool.utils;

import com.hchen.hooktool.core.CoreTool;

/**
 * Compatibility reflection invocation tool.
 *
 * @author HChenX
 */
public final class InvokeTool {
    private InvokeTool() {}

    public static Class<?> findClass(String className) {
        return CoreTool.findClass(className);
    }

    @SuppressWarnings("unchecked")
    public static <T> T callStaticMethod(Class<?> clazz, String methodName, Class<?>[] parameterTypes, Object... args) {
        return (T) CoreTool.callStaticMethod(clazz, methodName, args);
    }
}
