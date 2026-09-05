/*
 * This file is part of HookTool.
 *
 * HookTool is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * HookTool is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with HookTool. If not, see <https://www.gnu.org/licenses/lgpl-2.1>.
 *
 * Copyright (C) 2024–2026 HChenX
 */
package com.hchen.hooktool.utils;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hchen.hooktool.callback.IAppDataGetter;
import com.hchen.hooktool.core.CoreTool;
import com.hchen.hooktool.data.AppData;
import com.hchen.hooktool.log.AndroidLog;

import java.util.List;

/**
 * Android 应用包信息查询工具类。
 * <p>
 * 提供以下功能：
 * <ul>
 *     <li>应用安装状态检测与启用/禁用状态查询</li>
 *     <li>系统应用判定</li>
 *     <li>根据应用 uid 获取所属 user ID</li>
 *     <li>将多种包信息类型（{@link PackageInfo}、{@link ApplicationInfo}、{@link ResolveInfo} 等）
 *         统一转换为 {@link AppData} 数据结构</li>
 *     <li>同步获取应用数据（图标默认不加载，可按需显式开启）</li>
 * </ul>
 * <p>
 * 该类为纯工具类，所有方法均为静态方法，不允许实例化。
 * <p>
 * 注意（Android 11+ 包可见性）：本类基于 {@link PackageManager} 的查询结果受宿主应用
 * manifest 声明的包可见性约束。若宿主未声明对应包的 `<queries>` 或 {@code QUERY_ALL_PACKAGES}，
 * 对不可见包将返回 {@code false}/-1/{@code null} 等默认值，属预期行为；模块开发者应提示
 * 宿主配置相应可见性声明。
 *
 * @author 焕晨HChen
 */
public final class PackageTool {
    private static final String TAG = "PackageTool";

    private PackageTool() {
    }

    /**
     * 判断指定包名的应用是否已安装在当前设备上。
     * <p>
     * 内部通过 {@link PackageManager#getPackageInfo(String, int)} 进行查询，
     * 若抛出 {@link PackageManager.NameNotFoundException} 则视为未安装。
     *
     * @param context     非空上下文
     * @param packageName 待查询的应用包名
     * @return 已安装返回 {@code true}，未安装返回 {@code false}
     */
    public static boolean isInstalled(@NonNull Context context, @NonNull String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * 判断指定包名的应用是否已被禁用。
     * <p>
     * 基于 {@link PackageManager#getApplicationEnabledSetting(String)} 精确判断：
     * {@link PackageManager#COMPONENT_ENABLED_STATE_DISABLED} 与
     * {@link PackageManager#COMPONENT_ENABLED_STATE_DISABLED_USER} 均视为禁用；
     * {@link PackageManager#COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED}（首次使用自动启用）不算禁用。
     * 包未安装时返回 {@code false}，不抛出异常。
     *
     * @param context     非空上下文
     * @param packageName 待查询的应用包名
     * @return 已被禁用返回 {@code true}
     */
    public static boolean isDisabled(@NonNull Context context, @NonNull String packageName) {
        try {
            int state = context.getPackageManager().getApplicationEnabledSetting(packageName);
            return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
        } catch (IllegalArgumentException e) { // 包未安装
            return false;
        }
    }

    /**
     * 根据应用的 uid 获取所属的 user ID。
     * <p>
     * {@link UserHandle#getUserId(int)} 在 AOSP 中标注为 {@code @hide @UnsupportedAppUsage}
     * 的隐藏 API，无法在公开 SDK 中直接调用，故此处通过反射绕过编译期检查；
     * 若调用失败，返回 -1。
     *
     * @param uid 应用的 uid
     * @return 对应的 user ID，获取失败时返回 -1
     */
    public static int getUserId(int uid) {
        try {
            return (int) CoreTool.callStaticMethod(UserHandle.class, "getUserId", uid);
        } catch (Throwable e) {
            AndroidLog.logE(TAG, "Unable to resolve UserHandle.getUserId.", e);
            return -1;
        }
    }

    /**
     * 判断给定的 {@link ApplicationInfo} 是否属于系统应用。
     * <p>
     * 满足以下任一条件即视为系统应用：
     * <ul>
     *     <li>uid 大于等于 0 且小于 10000（默认值 -1 不会误判为系统应用）</li>
     *     <li>flags 包含 {@link ApplicationInfo#FLAG_SYSTEM}</li>
     *     <li>flags 包含 {@link ApplicationInfo#FLAG_UPDATED_SYSTEM_APP}</li>
     * </ul>
     *
     * @param app {@link ApplicationInfo} 对象，不得为 {@code null}
     * @return 是系统应用返回 {@code true}
     */
    public static boolean isSystem(@NonNull ApplicationInfo app) {
        if (app.uid >= 0 && app.uid < 10000) {
            return true;
        }
        return (app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
    }

    /**
     * 按包名判断应用是否为系统应用。
     * <p>
     * 内部通过 {@link PackageManager#getApplicationInfo(String, int)} 获取
     * {@link ApplicationInfo} 后委托给 {@link #isSystem(ApplicationInfo)}。
     * 包未安装时返回 {@code false}。
     *
     * @param context     非空上下文
     * @param packageName 待查询的应用包名
     * @return 是系统应用返回 {@code true}；包未安装返回 {@code false}
     * @see #isSystem(ApplicationInfo)
     */
    public static boolean isSystem(@NonNull Context context, @NonNull String packageName) {
        try {
            return isSystem(context.getPackageManager().getApplicationInfo(packageName, 0));
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * 通过包名获取应用的 uid。
     * <p>
     * 自动适配 API 33 及以上的 {@link PackageManager#getPackageUid(String, PackageManager.PackageInfoFlags)}。
     * 包未安装时返回 -1。
     *
     * @param context     非空上下文
     * @param packageName 待查询的应用包名
     * @return 应用 uid；包未安装返回 -1
     */
    public static int getPackageUid(@NonNull Context context, @NonNull String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return pm.getPackageUid(packageName, PackageManager.PackageInfoFlags.of(0));
            }
            return pm.getPackageUid(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    /**
     * 获取应用的版本名（versionName），包未安装时返回 {@code null}。
     *
     * @param context     非空上下文
     * @param packageName 待查询的应用包名
     * @return 版本名字符串；包未安装返回 {@code null}
     */
    @Nullable
    public static String getVersionName(@NonNull Context context, @NonNull String packageName) {
        try {
            return context.getPackageManager().getPackageInfo(packageName, 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /**
     * 获取应用的版本号（versionCode）。
     * <p>
     * 统一以 {@code long} 返回 {@link PackageInfo#getLongVersionCode()}，可承载超过 {@code int}
     * 范围的版本号。包未安装时返回 -1。
     *
     * @param context     非空上下文
     * @param packageName 待查询的应用包名
     * @return 版本号；包未安装返回 -1
     */
    public static long getVersionCode(@NonNull Context context, @NonNull String packageName) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            return info.getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    /**
     * 通过自定义查询逻辑同步获取应用数据。
     * <p>
     * 此方法为 {@link #getAppData(Context, boolean, IAppDataGetter)} 的便捷重载，
     * 默认不加载图标（{@code loadIcon = false}）。
     *
     * @param context       非空上下文
     * @param appDataGetter 自定义的应用数据查询回调，不得为 {@code null}
     * @param <T>           包信息类型（如 {@link PackageInfo}、{@link ApplicationInfo} 等）
     * @return 包含查询结果的 {@link AppData} 数组
     * @throws PackageManager.NameNotFoundException 查询失败时以原始异常向上抛出
     */
    @NonNull
    public static <T> AppData[] getAppData(@NonNull Context context, @NonNull IAppDataGetter<T> appDataGetter) {
        return getAppData(context, false, appDataGetter);
    }

    /**
     * 通过自定义查询逻辑同步获取应用数据。
     * <p>
     * 泛型参数 {@code T} 支持以下类型：{@link PackageInfo}、{@link ResolveInfo}、{@link ActivityInfo}、
     * {@link ApplicationInfo}、{@link ProviderInfo}。
     * <p>
     * 本方法在当前线程内同步执行查询与转换，并将结果直接返回。
     * <p>
     * 使用示例：
     * <pre>{@code
     * AppData[] appData = PackageTool.getAppData(context, false, new IAppDataGetter<PackageInfo>() {
     *      @Override
     *      @NonNull
     *      public List<PackageInfo> getPackages(@NonNull PackageManager pm) throws PackageManager.NameNotFoundException {
     *          return pm.getInstalledPackages(0);
     *      }
     * });
     * }</pre>
     *
     * @param context       非空上下文
     * @param loadIcon      是否加载应用图标；{@code true} 时填充 {@link AppData#icon}，
     *                      {@code false}（默认）时 {@link AppData#icon} 为 {@code null}，可显著降低
     *                      列表查询场景的内存与性能开销
     * @param appDataGetter 自定义的应用数据查询回调，不得为 {@code null}
     * @param <T>           包信息类型
     * @return 查询并转换后的 {@link AppData} 数组，恒不为 {@code null}
     * @see #createAppData(PackageManager, Object, boolean)
     */
    @NonNull
    public static <T> AppData[] getAppData(@NonNull Context context, boolean loadIcon,
                                           @NonNull IAppDataGetter<T> appDataGetter) {
        PackageManager packageManager = context.getPackageManager();
        List<T> ts = appDataGetter.getPackages(packageManager);
        AppData[] appDataArray = new AppData[ts.size()];
        int i = 0;
        for (T t : ts) {
            appDataArray[i++] = createAppData(packageManager, t, loadIcon);
        }
        return appDataArray;
    }

    /**
     * 将包信息对象转换为统一的 {@link AppData} 数据结构。
     * <p>
     * 此方法为 {@link #createAppData(PackageManager, Object, boolean)} 的便捷重载，
     * 默认不加载图标（{@code loadIcon = false}），
     * 转换后 {@link AppData#icon} 为 {@code null}。
     * <p>
     * 支持的输入类型包括：{@link PackageInfo}、{@link ApplicationInfo}、{@link ResolveInfo}、
     * {@link ActivityInfo}、{@link ServiceInfo}、{@link ProviderInfo}。
     * <p>
     * 转换过程中自动填充以下字段：包名、标签、系统应用标记、启用状态及用户 ID；
     * 其中版本号、版本名及 {@link AppData#packageInfo} 仅当输入为 {@link PackageInfo}
     * 类型时才被填充，其余输入类型下恒为 {@code null}。
     *
     * @param pm  {@link PackageManager} 实例，用于加载应用图标和标签
     * @param t   待转换的包信息对象
     * @param <T> 包信息类型
     * @return 填充完毕的 {@link AppData} 实例
     * @throws IllegalArgumentException 若传入的对象类型不受支持
     * @see #createAppData(PackageManager, Object, boolean)
     */
    @NonNull
    public static <T> AppData createAppData(@NonNull PackageManager pm, @NonNull T t) {
        return createAppData(pm, t, false);
    }

    /**
     * 将包信息对象转换为统一的 {@link AppData} 数据结构，可选是否加载图标。
     * <p>
     * 支持的输入类型包括：{@link PackageInfo}、{@link ApplicationInfo}、{@link ResolveInfo}、
     * {@link ActivityInfo}、{@link ServiceInfo}、{@link ProviderInfo}。类型之外的对象
     * 将抛出 {@link IllegalArgumentException}。
     * <p>
     * 转换过程中自动填充以下字段：包名、标签、系统应用标记、启用状态及用户 ID；
     * 其中版本号、版本名及 {@link AppData#packageInfo} 仅当输入为 {@link PackageInfo}
     * 类型时才被填充，其余输入类型下恒为 {@code null}。
     * <p>
     * {@link AppData#label} 在个别应用资源解析失败时会降级为 {@code null}，不会中断转换。
     * <p>
     * 注意：{@link AppData#isEnabled} 取自 {@link ApplicationInfo#enabled} 的静态清单标志，
     * 不等价于 {@link #isDisabled(Context, String)} 的运行时启用设置状态。
     *
     * @param pm       {@link PackageManager} 实例，用于加载应用图标和标签
     * @param t        待转换的包信息对象
     * @param loadIcon 是否加载应用图标；{@code true} 时填充 {@link AppData#icon}，
     *                 {@code false}（默认）时 {@link AppData#icon} 为 {@code null}
     * @param <T>      包信息类型
     * @return 填充完毕的 {@link AppData} 实例
     * @throws IllegalArgumentException 若传入的对象类型不受支持
     */
    @NonNull
    public static <T> AppData createAppData(@NonNull PackageManager pm, @NonNull T t, boolean loadIcon) {
        AppData appData = new AppData();
        PackageInfo packageInfo = null;
        ApplicationInfo applicationInfo;

        // 根据不同类型的 T 对象获取 ApplicationInfo
        // noinspection IfCanBeSwitch
        if (t instanceof PackageInfo info) {
            packageInfo = info;
            applicationInfo = info.applicationInfo;
            appData.versionName = info.versionName;
            appData.versionCode = Long.toString(info.getLongVersionCode());
        } else if (t instanceof ApplicationInfo appInfo) {
            applicationInfo = appInfo;
        } else if (t instanceof ResolveInfo resolveInfo) {
            applicationInfo = aboutResolveInfo(resolveInfo).applicationInfo;
        } else if (t instanceof ComponentInfo componentInfo) {
            // 统一覆盖 ActivityInfo、ServiceInfo、ProviderInfo 及其任何 ComponentInfo 子类
            applicationInfo = componentInfo.applicationInfo;
        } else {
            throw new IllegalArgumentException(
                "Unsupported package info type: " + t.getClass().getName() +
                    ". Supported types: PackageInfo, ApplicationInfo, ResolveInfo, " +
                    "ActivityInfo, ServiceInfo, ProviderInfo."
            );
        }

        // 填充应用数据
        if (applicationInfo != null) {
            appData.packageInfo = packageInfo;
            appData.applicationInfo = applicationInfo;
            if (loadIcon) {
                try {
                    appData.icon = BitmapTool.drawableToBitmap(applicationInfo.loadIcon(pm));
                } catch (RuntimeException e) {
                    // 单个应用图标加载/转换失败降级为 null，不中断整体列表转换。
                    AndroidLog.logW(TAG, "Failed to load icon for package: " + applicationInfo.packageName, e);
                    appData.icon = null;
                }
            }
            try {
                appData.label = applicationInfo.loadLabel(pm).toString();
            } catch (RuntimeException e) {
                // 个别应用资源解析失败（如畸形 label），降级为 null，不中断整体列表转换
                AndroidLog.logE(TAG, "Failed to load label for package: " + applicationInfo.packageName, e);
                appData.label = null;
            }
            appData.packageName = applicationInfo.packageName;
            appData.isSystemApp = isSystem(applicationInfo);
            appData.isEnabled = applicationInfo.enabled;
            appData.user = getUserId(applicationInfo.uid);
            appData.uid = applicationInfo.uid;
        }

        return appData;
    }

    /**
     * 从 {@link ResolveInfo} 中提取 {@link ComponentInfo}。
     * <p>
     * 按优先级依次尝试获取 {@code activityInfo}、{@code serviceInfo}、{@code providerInfo}。
     * 若三者均为 {@code null}，则抛出异常。
     *
     * @param resolveInfo {@link ResolveInfo} 对象
     * @return 包含 {@link ApplicationInfo} 的 {@link ComponentInfo}
     * @throws IllegalStateException 若无法从 ResolveInfo 中获取任何应用组件信息
     */
    @NonNull
    private static ComponentInfo aboutResolveInfo(@NonNull ResolveInfo resolveInfo) {
        if (resolveInfo.activityInfo != null) return resolveInfo.activityInfo;
        if (resolveInfo.serviceInfo != null) return resolveInfo.serviceInfo;
        if (resolveInfo.providerInfo != null) return resolveInfo.providerInfo;
        throw new IllegalStateException("Unable to obtain application information.");
    }
}
