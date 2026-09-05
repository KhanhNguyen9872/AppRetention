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
package com.hchen.hooktool.log;

import android.util.Log;

import com.hchen.hooktool.ModuleConfig;

/**
 * Android 平台原生日志输出工具类。
 * <p>
 * 封装 {@link android.util.Log} 的系统级 API，为 HookTool 框架提供统一的日志输出通道。
 * 输出逻辑复用 {@link AbstractLog} 的等级门控与消息格式化，本类仅负责将消息交给
 * {@link android.util.Log}。所有日志输出受全局配置约束：等级由 {@link ModuleConfig#getLogLevel()}
 * 控制，标签由 {@link ModuleConfig#getLogTag()} 统一指定；调用者提供的 {@code tag}
 * 参数以 {@code [tag]} 的格式嵌入日志消息体中。
 *
 * @author 焕晨HChen
 * @see AbstractLog
 * @see XposedLog
 */
public class AndroidLog extends AbstractLog {
    /**
     * 本类输出目标单例，供静态门面委托。
     */
    private static final AbstractLog IMPL = new AndroidLog();

    private AndroidLog() {
    }

    // ----------- logE ----------

    /**
     * 以 ERROR 级别输出一条纯文本日志。
     * <p>
     * 日志格式为：{@code [tag][E]: message}。
     * 当全局日志等级低于 {@link ModuleConfig#LOG_E} 时，此调用将被静默跳过。
     *
     * @param tag     业务侧自定义标识，将嵌入消息头部的方括号中
     * @param message 待输出的日志正文
     */
    public static void logE(String tag, String message) {
        logAt(IMPL, ModuleConfig.LOG_E, tag, message, null);
    }

    /**
     * 以 ERROR 级别输出一条仅包含异常信息的日志。
     * <p>
     * 异常对象的完整堆栈将由 {@link android.util.Log} 底层自动格式化输出。
     *
     * @param tag       业务侧自定义标识
     * @param throwable 待记录的异常实例
     */
    public static void logE(String tag, Throwable throwable) {
        logAt(IMPL, ModuleConfig.LOG_E, tag, null, throwable);
    }

    /**
     * 以 ERROR 级别输出一条同时包含文本描述和异常信息的日志。
     * <p>
     * 异常对象的完整堆栈将由 {@link android.util.Log} 底层自动格式化输出。
     *
     * @param tag       业务侧自定义标识，将嵌入消息头部的方括号中
     * @param message   待输出的日志正文
     * @param throwable 待记录的异常实例
     */
    public static void logE(String tag, String message, Throwable throwable) {
        logAt(IMPL, ModuleConfig.LOG_E, tag, message, throwable);
    }

    // -------- logW --------------

    /**
     * 以 WARN 级别输出一条纯文本日志。
     * <p>
     * 当全局日志等级低于 {@link ModuleConfig#LOG_W} 时，此调用将被静默跳过。
     *
     * @param tag     业务侧自定义标识，将嵌入消息头部的方括号中
     * @param message 待输出的日志正文
     */
    public static void logW(String tag, String message) {
        logAt(IMPL, ModuleConfig.LOG_W, tag, message, null);
    }

    /**
     * 以 WARN 级别输出一条仅包含异常信息的日志。
     *
     * @param tag       业务侧自定义标识
     * @param throwable 待记录的异常实例
     */
    public static void logW(String tag, Throwable throwable) {
        logAt(IMPL, ModuleConfig.LOG_W, tag, null, throwable);
    }

    /**
     * 以 WARN 级别输出一条同时包含文本描述和异常信息的日志。
     *
     * @param tag       业务侧自定义标识，将嵌入消息头部的方括号中
     * @param message   待输出的日志正文
     * @param throwable 待记录的异常实例
     */
    public static void logW(String tag, String message, Throwable throwable) {
        logAt(IMPL, ModuleConfig.LOG_W, tag, message, throwable);
    }

    // ------------ logI -------------

    /**
     * 以 INFO 级别输出一条纯文本日志。
     * <p>
     * 当全局日志等级低于 {@link ModuleConfig#LOG_I} 时，此调用将被静默跳过。
     *
     * @param tag     业务侧自定义标识，将嵌入消息头部的方括号中
     * @param message 待输出的日志正文
     */
    public static void logI(String tag, String message) {
        logAt(IMPL, ModuleConfig.LOG_I, tag, message, null);
    }

    /**
     * 以 INFO 级别输出一条仅包含异常信息的日志。
     *
     * @param tag       业务侧自定义标识
     * @param throwable 待记录的异常实例
     */
    public static void logI(String tag, Throwable throwable) {
        logAt(IMPL, ModuleConfig.LOG_I, tag, null, throwable);
    }

    /**
     * 以 INFO 级别输出一条同时包含文本描述和异常信息的日志。
     *
     * @param tag       业务侧自定义标识，将嵌入消息头部的方括号中
     * @param message   待输出的日志正文
     * @param throwable 待记录的异常实例
     */
    public static void logI(String tag, String message, Throwable throwable) {
        logAt(IMPL, ModuleConfig.LOG_I, tag, message, throwable);
    }

    // ---------- logD ---------------

    /**
     * 以 DEBUG 级别输出一条纯文本日志。
     * <p>
     * 当全局日志等级低于 {@link ModuleConfig#LOG_D} 时，此调用将被静默跳过。
     *
     * @param tag     业务侧自定义标识，将嵌入消息头部的方括号中
     * @param message 待输出的日志正文
     */
    public static void logD(String tag, String message) {
        logAt(IMPL, ModuleConfig.LOG_D, tag, message, null);
    }

    /**
     * 以 DEBUG 级别输出一条仅包含异常信息的日志。
     *
     * @param tag       业务侧自定义标识
     * @param throwable 待记录的异常实例
     */
    public static void logD(String tag, Throwable throwable) {
        logAt(IMPL, ModuleConfig.LOG_D, tag, null, throwable);
    }

    /**
     * 以 DEBUG 级别输出一条同时包含文本描述和异常信息的日志。
     *
     * @param tag       业务侧自定义标识，将嵌入消息头部的方括号中
     * @param message   待输出的日志正文
     * @param throwable 待记录的异常实例
     */
    public static void logD(String tag, String message, Throwable throwable) {
        logAt(IMPL, ModuleConfig.LOG_D, tag, message, throwable);
    }

    /**
     * 包级私有输出桥：将格式化消息输出到 {@link android.util.Log}。
     * <p>
     * 调用者 {@code tag} 以 {@code [tag]} 嵌入消息体（{@link android.util.Log} 只能按全局
     * {@link ModuleConfig#getLogTag()} 过滤），异常按优先级分发到对应 {@code Log.x} 方法。
     * 该桥同时被 {@link XposedLog} 在非 Xposed 环境回落时复用。
     *
     * @param priority  {@link android.util.Log} 优先级常量
     * @param tag       调用方自定义标识
     * @param message   已格式化的消息体（含等级前缀）
     * @param throwable 待记录的异常，可为 {@code null}
     */
    static void output(int priority, String tag, String message, Throwable throwable) {
        String fullTag = ModuleConfig.getLogTag();
        String fullMsg = "[" + tag + "]" + message;
        if (throwable == null) {
            Log.println(priority, fullTag, fullMsg);
        } else {
            switch (priority) {
                case Log.ERROR:
                    Log.e(fullTag, fullMsg, throwable);
                    break;
                case Log.WARN:
                    Log.w(fullTag, fullMsg, throwable);
                    break;
                case Log.INFO:
                    Log.i(fullTag, fullMsg, throwable);
                    break;
                default:
                    Log.d(fullTag, fullMsg, throwable);
                    break;
            }
        }
    }

    @Override
    protected void log(int priority, String tag, String message, Throwable throwable) {
        output(priority, tag, message, throwable);
    }
}
