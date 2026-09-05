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
package com.hchen.hooktool.data;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 已安装应用程序的元数据容器。
 * <p>
 * 该类聚合了 Android 设备上某个已安装应用的核心属性，包括但不限于
 * {@link PackageInfo}、{@link ApplicationInfo}、多用户 ID、Linux UID、
 * 应用图标、显示名称、包名、版本信息，以及系统应用标志和启用状态标志。
 * <p>
 * 本类实现了 {@link Parcelable} 协议，可借助 {@link Parcel} 在不同 Android
 * 进程间高效地进行序列化与反序列化传输。
 *
 * @author 焕晨HChen
 */
public class AppData implements Parcelable {
    /**
     * 与该应用关联的 {@link PackageInfo} 对象，其中包含完整的包级别元数据信息。
     * 仅当输入为 {@link PackageInfo} 类型时才会被填充，其余输入类型下恒为 {@code null}。
     */
    @Nullable
    public PackageInfo packageInfo;
    /**
     * 与该应用关联的 {@link ApplicationInfo} 对象，记录应用级别的配置与属性。
     */
    public ApplicationInfo applicationInfo;
    /**
     * 多用户环境下的用户标识符。默认值 {@code -1} 表示尚未显式填充（例如获取失败），并非特指主用户。
     */
    public int user = -1;
    /**
     * 该应用在 Linux 层面的用户标识符（UID）。默认值 {@code -1} 表示尚未分配。
     */
    public int uid = -1;
    /**
     * 应用的图标位图。
     * <p>
     * 默认不加载，为 {@code null}；仅在调用方显式传入 {@code loadIcon=true} 时填充。
     * 该字段不参与 {@link Parcelable} 序列化，跨进程重建后恒为 {@code null}。
     */
    @Nullable
    public Bitmap icon;
    /**
     * 应用面向用户展示的名称标签。
     * <p>
     * 在个别应用资源解析失败时可能为 {@code null}。
     */
    public String label;
    /**
     * 应用的唯一包名（例如 {@code "com.android.settings"}）。
     */
    public String packageName;
    /**
     * 应用的版本名称字符串（例如 {@code "1.2.3"}）。同 {@link #packageInfo}，仅当通过
     * {@link PackageInfo} 获取数据时才会被填充。
     */
    public String versionName;
    /**
     * 应用的版本号字符串。同 {@link #packageInfo}，仅当通过 {@link PackageInfo} 获取数据时才会被填充。
     */
    public String versionCode;
    /**
     * 指示该应用是否为系统预装应用。{@code true} 表示为系统应用。
     */
    public boolean isSystemApp;
    /**
     * 指示该应用当前是否处于启用状态。{@code true} 表示已启用，{@code false} 表示已被停用。
     * <p>
     * 注意：该标志取自 {@link ApplicationInfo#enabled} 的静态清单标志，
     * 不等价于 {@link com.hchen.hooktool.utils.PackageTool#isDisabled} 的运行时启用设置状态
     * （后者基于 {@link android.content.pm.PackageManager#getApplicationEnabledSetting}，
     * 能区分用户禁用与临时禁用）。需要精确的禁用判定时请使用 {@code PackageTool.isDisabled}。
     */
    public boolean isEnabled;

    /**
     * 构造一个所有字段均保持默认值的空 {@link AppData} 实例。
     */
    public AppData() {
    }

    @NonNull
    @Override
    public String toString() {
        return "AppData{" +
            "packageInfo=" + packageInfo +
            ", applicationInfo=" + applicationInfo +
            ", user=" + user +
            ", uid=" + uid +
            ", icon=" + icon +
            ", label='" + label + '\'' +
            ", packageName='" + packageName + '\'' +
            ", versionName='" + versionName + '\'' +
            ", versionCode='" + versionCode + '\'' +
            ", isSystemApp=" + isSystemApp +
            ", isEnabled=" + isEnabled +
            '}';
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof AppData appData)) return false;

        return user == appData.user &&
            uid == appData.uid &&
            isSystemApp == appData.isSystemApp &&
            isEnabled == appData.isEnabled &&
            Objects.equals(packageName, appData.packageName) &&
            Objects.equals(versionName, appData.versionName) &&
            Objects.equals(versionCode, appData.versionCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            user,
            uid,
            packageName,
            versionName,
            versionCode,
            isSystemApp,
            isEnabled
        );
    }

    /**
     * 从 {@link Parcel} 反序列化构造 {@link AppData} 实例的私有构造方法。
     * <p>
     * 字段的读取顺序必须与 {@link #writeToParcel(Parcel, int)} 中的写入顺序严格一致。
     * 注意：{@link #icon} 不参与序列化，重建后恒为 {@code null}。
     *
     * @param in 携带序列化数据的源 {@link Parcel} 对象
     */
    private AppData(@NonNull Parcel in) {
        packageInfo = in.readParcelable(PackageInfo.class.getClassLoader());
        applicationInfo = in.readParcelable(ApplicationInfo.class.getClassLoader());
        user = in.readInt();
        uid = in.readInt();
        label = in.readString();
        packageName = in.readString();
        versionName = in.readString();
        versionCode = in.readString();
        isSystemApp = in.readByte() != 0;
        isEnabled = in.readByte() != 0;
    }

    /**
     * 将本实例的全部字段按固定顺序序列化写入指定的 {@link Parcel}。
     * <p>
     * 注意：{@link #icon} 因可能携带大体积位图、且跨进程传输易超出 Binder 事务缓冲，
     * 故不参与序列化。如需跨进程共享图标，请自行以字节流等旁路方式传递。
     *
     * @param dest  目标 {@link Parcel} 对象，序列化数据将写入此对象中
     * @param flags 附加控制标志，用于指示是否需要写入特殊对象引用（如文件描述符），通常传入 {@code 0}
     */
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelable(packageInfo, flags);
        dest.writeParcelable(applicationInfo, flags);
        dest.writeInt(user);
        dest.writeInt(uid);
        dest.writeString(label);
        dest.writeString(packageName);
        dest.writeString(versionName);
        dest.writeString(versionCode);
        dest.writeByte((byte) (isSystemApp ? 1 : 0));
        dest.writeByte((byte) (isEnabled ? 1 : 0));
    }

    /**
     * 返回此 Parcelable 对象的内容描述标志位。
     *
     * @return 固定返回 {@code 0}，表示该对象不包含需要特殊处理的文件描述符
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Parcelable 反序列化工厂实例，负责从 {@link Parcel} 中重建 {@link AppData} 对象，
     * 以及创建指定容量的 {@code AppData} 数组以供框架内部使用。
     */
    public static final Creator<AppData> CREATOR = new Creator<AppData>() {
        @Override
        public AppData createFromParcel(Parcel in) {
            return new AppData(in);
        }

        @Override
        public AppData[] newArray(int size) {
            return new AppData[size];
        }
    };
}
