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

import androidx.annotation.NonNull;

/**
 * Shell 命令执行流程的最终结果类型。
 * <p>
 * 取代 {@code exec()/async()} 旧有的"失败返回 {@code null}"的隐式约定，把"命令是否正常
 * 执行完毕并产出数据"（流程层）与"命令自身退出码是否为零"（命令层）两层正交语义
 * 显式建模，使调用方无需判空即可区分流程结果与具体失败原因：
 * <ul>
 *   <li>{@link Completed}：命令已正常执行完毕并产出数据，携带 {@link CommandResult}；
 *       命令自身是否成功需再经 {@link Completed#isSuccess()}（或 {@link CommandResult#isSuccess()}）判断</li>
 *   <li>{@link Failed}：shell 执行流程未能正常完成，携带 {@link ShellFailureReason} 指明原因</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>{@code
 *         ShellResult result = shellTool.cmd("ls").exec();
 *         if (result instanceof ShellResult.Completed completed) {
 *             boolean ok = completed.isSuccess();
 *             String[] out = completed.result().outputs();
 *         } else if (result instanceof ShellResult.Failed failed) {
 *             switch (failed.reason()) {
 *                 case TIMEOUT:
 *                     // 命令超时
 *                 case IO_ERROR:
 *                     // 流或进程异常
 *                 default:
 *                     // NOT_CONFIGURED / INTERCEPTED / INTERRUPTED ...
 *             }
 *         }
 * }</pre>
 *
 * @author 焕晨HChen
 * @see CommandResult
 * @see ShellFailureReason
 */
public sealed interface ShellResult {
    /**
     * 命令已正常执行完毕并产出数据的变体。
     * <p>
     * 注意：此变体只代表流程正常收敛，不代表命令自身成功——命令退出码可能非零。
     * 命令级成功判断请使用 {@link #isSuccess()}。
     *
     * @param result 单条命令的完整执行数据记录
     */
    record Completed(@NonNull CommandResult result) implements ShellResult {
        /**
         * 判断该命令自身是否执行成功。
         * <p>
         * 委托给底层 {@link CommandResult}，通过检查退出码是否等于 {@code "0"} 确定。
         *
         * @return 命令退出码为 {@code "0"} 时返回 {@code true}，否则返回 {@code false}
         */
        public boolean isSuccess() {
            return result.isSuccess();
        }
    }

    /**
     * shell 执行流程未能正常完成的变体。
     *
     * @param reason 流程失败原因分类
     */
    record Failed(@NonNull ShellFailureReason reason) implements ShellResult {
    }
}
