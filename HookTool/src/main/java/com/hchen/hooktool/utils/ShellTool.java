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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hchen.hooktool.callback.ICommandListener;
import com.hchen.hooktool.callback.IExecListener;
import com.hchen.hooktool.data.CommandResult;
import com.hchen.hooktool.data.ShellFailureReason;
import com.hchen.hooktool.data.ShellResult;
import com.hchen.hooktool.log.AndroidLog;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shell 命令执行工具类。
 * <p>
 * 提供同步（{@link #exec()}）和异步（{@link #async()}）两种 Shell 命令执行能力，
 * 支持 Root 和普通两种模式（默认分别使用 {@code su} / {@code sh}，可通过
 * {@link #setShellCommands} 自定义）。内部维护持久化的 Shell 进程流，
 * 通过自增命令 ID 与带随机 nonce 的结束标记关联每条命令的输出，实现多命令执行时结果的正确分离。
 * <p>
 * 命令以 {@link CompletableFuture}{@code <ShellResult>} 作为统一执行句柄：同步与异步
 * 走同一条提交路径，仅调用方是否阻塞等待不同。成功与各类失败统一以 {@link ShellResult}
 * 建模（不再用 {@code null} 隐式表达失败），命令必然收敛（配对完成 / 流结束兜底 / 命令超时兜底），
 * 不会永久阻塞。
 * <p>
 * 使用示例：
 * <pre>{@code
 *         ShellTool shellTool = ShellTool.obtain(true);
 *         ShellResult result = shellTool.cmd("ls").exec();
 *         if (result instanceof ShellResult.Completed completed) {
 *             boolean ok = completed.isSuccess();
 *             String[] out = completed.result().outputs();
 *         } else {
 *             // result instanceof ShellResult.Failed failure
 *         }
 *         shellTool.cmd("""
 *             if [[ 1 == 1 ]]; then
 *                 echo hello;
 *             elif [[ 1 == 2 ]]; then
 *                 echo world;
 *             fi
 *             """).exec();
 *         shellTool.enableSplicingMode()
 *             .cmd("if [[ true == true ]]; then")
 *             .cmd("  echo hello               ")
 *             .cmd("fi                         ")
 *             .exec();
 *         CompletableFuture<ShellResult> future = shellTool.cmd("echo hello").async();
 *         shellTool.setExecListener(new IExecListener() {
 *             @Override
 *             public void output(@NonNull String command, @NonNull String exitCode, @NonNull String[] outputs) {
 *                 IExecListener.super.output(command, exitCode, outputs);
 *             }
 *         });
 *         ShellTool.close();
 * }
 * @author 焕晨HChen
 */
public final class ShellTool {
    private static final String TAG = "ShellTool";
    private static final byte[] LINE_BREAK = "\n".getBytes(StandardCharsets.UTF_8);
    /**
     * 同步 {@link #exec()} 阻塞等待结果的最大时长（毫秒）。
     */
    private static final long EXEC_TIMEOUT_MS = 10_000;
    /**
     * 命令级兜底超时（毫秒）：超过该时长仍未收敛的命令将以失败完成，防止命令句柄泄漏。
     */
    private static final long COMMAND_TIMEOUT_MS = 15_000;
    /**
     * Root 检测 {@code su -c true} 的等待超时（秒）：超时视为无 Root 权限，避免单线程检测被挂起的 su 占死。
     */
    private static final long ROOT_CHECK_TIMEOUT_S = 5;

    private static final ShellTool instance = new ShellTool();
    private static volatile boolean isRoot = false;
    private static volatile String[] shellCommands = new String[]{"su", "sh"};
    private static volatile IExecListener globalExecListeners;
    private static volatile ICommandListener globalCommandListener;
    private static volatile ShellImpl shellImpl;
    /**
     * 全局自增命令 ID，跨进程重建持续递增，永不复用。
     */
    private static final AtomicLong nextCommandId = new AtomicLong();

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "HookTool-Shell-Scheduler");
        thread.setDaemon(true);
        return thread;
    });
    private static final ExecutorService ROOT_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "HookTool-RootCheck");
        thread.setDaemon(true);
        return thread;
    });

    private ShellTool() {
        shellImpl = new ShellImpl();
    }

    /**
     * 获取 {@link ShellTool} 单例并初始化 Shell 进程流。
     * <p>
     * 使用当前通过 {@link #setRoot(boolean)} 设定的模式启动 Shell 进程。
     *
     * @return {@link ShellTool} 单例实例
     */
    @NonNull
    public static ShellTool obtain() {
        shellImpl.init();
        return instance;
    }

    /**
     * 获取 {@link ShellTool} 单例，同时指定 Root 模式并初始化 Shell 进程流。
     *
     * @param isRoot {@code true} 使用 {@code su}（Root 模式），{@code false} 使用 {@code sh}（普通模式）
     * @return {@link ShellTool} 单例实例
     */
    @NonNull
    public static ShellTool obtain(boolean isRoot) {
        setRoot(isRoot);
        return obtain();
    }

    /**
     * 设置 Shell 的 Root 模式。
     *
     * @param isRoot {@code true} 启用 Root 模式，{@code false} 使用普通模式
     * @return {@link ShellTool} 单例实例，支持链式调用
     */
    @NonNull
    public static ShellTool setRoot(boolean isRoot) {
        ShellTool.isRoot = isRoot;
        return instance;
    }

    /**
     * 自定义 Shell 启动命令。
     * <p>
     * 数组长度必须为 2：第一个元素为 Root 模式命令，第二个为普通模式命令。
     * 数组的合法性在下次 {@link #obtain()} 初始化进程时校验，长度不足 2 或含空元素将抛出
     * {@link IllegalArgumentException}。该方法修改的是全局静态配置，仅作用于当前进程。
     *
     * @param commands Shell 命令数组，长度必须为 2
     * @return {@link ShellTool} 单例实例，支持链式调用
     */
    @NonNull
    public static ShellTool setShellCommands(@NonNull String[] commands) {
        shellCommands = commands.clone();
        return instance;
    }

    /**
     * 设置全局执行监听器，用于接收所有命令（同步与异步）的输出和错误回调。
     * <p>
     * 设置新监听器会<strong>覆盖此前注册的全局监听器</strong>（全局仅保留单个槽位）；传 {@code null} 可取消监听。
     * 监听器回调在读取线程上执行，回调内请勿同步调用 {@link #exec()}——那会阻塞读取线程自身，
     * 导致回调内发起的新命令因输出无人读取而在 10 秒后超时返回。
     *
     * @param execListener 执行监听器实例；传 {@code null} 可取消监听
     * @return {@link ShellTool} 单例实例，支持链式调用
     */
    @NonNull
    public static ShellTool setExecListener(@Nullable IExecListener execListener) {
        globalExecListeners = execListener;
        return instance;
    }

    /**
     * 设置全局命令监听器，在命令执行前进行拦截。
     * <p>
     * 监听器的 {@code onCommand} 方法返回 {@code false} 时将阻止该命令的执行。
     *
     * @param listener 命令监听器实例；传 {@code null} 可取消监听
     * @return {@link ShellTool} 单例实例，支持链式调用
     */
    @NonNull
    public static ShellTool setCommandListener(@Nullable ICommandListener listener) {
        globalCommandListener = listener;
        return instance;
    }

    /**
     * 判断当前 Shell 进程流是否处于活跃状态。
     *
     * @return Shell 进程存活且读取线程正常运行时返回 {@code true}
     */
    public static boolean isActive() {
        return shellImpl.isActive();
    }

    /**
     * 关闭当前 Shell 进程流并释放所有相关资源。
     * <p>
     * 关闭流程包括：发送 {@code exit} 命令、等待进程退出、关闭输出流、终止读取线程。
     * 关闭后<strong>不会自动重建</strong>，需要重建请重新调用 {@link #obtain()}。
     */
    public static void close() {
        shellImpl.close();
    }

    /**
     * 启用命令拼接模式。
     * <p>
     * 启用后，通过 {@link #cmd(String)} 添加的多条命令将以换行符连接后拼接为一条命令一次性执行。
     * 拼接模式在执行一次后自动关闭。
     *
     * @return {@link ShellTool} 单例实例，支持链式调用
     */
    @NonNull
    public ShellTool enableSplicingMode() {
        shellImpl.enableSplicingMode();
        return instance;
    }

    /**
     * 添加一条待执行的 Shell 命令。
     * <p>
     * 若处于拼接模式，命令将被暂存到拼接列表中；否则直接覆盖当前待执行命令。
     * 注意：命令内容若包含 {@code exit} 会终止持久化 Shell 会话，之后需重新 {@link #obtain()}。
     *
     * @param cmd 命令字符串，不可为 {@code null}
     * @return {@link ShellTool} 单例实例，支持链式调用
     */
    @NonNull
    public ShellTool cmd(@NonNull String cmd) {
        shellImpl.cmd(cmd);
        return instance;
    }

    /**
     * 同步执行已添加的命令，阻塞当前线程直到命令执行完毕并返回结果。
     * <p>
     * 阻塞等待存在超时（默认 10 秒），超时返回 {@link ShellFailureReason#TIMEOUT} 失败结果，
     * 不会永久阻塞。若需长时间运行，请改用 {@link #async()}。
     *
     * @return 命令执行流程结果：流程收敛为 {@link ShellResult.Completed}，流程失败为 {@link ShellResult.Failed}；
     * 不会为 {@code null}
     */
    @NonNull
    public ShellResult exec() {
        return shellImpl.exec();
    }

    /**
     * 异步执行已添加的命令，立即返回结果句柄，不阻塞当前线程。
     * <p>
     * 返回的 {@link CompletableFuture} 在命令收敛时携带 {@link ShellResult}；可通过 {@code get()} 阻塞等待
     * 或 {@code whenComplete} 异步监听。预飞行失败（未添加命令、被拦截）直接返回已完成的
     * {@code ShellResult.Failed} 结果，不会返回 {@code null}。同时命令完成会触发全局执行监听器
     * （{@link #setExecListener}）的对应回调。
     *
     * @return 命令结果的 {@link CompletableFuture} 句柄，不为 {@code null}
     */
    @NonNull
    public CompletableFuture<ShellResult> async() {
        return shellImpl.submitCommand(null);
    }

    /**
     * 异步执行已添加的命令，并通过指定的监听器接收结果。
     *
     * @param execListener 用于接收本次命令执行结果的监听器，不为 {@code null}
     * @return 命令结果的 {@link CompletableFuture} 句柄，不为 {@code null}
     */
    @NonNull
    public CompletableFuture<ShellResult> async(@NonNull IExecListener execListener) {
        return shellImpl.submitCommand(execListener);
    }

    // --------------------------------------- Root Check -------------------------------------------

    /**
     * 同步检查当前设备是否具备 Root 权限。
     *
     * @return 具备 Root 权限返回 {@code true}
     */
    public static boolean isRootAvailable() {
        return checkRootSync(null);
    }

    /**
     * 同步检查当前设备是否具备 Root 权限，并通过监听器返回检测结果。
     *
     * @param execListener 接收 Root 检测结果的监听器
     * @return 具备 Root 权限返回 {@code true}
     */
    public static boolean isRootAvailable(@NonNull IExecListener execListener) {
        return checkRootSync(execListener);
    }

    /**
     * 检查当前设备是否具备 Root 权限。
     * <p>
     * 历史方法：{@code sync} 参数已无意义，本方法始终执行<strong>真实同步检测</strong>并返回真实结果。
     * 需要异步检测请使用 {@link #isRootAvailableAsync(IExecListener)}。
     *
     * @param sync         已忽略，统一为真实同步检测
     * @param execListener 接收 Root 检测结果的监听器，可为 {@code null}
     * @return 具备 Root 权限返回 {@code true}
     * @deprecated 请使用 {@link #isRootAvailable()} 或 {@link #isRootAvailableAsync(IExecListener)}
     */
    @Deprecated
    public static boolean isRootAvailable(boolean sync, @Nullable IExecListener execListener) {
        return checkRootSync(execListener);
    }

    /**
     * 异步检查当前设备是否具备 Root 权限。
     * <p>
     * 在独立后台线程中执行检测，结果通过返回的 {@link CompletableFuture} 获取，
     * 同时通过监听器的 {@code onRootResult} 回调返回。
     *
     * @param execListener 接收 Root 检测结果的监听器，可为 {@code null}
     * @return 携带检测结果的 {@link CompletableFuture}，不为 {@code null}
     */
    @NonNull
    public static CompletableFuture<Boolean> isRootAvailableAsync(@Nullable IExecListener execListener) {
        return CompletableFuture.supplyAsync(() -> checkRootSync(execListener), ROOT_EXECUTOR);
    }

    /**
     * 通过执行 {@code su -c true} 并检查退出码，同步判断 Root 权限是否可用。
     * <p>
     * 等待子进程退出带 5 秒超时，避免个别设备上 {@code su} 授权弹窗挂起导致单线程
     * {@link #ROOT_EXECUTOR} 被永久占死、后续异步检测全部排队。
     *
     * @param execListener 接收 Root 检测结果的监听器，可为 {@code null}
     * @return 具备 Root 权限返回 {@code true}
     */
    private static boolean checkRootSync(@Nullable IExecListener execListener) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("su -c true");
            // 丢弃子进程的 stdout/stderr，防止输出填满管道缓冲导致 waitFor 永久阻塞，
            // 并避免每次调用泄漏两个输入流。
            try (InputStream ignored = process.getInputStream();
                 InputStream ignoredErr = process.getErrorStream()) {
                if (!process.waitFor(ROOT_CHECK_TIMEOUT_S, TimeUnit.SECONDS)) {
                    // 超时未退出：视为无 Root 权限，释放进程。
                    if (execListener != null) {
                        execListener.onRootResult(false, "-1");
                    }
                    return false;
                }
                int exitCode = process.exitValue();
                if (execListener != null) {
                    execListener.onRootResult(exitCode == 0, String.valueOf(exitCode));
                }
                return exitCode == 0;
            }
        } catch (IOException e) {
            AndroidLog.logE(TAG, "Error executing 'su -c true' for root check.", e);
            if (execListener != null) {
                execListener.onRootResult(false, "-1");
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            AndroidLog.logE(TAG, "Root check ('su -c true') interrupted.", e);
            if (execListener != null) {
                execListener.onRootResult(false, "-1");
            }
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    // ----------------------------------------------------------------------------------------------

    /**
     * Shell 流内部实现类。
     * <p>
     * 负责管理 Shell 进程的完整生命周期：进程启动、命令提交、写入、同步等待、关闭与异常处理。
     * 命令统一经 {@link #submitCommand} 提交为 {@link CompletableFuture}{@code <ShellResult>}，
     * 同步/异步共用同一路径。
     */
    final class ShellImpl {
        private final Object writeLock = new Object();
        private final AtomicBoolean brokenReported = new AtomicBoolean();
        private final List<String> splicingCommands = new ArrayList<>();

        private volatile boolean isSplicingMode = false;
        private String command = null;
        private volatile Process process = null;
        private volatile DataOutputStream os = null;
        private volatile StreamThread streamThread = null;
        private volatile String token = "";
        private volatile boolean closing = false;
        private volatile int generation = 0;

        private ShellImpl() {
        }

        private synchronized void init() {
            if (isActive()) return;
            validateShellCommands();

            closing = false;
            brokenReported.set(false);
            command = null;
            isSplicingMode = false;
            splicingCommands.clear();

            Process newProcess = null;
            try {
                newProcess = Runtime.getRuntime().exec(isRoot ? shellCommands[0] : shellCommands[1]);
                DataOutputStream newOs = new DataOutputStream(newProcess.getOutputStream());
                token = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                generation++;
                process = newProcess;
                os = newOs;
                streamThread = new StreamThread(this, newProcess.getInputStream(), newProcess.getErrorStream());
                streamThread.start();
            } catch (IOException e) {
                // 已创建的进程在此销毁，避免子进程与管道泄漏。
                if (newProcess != null) {
                    newProcess.destroy();
                }
                process = null;
                os = null;
                streamThread = null;
                throw new IllegalStateException("Error initializing shell stream.", e);
            }
        }

        private void validateShellCommands() {
            String[] commands = shellCommands;
            if (commands == null || commands.length < 2
                || commands[0] == null || commands[0].isEmpty()
                || commands[1] == null || commands[1].isEmpty()) {
                throw new IllegalArgumentException(
                    "setShellCommands must provide a non-empty command array of length 2.");
            }
        }

        private synchronized void enableSplicingMode() {
            this.isSplicingMode = true;
        }

        private synchronized void cmd(@NonNull String cmd) {
            if (!isActive()) {
                throw new IllegalStateException("Shell stream is dead.");
            }
            if (isSplicingMode) {
                splicingCommands.add(cmd);
            } else {
                command = cmd;
            }
        }

        /**
         * 统一提交命令入口：将当前待执行命令（或拼接结果）写入 Shell 流并返回结果句柄。
         *
         * @param perCmdListener 本次命令专属监听器，可为 {@code null}
         * @return 命令结果句柄，不为 {@code null}；无待执行命令或被拦截时返回已完成的失败结果
         * @throws IllegalStateException 当 Shell 流已失效时抛出
         */
        @NonNull
        private synchronized CompletableFuture<ShellResult> submitCommand(@Nullable IExecListener perCmdListener) {
            if (!isActive()) {
                throw new IllegalStateException("Shell stream is dead.");
            }

            splicingCommandIfNeeded();
            if (command == null) {
                return CompletableFuture.completedFuture(new ShellResult.Failed(ShellFailureReason.NOT_CONFIGURED));
            }
            if (globalCommandListener != null) {
                boolean allowed;
                try {
                    allowed = globalCommandListener.onCommand(command);
                } catch (Throwable e) {
                    // 监听器回调异常隔离：不中断执行，保守拦截该命令。
                    AndroidLog.logW(TAG, "Error during global command listener callback.", e);
                    allowed = false;
                }
                if (!allowed) {
                    command = null;
                    return CompletableFuture.completedFuture(new ShellResult.Failed(ShellFailureReason.INTERCEPTED));
                }
            }

            String cmd = command;
            command = null;
            long id = nextCommandId.incrementAndGet();
            // 每命令独立随机 nonce：结束标记前缀不可预测，防止命令输出内容伪造标记截断/污染结果。
            String nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            StreamThread.PendingCommand pc =
                new StreamThread.PendingCommand(id, generation, cmd, perCmdListener, nonce);
            streamThread.pending.put(id, pc);
            String marker = String.format(Locale.ROOT,
                "__HT_RET=$?; echo HTM_%s_%d_%s,$__HT_RET; echo HTM_%s_%d_%s,$__HT_RET 1>&2; unset __HT_RET",
                token, id, nonce, token, id, nonce
            );

            synchronized (writeLock) {
                boolean ok = write("{");
                ok &= writeAll(cmd.split("\n"));
                ok &= write("}");
                ok &= write(marker);
                if (!ok) {
                    streamThread.pending.remove(id);
                    pc.future.complete(new ShellResult.Failed(ShellFailureReason.IO_ERROR));
                    return pc.future;
                }
            }

            scheduleTimeout(pc);
            return pc.future;
        }

        @NonNull
        private ShellResult exec() {
            String cmd = command;
            CompletableFuture<ShellResult> future = submitCommand(null);
            try {
                // 预飞行失败（未添加命令/被拦截）的 future 已处于完成态，get 立即返回。
                return future.get(EXEC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                AndroidLog.logW(TAG, "Shell exec timed out for command: " + cmd, e);
                return new ShellResult.Failed(ShellFailureReason.TIMEOUT);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                AndroidLog.logW(TAG, "Shell exec interrupted while waiting for result.", e);
                return new ShellResult.Failed(ShellFailureReason.INTERRUPTED);
            } catch (ExecutionException e) {
                AndroidLog.logE(TAG, "Shell exec failed for command: " + cmd, e);
                return new ShellResult.Failed(ShellFailureReason.IO_ERROR);
            }
        }

        private void splicingCommandIfNeeded() {
            if (!isSplicingMode) {
                return;
            }
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < splicingCommands.size(); i++) {
                if (i > 0) {
                    builder.append('\n');
                }
                builder.append(splicingCommands.get(i));
            }
            isSplicingMode = false;
            splicingCommands.clear();
            // 注意：builder.isEmpty() 依赖 CharSequence.isEmpty()（API 35），minSdk 30 下不可用，用 length() == 0
            command = builder.length() == 0 ? null : builder.toString();
        }

        /**
         * 为命令调度兜底超时任务：超时仍未收敛时以失败结果完成，防止命令句柄泄漏。
         * <p>
         * 与 {@link StreamThread#completeCommand} 共用 {@code completed} CAS：超时与正常完成
         * 只能胜出一方，杜绝"回调已分发真实结果而句柄却报超时"的窗口。
         *
         * @param pc 待调度的命令数据
         */
        private void scheduleTimeout(@NonNull StreamThread.PendingCommand pc) {
            pc.timeoutTask = SCHEDULER.schedule(() -> {
                if (!pc.completed.compareAndSet(false, true)) {
                    return; // 已由正常完成路径胜出，放弃超时。
                }
                pc.future.complete(new ShellResult.Failed(ShellFailureReason.TIMEOUT));
                StreamThread st = streamThread;
                if (st != null) {
                    st.pending.remove(pc.id);
                }
            }, COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        private boolean write(@NonNull String command) {
            return write(command.getBytes(StandardCharsets.UTF_8));
        }

        private boolean write(@NonNull byte[] bytes) {
            try {
                DataOutputStream output = os;
                if (output == null) {
                    return false;
                }
                output.write(bytes);
                output.write(LINE_BREAK);
                output.flush();
                return true;
            } catch (IOException e) {
                AndroidLog.logE(TAG, "Error writing bytes to shell stream.", e);
                return false;
            }
        }

        private boolean writeAll(@NonNull String[] commands) {
            try {
                DataOutputStream output = os;
                if (output == null) {
                    return false;
                }
                for (String cmd : commands) {
                    output.write(cmd.getBytes(StandardCharsets.UTF_8));
                    output.write(LINE_BREAK);
                }
                output.flush();
                return true;
            } catch (IOException e) {
                AndroidLog.logE(TAG, "Error writing commands to shell stream: " + java.util.Arrays.toString(commands), e);
                return false;
            }
        }

        private void close() {
            StreamThread st;
            synchronized (this) {
                if (process == null && streamThread == null) {
                    return;
                }
                closing = true;
                try {
                    if (isActive()) {
                        synchronized (writeLock) {
                            write("exit");
                        }
                    }

                    if (process != null) {
                        try {
                            process.waitFor(3, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        process.destroy();
                        try {
                            process.waitFor(1, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }

                    if (os != null) {
                        try {
                            os.close();
                        } catch (IOException e) {
                            AndroidLog.logE(TAG, "Error closing shell output stream.", e);
                        }
                    }

                    if (streamThread != null) {
                        for (StreamThread.PendingCommand pc : streamThread.pending.values()) {
                            if (!pc.future.isDone()) {
                                pc.future.complete(
                                    new ShellResult.Failed(ShellFailureReason.IO_ERROR));
                            }
                            if (pc.timeoutTask != null) {
                                pc.timeoutTask.cancel(false);
                            }
                        }
                    }
                } finally {
                    process = null;
                    os = null;
                    command = null;
                    isSplicingMode = false;
                    splicingCommands.clear();
                }
                st = streamThread;
                streamThread = null;
            }
            // 在 ShellImpl monitor 之外 join 读取线程：避免与读取线程内的 onBrokenPipe→close()
            // 互等（持锁 join 会造成 3 秒停顿并阻塞所有 Shell 操作）。
            if (st != null) {
                st.close();
            }
        }

        private synchronized boolean isActive() {
            if (streamThread == null || process == null || closing) {
                return false;
            }
            return process.isAlive() && streamThread.isActive();
        }

        /**
         * 处理 Shell 进程异常死亡：保证仅上报一次，完成在途命令并回调 {@code brokenPipe}，随后关闭进程流。
         * <strong>不会自动重建进程。</strong>
         *
         * @param leftoverErrors 进程死亡时残留的错误输出行
         */
        private void onBrokenPipe(@NonNull String[] leftoverErrors) {
            if (!brokenReported.compareAndSet(false, true)) {
                return;
            }
            StreamThread st = streamThread;
            if (st != null) {
                List<String> inFlight = new ArrayList<>();
                for (StreamThread.PendingCommand pc : st.pending.values()) {
                    inFlight.add(pc.command);
                }
                for (StreamThread.PendingCommand pc : st.pending.values()) {
                    if (!pc.future.isDone()) {
                        pc.future.complete(new ShellResult.Failed(ShellFailureReason.IO_ERROR));
                    }
                    if (pc.timeoutTask != null) {
                        pc.timeoutTask.cancel(false);
                    }
                }
                if (globalExecListeners != null) {
                    try {
                        globalExecListeners.brokenPipe(
                            "Incorrect shell code causing pipeline rupture. In-flight commands: " + inFlight,
                            leftoverErrors
                        );
                    } catch (Throwable e) {
                        AndroidLog.logE(TAG, "Error during brokenPipe callback.", e);
                    }
                }
            }
            close();
        }

        /**
         * 处理"进程仍存活但某条流已终止"的会话失效（如命令执行 {@code exec >/dev/null} 重定向）：
         * 在途命令不再以伪造的成功收敛（旧行为会以 {@code exitCode="-1"} 产出假结果），
         * 改为以 {@link ShellFailureReason#IO_ERROR} 失败完成，并保留流结束残留输出供
         * {@code brokenPipe} 回调诊断。
         *
         * @param leftover 流终止时残留的未归属输出行
         */
        private void onStreamClosed(@NonNull String[] leftover) {
            StreamThread st = streamThread;
            if (st != null) {
                for (StreamThread.PendingCommand pc : st.pending.values()) {
                    if (!pc.future.isDone()) {
                        pc.future.complete(new ShellResult.Failed(ShellFailureReason.IO_ERROR));
                    }
                    if (pc.timeoutTask != null) {
                        pc.timeoutTask.cancel(false);
                    }
                }
            }
            if (brokenReported.compareAndSet(false, true)) {
                if (globalExecListeners != null) {
                    try {
                        globalExecListeners.brokenPipe(
                            "Shell stream ended while process alive (e.g. exec redirect).",
                            leftover
                        );
                    } catch (Throwable e) {
                        AndroidLog.logE(TAG, "Error during brokenPipe callback.", e);
                    }
                }
            }
            // 两条流均已终止后 streamThread.isActive() 为 false，会话自然失效；无需额外重置。
        }
    }

    /**
     * Shell 标准输出和错误输出的读取线程管理类。
     * <p>
     * 通过两条独立的后台读取线程分别读取标准输出流与错误输出流，利用带随机 nonce 的结束标记行关联命令。
     * 读取线程<strong>永不阻塞等待</strong>：普通行追加到当前流缓冲，标记行将缓冲移交对应命令；
     * 命令收敛由「双流配对 / 流结束兜底 / 命令超时兜底」三条路径保证。
     */
    final class StreamThread {
        private static final String[] EMPTY = new String[0];

        @NonNull
        private final ShellImpl shellImpl;
        @NonNull
        private final InputStream input;
        @NonNull
        private final InputStream error;
        private final Thread stdoutThread;
        private final Thread stderrThread;
        private final ConcurrentHashMap<Long, PendingCommand> pending = new ConcurrentHashMap<>();

        private StreamThread(@NonNull ShellImpl shellImpl, @NonNull InputStream inputStream,
                             @NonNull InputStream errorStream) {
            this.shellImpl = shellImpl;
            input = inputStream;
            error = errorStream;
            stdoutThread = new Thread(this::readStdout, "HookTool-Shell-Stdout");
            stderrThread = new Thread(this::readStderr, "HookTool-Shell-Stderr");
            stdoutThread.setDaemon(true);
            stderrThread.setDaemon(true);
        }

        private void start() {
            stdoutThread.start();
            stderrThread.start();
        }

        private boolean isActive() {
            return stdoutThread.isAlive() && stderrThread.isAlive();
        }

        private void readStdout() {
            List<String> buffer = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(input))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (handleLine(line, false, buffer)) {
                        buffer = new ArrayList<>();
                    } else {
                        buffer.add(line);
                    }
                }
            } catch (IOException e) {
                if (!shellImpl.closing) {
                    AndroidLog.logE(TAG, "Error reading shell standard output stream.", e);
                }
            }
            onStreamEof(false, toArray(buffer));
        }

        private void readStderr() {
            List<String> buffer = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(error))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (handleLine(line, true, buffer)) {
                        buffer = new ArrayList<>();
                    } else {
                        buffer.add(line);
                    }
                }
            } catch (IOException e) {
                if (!shellImpl.closing) {
                    AndroidLog.logE(TAG, "Error reading shell standard error stream.", e);
                }
            }
            onStreamEof(true, toArray(buffer));
        }

        /**
         * 处理一行输出：解析结束标记行并移交缓冲，普通行返回 {@code false} 由调用方入缓冲。
         * <p>
         * 仅当标记的 ID、代际与随机 nonce 全部匹配当前在途命令时才算有效标记；
         * 其余（伪造/陈旧/不匹配）直接丢弃而不回退入缓冲，避免残留/伪造标记污染后续命令输出。
         *
         * @param line    当前读取的行
         * @param isError 是否来自错误输出流
         * @param buffer  当前流缓冲
         * @return 若是已处理的标记行返回 {@code true}（不应再入缓冲）
         */
        private boolean handleLine(@NonNull String line, boolean isError, @NonNull List<String> buffer) {
            Marker marker = parseMarker(line);
            if (marker == null) {
                return false;
            }
            PendingCommand pc = pending.get(marker.id);
            if (pc == null || pc.generation != shellImpl.generation || !pc.nonce.equals(marker.nonce)) {
                // 非预期命令 / 代际不符 / nonce 不匹配：不是本会话有效命令的标记，
                // 丢弃该行而非回退入缓冲，避免残留/伪造标记污染后续命令的输出。
                return true;
            }
            if (isError) {
                pc.errLines = toArray(buffer);
                pc.isErrArrived = true;
            } else {
                pc.outLines = toArray(buffer);
                pc.isOutArrived = true;
            }
            pc.exitCode = marker.exitCode;
            maybeComplete(pc);
            return true;
        }

        @Nullable
        private Marker parseMarker(@NonNull String line) {
            String prefix = "HTM_" + shellImpl.token + "_";
            if (!line.startsWith(prefix)) {
                return null;
            }
            String body = line.substring(prefix.length());
            int underscore = body.indexOf('_');
            int comma = body.indexOf(',');
            if (underscore < 0 || comma < 0 || underscore >= comma) {
                return null;
            }
            try {
                long id = Long.parseLong(body.substring(0, underscore));
                String nonce = body.substring(underscore + 1, comma);
                String exitCode = body.substring(comma + 1).trim();
                return new Marker(id, nonce, exitCode);
            } catch (NumberFormatException e) {
                AndroidLog.logD(TAG, "Ignoring unparseable shell marker line: " + line, e);
                return null;
            }
        }

        /**
         * 双流标记均到达时组装结果并完成命令（含回调分发）。
         *
         * @param pc 待完成的命令数据
         */
        private void maybeComplete(@NonNull PendingCommand pc) {
            if (pc.future.isDone()) {
                return;
            }
            if (!pc.isOutArrived || !pc.isErrArrived) {
                return;
            }
            completeCommand(pc, new CommandResult(
                pc.command,
                pc.exitCode,
                pc.outLines != null ? pc.outLines : EMPTY,
                pc.errLines != null ? pc.errLines : EMPTY
            ));
        }

        /**
         * 完成命令：先取消超时任务，再分发监听器回调，最后完成 {@link CompletableFuture} 并清理登记。
         * <p>
         * stdout/stderr 两个读取线程以及超时任务可能并发到达，通过 {@code completed} 原子标志
         * 保证命令只被完整执行一次（回调与清理均只发生一次）；取消超时任务在分发回调之前，
         * 消除"回调执行期间超时任务误报"的窗口。
         *
         * @param pc     命令数据
         * @param result 组装好的命令结果
         */
        private void completeCommand(@NonNull PendingCommand pc, @NonNull CommandResult result) {
            if (pc.completed.compareAndSet(false, true)) {
                if (pc.timeoutTask != null) {
                    pc.timeoutTask.cancel(false);
                }
                dispatchCallbacks(pc, result);
                pc.future.complete(new ShellResult.Completed(result));
                pending.remove(pc.id);
            }
        }

        /**
         * 按退出码分发 output/error 回调：先全局监听器，后命令专属监听器。
         * <p>
         * 回调在读取线程上执行；回调内请勿同步调用 {@link ShellTool#exec()}（会阻塞读取线程自身）。
         *
         * @param pc     命令数据
         * @param result 命令结果
         */
        private void dispatchCallbacks(@NonNull PendingCommand pc, @NonNull CommandResult result) {
            boolean success = "0".equals(result.exitCode());
            if (globalExecListeners != null) {
                try {
                    if (success) {
                        globalExecListeners.output(result.command(), result.exitCode(), result.outputs());
                    } else {
                        globalExecListeners.error(result.command(), result.exitCode(), result.errors());
                    }
                } catch (Throwable e) {
                    AndroidLog.logW(TAG, "Error during global exec listener callback for command: " + result.command(), e);
                }
            }
            if (pc.perCmdListener != null) {
                try {
                    if (success) {
                        pc.perCmdListener.output(result.command(), result.exitCode(), result.outputs());
                    } else {
                        pc.perCmdListener.error(result.command(), result.exitCode(), result.errors());
                    }
                } catch (Throwable e) {
                    AndroidLog.logW(TAG, "Error during per-command exec listener callback for command: " + result.command(), e);
                }
            }
        }

        /**
         * 某条流结束时触发：进程异常死亡则上报 brokenPipe 并关闭；进程存活但流已终止
         * （如命令重定向关闭流）则按会话失效处理，在途命令以失败完成、残留输出交 brokenPipe 诊断。
         *
         * @param isError  是否错误流结束
         * @param leftover 流结束时残留的未归属输出行
         */
        private void onStreamEof(boolean isError, @NonNull String[] leftover) {
            if (shellImpl.closing) {
                return;
            }
            Process process = shellImpl.process;
            if (process != null && !process.isAlive()) {
                shellImpl.onBrokenPipe(leftover);
                return;
            }
            shellImpl.onStreamClosed(leftover);
        }

        private void close() {
            Thread current = Thread.currentThread();
            interruptAndJoin(stdoutThread, current);
            interruptAndJoin(stderrThread, current);
            pending.clear();
        }

        private void interruptAndJoin(@NonNull Thread thread, @NonNull Thread current) {
            if (thread == current) {
                return;
            }
            thread.interrupt();
            try {
                thread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @NonNull
        private String[] toArray(@NonNull List<String> list) {
            return list.toArray(new String[0]);
        }

        /**
         * 命令结束标记的解析结果。
         */
        private static final class Marker {
            final long id;
            @NonNull
            final String nonce;
            final String exitCode;

            Marker(long id, @NonNull String nonce, @NonNull String exitCode) {
                this.id = id;
                this.nonce = nonce;
                this.exitCode = exitCode;
            }
        }

        /**
         * 单条命令的待处理数据：持有独立的 {@link CompletableFuture}、随机 nonce 与收集缓冲。
         */
        static final class PendingCommand {
            final long id;
            final int generation;
            @NonNull
            final String command;
            @NonNull
            final CompletableFuture<ShellResult> future;
            @Nullable
            final IExecListener perCmdListener;
            /**
             * 本命令结束标记前缀中携带的随机 nonce，用于校验标记真伪。
             */
            @NonNull
            final String nonce;
            /**
             * 命令是否已被完整执行一次（回调 + 完成 + 清理），用于双读取线程与超时任务并发防护。
             */
            final AtomicBoolean completed = new AtomicBoolean();
            volatile boolean isOutArrived;
            volatile boolean isErrArrived;
            volatile String[] outLines;
            volatile String[] errLines;
            volatile String exitCode = "-1";
            volatile ScheduledFuture<?> timeoutTask;

            PendingCommand(long id, int generation, @NonNull String command,
                           @Nullable IExecListener perCmdListener, @NonNull String nonce) {
                this.id = id;
                this.generation = generation;
                this.command = command;
                this.future = new CompletableFuture<>();
                this.perCmdListener = perCmdListener;
                this.nonce = nonce;
            }
        }
    }
}
