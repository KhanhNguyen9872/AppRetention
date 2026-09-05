package com.hchen.hooktool.hook;

import androidx.annotation.Nullable;
import com.hchen.hooktool.core.CoreTool;
import java.lang.reflect.Member;
import io.github.libxposed.api.XposedInterface;

/**
 * Compatibility hook class extending modern AbsHook.
 *
 * @author HChenX
 */
public class IHook extends AbsHook {
    public IHook() {
        super();
    }

    public IHook(int priority) {
        super(priority);
    }

    public IHook(@Nullable String id) {
        super(id);
    }

    public IHook(int priority, @Nullable String id) {
        super(priority, id);
    }

    public IHook(@Nullable XposedInterface.ExceptionMode mode) {
        super(mode);
    }

    public IHook(int priority, @Nullable XposedInterface.ExceptionMode mode) {
        super(priority, mode);
    }

    public IHook(@Nullable String id, @Nullable XposedInterface.ExceptionMode mode) {
        super(id, mode);
    }

    public IHook(int priority, @Nullable String id, @Nullable XposedInterface.ExceptionMode mode) {
        super(priority, id, mode);
    }

    public Member getMember() {
        return getExecutable();
    }

    public Object thisObject() {
        return getThisObject();
    }

    public Object getThisField(String fieldName) {
        return CoreTool.getField(getThisObject(), fieldName);
    }

    public void setThisField(String fieldName, Object value) {
        CoreTool.setField(getThisObject(), fieldName, value);
    }

    public void unHookSelf() {
        unhookSelf();
    }

    public void returnNull() {
        setResult(null);
    }
}
