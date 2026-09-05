package com.hchen.hooktool.utils;

import android.content.Context;
import com.hchen.hooktool.core.CoreTool;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Compatibility context provider tool.
 *
 * @author HChenX
 */
public final class ContextTool {
    public static final int FLAG_ALL = 0;

    private ContextTool() {}

    public static void getAsyncContext(Consumer<Context> callback, int flags) {
        Executors.newSingleThreadExecutor().execute(() -> {
            int retries = 50;
            while (retries-- > 0) {
                try {
                    Class<?> activityThreadClass = CoreTool.findClassIfExists("android.app.ActivityThread");
                    if (activityThreadClass != null) {
                        Context context = (Context) CoreTool.callStaticMethod(activityThreadClass, "currentApplication");
                        if (context != null) {
                            callback.accept(context);
                            return;
                        }
                    }
                    Thread.sleep(200);
                } catch (Throwable ignored) {
                }
            }
        });
    }
}
