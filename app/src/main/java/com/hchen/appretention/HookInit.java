package com.hchen.appretention;

import static com.hchen.appretention.log.XposedLog.logENoSave;

import androidx.annotation.NonNull;

import com.hchen.appretention.hook.EntranceMap;
import com.hchen.appretention.log.SaveLog;
import com.hchen.hooktool.HCBase;
import com.hchen.hooktool.HCData;
import com.hchen.hooktool.ModuleConfig;
import com.hchen.hooktool.ModuleData;
import com.hchen.hooktool.ModuleEntrance;
import com.hchen.hooktool.utils.DeviceTool;
import com.hchen.hooktool.utils.SystemPropTool;

import java.util.Arrays;
import java.util.Objects;

/**
 * Modern LibXposed API 101 Hook Entry Point.
 *
 * @author HChenX
 */
public class HookInit extends ModuleEntrance {
    private static final String TAG = "AppRetention";

    @Override
    public void initModuleConfig() {
        ModuleConfig.setLogTag(TAG);
        ModuleConfig.setLogLevel(ModuleConfig.LOG_D);
        ModuleConfig.setPrefsName("AppRetention");
    }

    @Override
    public void handleSystemServerStarting(@NonNull SystemServerStartingParam param) {
        ModuleData.setClassLoader(param.getClassLoader());
        HCData.setTargetPackageName("android");
        dispatchHooks("android", param.getClassLoader());
    }

    @Override
    public void handlePackageReady(@NonNull PackageReadyParam param) {
        ModuleData.setClassLoader(param.getClassLoader());
        HCData.setTargetPackageName(param.getPackageName());
        dispatchHooks(param.getPackageName(), param.getClassLoader());
    }

    private void dispatchHooks(@NonNull String targetPackage, @NonNull ClassLoader classLoader) {
        EntranceMap.get().forEach((className, entranceMap) -> {
            if (!entranceMap.mTargetPackage.equals(targetPackage)) {
                return;
            }
            if (!"Any".equals(entranceMap.mTargetBrand) && !DeviceTool.isRightRom(entranceMap.mTargetBrand)) {
                return;
            }

            // Android SDK compatibility check with upward & downward support (supports Android 16 SDK 36)
            if (!(entranceMap.mTargetSdks.length == 1 && entranceMap.mTargetSdks[0] == 0)) {
                int currentSdk = android.os.Build.VERSION.SDK_INT;
                int minSdk = Arrays.stream(entranceMap.mTargetSdks).min().orElse(0);
                int maxSdk = Arrays.stream(entranceMap.mTargetSdks).max().orElse(Integer.MAX_VALUE);
                if (entranceMap.mUpward) {
                    if (currentSdk < minSdk) {
                        return;
                    }
                } else if (entranceMap.mDownward) {
                    if (currentSdk > maxSdk) {
                        return;
                    }
                } else {
                    if (Arrays.stream(entranceMap.mTargetSdks).noneMatch(DeviceTool::isAndroidVersion)) {
                        return;
                    }
                }
            }

            if ("Xiaomi".equals(entranceMap.mTargetBrand)) {
                if (entranceMap.mTargetOS != -1) {
                    if (entranceMap.isHyperOS) {
                        if (!DeviceTool.isHyperOSVersion(entranceMap.mTargetOS) && !entranceMap.mUpward && !entranceMap.mDownward)
                            return;
                        if (entranceMap.mUpward && !(DeviceTool.getHyperOSVersion() >= entranceMap.mTargetOS))
                            return;
                        if (entranceMap.mDownward && !(DeviceTool.getHyperOSVersion() <= entranceMap.mTargetOS))
                            return;
                    } else if (DeviceTool.getMiuiVersion() != 0f) {
                        if (!DeviceTool.isMiuiVersion(entranceMap.mTargetOS) && !entranceMap.mUpward && !entranceMap.mDownward)
                            return;
                        if (entranceMap.mUpward && !(DeviceTool.getMiuiVersion() >= entranceMap.mTargetOS))
                            return;
                        if (entranceMap.mDownward && !(DeviceTool.getMiuiVersion() <= entranceMap.mTargetOS))
                            return;
                    } else {
                        return;
                    }
                }
            }

            if (Objects.equals(entranceMap.mTargetBrand, "samsung")) {
                if (!isEnableOneUi()) {
                    return; // 暂时关闭 OneUi 修改
                }
            }

            try {
                Class<?> hookClass = getClass().getClassLoader().loadClass(className);
                HCBase hcBase = (HCBase) hookClass.getDeclaredConstructor().newInstance();
                SaveLog.initLogToFile(hcBase.TAG);
                hcBase.onLoadPackage();
            } catch (Throwable e) {
                logENoSave(TAG, e);
            }
        });
    }

    private boolean isEnableOneUi() {
        return SystemPropTool.getProp("persist.hchen.oneui.enable", false);
    }
}
