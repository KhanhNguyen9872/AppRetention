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
package com.hchen.hooktool.callback;

import android.content.pm.PackageManager;

import androidx.annotation.NonNull;

import com.hchen.hooktool.utils.PackageTool;

import java.util.List;

/**
 * 应用包信息查询契约接口。
 * <p>
 * 该接口定义了从 {@link PackageManager} 查询应用包信息的标准约定，
 * 由 {@link PackageTool#getAppData} 在转换阶段消费。泛型 {@code T} 表示
 * 包信息元素的类型，合法的取值仅限于以下受支持类型之一：
 * <ul>
 *     <li>{@link android.content.pm.PackageInfo}</li>
 *     <li>{@link android.content.pm.ApplicationInfo}</li>
 *     <li>{@link android.content.pm.ResolveInfo}</li>
 *     <li>{@link android.content.pm.ActivityInfo}</li>
 *     <li>{@link android.content.pm.ServiceInfo}</li>
 *     <li>{@link android.content.pm.ProviderInfo}</li>
 * </ul>
 * 若 {@link #getPackages} 返回的列表包含上述类型之外的元素，
 * {@link PackageTool#createAppData} 将在转换阶段抛出
 * {@link IllegalArgumentException}。
 * <p>
 * 本接口不承诺任何线程模型：查询与转换均在调用线程内同步执行，
 * 并发编排完全由调用方负责。
 *
 * @param <T> 包信息列表中元素的类型
 * @author 焕晨HChen
 * @see PackageTool#getAppData(android.content.Context, boolean, IAppDataGetter)
 * @see PackageTool#createAppData(PackageManager, Object, boolean)
 */
@FunctionalInterface
public interface IAppDataGetter<T> {
    /**
     * 从 PackageManager 中查询目标应用的包信息列表。
     * <p>
     * 实现者应在此方法内部调用 {@link PackageManager} 的相关 API
     * （例如 {@code getInstalledPackages} 或 {@code getInstalledApplications}），
     * 以获取满足业务需求的包信息集合。
     *
     * @param pm 用于执行包信息查询的 {@link PackageManager} 实例，不为 {@code null}
     * @return 包含目标应用包信息的列表，不为 {@code null}；列表元素也不得为 {@code null}，
     * 且元素类型必须落在类注释列出的受支持类型集合内
     */
    @NonNull
    List<T> getPackages(@NonNull PackageManager pm);
}
