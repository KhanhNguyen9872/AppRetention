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
package com.hchen.hooktool;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;

import androidx.annotation.NonNull;

import com.hchen.hooktool.log.AndroidLog;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import kotlin.text.Charsets;

/**
 * Xposed 模块运行环境检测工具类。
 * <p>
 * 提供对第三方 Xposed 宿主环境的检测能力，目前支持识别太极（TaiChi）和
 * LSPatch 两种常见的 Xposed 框架宿主。开发者可在运行时据此判断模块所处的
 * 环境类型并执行相应的适配策略。
 *
 * @author 焕晨HChen
 */
public final class ModuleState {
    private static final String TAG = "ModuleState";
    /**
     * 太极宿主应用包名。
     */
    private static final String TAICHI_PACKAGE = "me.weishu.exp";
    /**
     * 太极框架激活状态查询的 ContentProvider URI。
     */
    private static final String TAICHI_CP_URI = "content://me.weishu.exposed.CP/";
    /**
     * 太极 ContentProvider 查询激活状态的方法名。
     */
    private static final String TAICHI_METHOD_ACTIVE = "active";

    private ModuleState() {
    }

    /**
     * 检测当前设备是否运行在太极（TaiChi）Xposed 宿主环境中。
     * <p>
     * 检测逻辑分为两步：首先检查太极应用是否已安装，然后通过 ContentProvider
     * 查询太极框架的激活状态。内部会执行两次 ContentProvider 调用以应对
     * 首次调用可能失败的情况。
     *
     * @param context 应用上下文，用于访问 PackageManager 和 ContentResolver
     * @return {@code true} 表示处于太极环境且框架已激活，{@code false} 表示不在太极环境中
     */
    public static boolean isExpActive(@NonNull Context context) {
        try {
            context.getPackageManager().getPackageInfo(TAICHI_PACKAGE, PackageManager.GET_ACTIVITIES);

            ContentResolver contentResolver = context.getContentResolver();
            Uri uri = Uri.parse(TAICHI_CP_URI);
            Bundle result = null;
            Throwable lastFailure = null;
            try {
                result = contentResolver.call(uri, TAICHI_METHOD_ACTIVE, null, null);
            } catch (Throwable ignore) {
                lastFailure = ignore;
            }

            try {
                if (result == null)
                    result = contentResolver.call(uri, TAICHI_METHOD_ACTIVE, null, null);
            } catch (Throwable ignore) {
                lastFailure = ignore;
            }
            if (result == null) {
                // 首次调用可能失败故重试；双重失败时保留一次 W 级痕迹便于排查。
                if (lastFailure != null) {
                    AndroidLog.logW(TAG, "TaiChi active check failed after retry.", lastFailure);
                }
                return false;
            }
            return result.getBoolean(TAICHI_METHOD_ACTIVE, false);
        } catch (PackageManager.NameNotFoundException | SecurityException e) {
            // Android 11+ 无包可见性时 getPackageInfo 抛 SecurityException，同样视为非太极环境
            AndroidLog.logD(TAG, "TaiChi package not visible; treated as non-TaiChi environment.", e);
            return false;
        }
    }

    /**
     * 检测目标应用是否通过 LSPatch 框架加载，并提取其配置信息。
     * <p>
     * 通过读取目标应用 {@code AndroidManifest.xml} 中的 {@code lspatch}
     * 元数据字段进行检测。该字段以 Base64 编码的 JSON 格式存储 LSPatch 配置。
     * 解析成功后返回包含以下键值的 {@link Map}：
     * <ul>
     *   <li>{@code useManager} - 使用模式，值为 "本地模式" 或 "集成模式"</li>
     *   <li>{@code versionName} - LSPatch 框架版本名称</li>
     *   <li>{@code versionCode} - LSPatch 框架版本号</li>
     * </ul>
     * <p>
     * 调用此方法需确保应用已声明 {@code android.permission.QUERY_ALL_PACKAGES} 权限。
     *
     * @param context     应用上下文，用于访问 PackageManager
     * @param packageName 目标应用的包名
     * @return 包含 LSPatch 配置信息的 Map；若目标应用未使用 LSPatch 或解析失败则返回空 Map
     */
    @NonNull
    public static Map<String, String> isLSPatchActive(@NonNull Context context, @NonNull String packageName) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, PackageManager.GET_META_DATA);
            if (info.applicationInfo == null || info.applicationInfo.metaData == null)
                return new HashMap<>();
            String config = info.applicationInfo.metaData.getString("lspatch");
            if (config == null) return new HashMap<>();

            String json = new String(Base64.decode(config, Base64.DEFAULT), Charsets.UTF_8);
            JSONObject pathConfig = new JSONObject(json);
            boolean useManager = pathConfig.getBoolean("useManager");
            JSONObject lspConfig = pathConfig.getJSONObject("lspConfig");
            String versionName = lspConfig.getString("VERSION_NAME");
            String versionCode = lspConfig.getString("VERSION_CODE");
            HashMap<String, String> configMap = new HashMap<>();
            configMap.put("useManager", useManager ? "本地模式" : "集成模式");
            configMap.put("versionName", versionName);
            configMap.put("versionCode", versionCode);
            return configMap;
        } catch (Throwable e) {
            // 解析失败（Base64/JSON 异常、缺字段等），记录日志便于排查 LSPatch 配置损坏场景
            AndroidLog.logW("ModuleState", "LSPatch configuration parsing failed: " + packageName, e);
            return new HashMap<>();
        }
    }
}
