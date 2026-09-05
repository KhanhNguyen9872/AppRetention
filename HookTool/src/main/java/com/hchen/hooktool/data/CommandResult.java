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

import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * 单条 Shell 命令执行的数据记录。
 * <p>
 * 该记录封装了一次 Shell 命令执行后的完整输出信息，包括所执行的命令字符串、
 * 进程退出码、标准输出（stdout）内容以及标准错误（stderr）内容，
 * 方便调用方对命令自身执行结果进行全方位的检查与分析。
 * <p>
 * 它仅承载"命令级"数据；"命令是否被正常执行完毕并产出数据"（流程层）由
 * {@link ShellResult} 建模，本记录作为 {@link ShellResult.Completed} 的载荷出现。
 * <p>
 * 构造时会对 {@code outputs} 与 {@code errors} 数组进行防御性拷贝，
 * 防止外部对数组的后续修改影响本记录；但数组内部元素仍为共享引用，
 * 跨线程传递时请勿并发修改数组内容。
 *
 * @param command  实际执行的完整命令字符串
 * @param exitCode 命令进程的退出码字符串（{@code "0"} 通常表示执行成功）
 * @param outputs  标准输出内容按行分割后所得的字符串数组
 * @param errors   标准错误输出内容按行分割后所得的字符串数组
 * @author 焕晨HChen
 * @see ShellResult
 */
public record CommandResult(@NonNull String command, @NonNull String exitCode,
                            @NonNull String[] outputs, @NonNull String[] errors) {
    public CommandResult {
        Objects.requireNonNull(command);
        Objects.requireNonNull(exitCode);
        Objects.requireNonNull(outputs);
        Objects.requireNonNull(errors);
        // 防御性拷贝，避免外部修改传入数组影响本记录的不可变性。
        outputs = outputs.clone();
        errors = errors.clone();
    }

    /**
     * 判断该命令是否执行成功。
     * <p>
     * 通过检查退出码是否等于 {@code "0"} 来确定执行是否成功。
     *
     * @return 当退出码为 {@code "0"} 时返回 {@code true}，否则返回 {@code false}
     */
    public boolean isSuccess() {
        return TextUtils.equals("0", exitCode);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CommandResult that)) return false;
        return Objects.equals(command, that.command) &&
            Objects.equals(exitCode, that.exitCode) &&
            Arrays.deepEquals(errors, that.errors) &&
            Arrays.deepEquals(outputs, that.outputs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(command, exitCode, Arrays.hashCode(outputs), Arrays.hashCode(errors));
    }

    @NonNull
    @Override
    public String toString() {
        return "CommandResult{" +
            "command='" + command + '\'' +
            ", exitCode='" + exitCode + '\'' +
            ", outputs=" + Arrays.deepToString(outputs) +
            ", errors=" + Arrays.deepToString(errors) +
            '}';
    }
}
