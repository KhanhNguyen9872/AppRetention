package com.hchen.appretention.ui;

import com.hchen.hooktool.utils.SystemPropTool;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RootTool {
    private static volatile Boolean sHasRoot = null;
    private static final ExecutorService sAsyncExecutor = Executors.newSingleThreadExecutor();

    private RootTool() {}

    public static boolean isRootAvailable() {
        if (sHasRoot != null) return sHasRoot;
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = reader.readLine();
                sHasRoot = (line != null && line.contains("uid=0"));
            }
            p.waitFor();
        } catch (Throwable e) {
            sHasRoot = false;
        }
        return Boolean.TRUE.equals(sHasRoot);
    }

    public static void setProp(String key, String value) {
        sAsyncExecutor.execute(() -> {
            try {
                SystemPropTool.setProp(key, value);
            } catch (Throwable ignored) {}

            try {
                Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "setprop " + key + " \"" + value + "\""});
                p.waitFor();
            } catch (Throwable ignored) {}
        });
    }

    public static String readCpuStatLine() {
        if (!isRootAvailable()) return null;
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{"su", "-c", "head -n 1 /proc/stat 2>/dev/null || cat /proc/stat"});
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("cpu ")) {
                        return line;
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (p != null) {
                try {
                    p.destroy();
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    public static void resetRootCheck() {
        sHasRoot = null;
    }

    public static boolean requestRoot() {
        sHasRoot = null;
        return isRootAvailable();
    }

    public static class HardwareStats {
        public String cpuLine;
        public long memTotalBytes;
        public long memAvailableBytes;
        public long storageTotalBytes;
        public long storageUsedBytes;
        public long storageAvailableBytes;
    }

    public static HardwareStats getHardwareStats() {
        if (!isRootAvailable()) return null;
        Process p = null;
        try {
            String script = "head -n 1 /proc/stat 2>/dev/null || cat /proc/stat; echo '---MEM---'; head -n 5 /proc/meminfo 2>/dev/null || cat /proc/meminfo; echo '---DF---'; df -k /data";
            p = Runtime.getRuntime().exec(new String[]{"su", "-c", script});
            HardwareStats stats = new HardwareStats();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                int section = 0;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    if (line.equals("---MEM---")) {
                        section = 1;
                        continue;
                    } else if (line.equals("---DF---")) {
                        section = 2;
                        continue;
                    }

                    if (section == 0) {
                        if (line.startsWith("cpu ")) {
                            stats.cpuLine = line;
                        }
                    } else if (section == 1) {
                        String[] parts = line.split(":");
                        if (parts.length == 2) {
                            String key = parts[0].trim();
                            String valStr = parts[1].replace("kB", "").trim();
                            try {
                                long val = Long.parseLong(valStr);
                                if ("MemTotal".equalsIgnoreCase(key)) {
                                    stats.memTotalBytes = val * 1024L;
                                } else if ("MemAvailable".equalsIgnoreCase(key)) {
                                    stats.memAvailableBytes = val * 1024L;
                                }
                            } catch (Throwable ignored) {}
                        }
                    } else if (section == 2) {
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 6 && !parts[0].startsWith("Filesystem")) {
                            try {
                                stats.storageTotalBytes = Long.parseLong(parts[1]) * 1024L;
                                stats.storageUsedBytes = Long.parseLong(parts[2]) * 1024L;
                                stats.storageAvailableBytes = Long.parseLong(parts[3]) * 1024L;
                            } catch (Throwable ignored) {}
                        } else if (parts.length >= 4 && parts[0].matches("\\d+")) {
                            try {
                                stats.storageTotalBytes = Long.parseLong(parts[0]) * 1024L;
                                stats.storageUsedBytes = Long.parseLong(parts[1]) * 1024L;
                                stats.storageAvailableBytes = Long.parseLong(parts[2]) * 1024L;
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
            return stats;
        } catch (Throwable ignored) {
        } finally {
            if (p != null) {
                try {
                    p.destroy();
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    public static class ProcessInfo {
        public final int pid;
        public final String processName;
        public final int adj;

        public ProcessInfo(int pid, String processName, int adj) {
            this.pid = pid;
            this.processName = processName;
            this.adj = adj;
        }
    }

    public static List<ProcessInfo> getRunningProcesses() {
        List<ProcessInfo> list = new ArrayList<>();
        if (!isRootAvailable()) return list;

        try {
            // Highly optimized single-pass scanner using shell built-in read
            String script = "for d in /proc/[0-9]*; do [ -r \"$d/oom_score_adj\" ] && [ -r \"$d/cmdline\" ] || continue; read -r c < \"$d/cmdline\" || continue; read -r a < \"$d/oom_score_adj\" || continue; [ -n \"$c\" ] && echo \"${d##*/}:$c:$a\"; done";
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", script});
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(":", 3);
                    if (parts.length == 3) {
                        try {
                            int pid = Integer.parseInt(parts[0].trim());
                            String name = parts[1].trim();
                            int adj = Integer.parseInt(parts[2].trim());
                            if (!name.isEmpty() && !name.startsWith("/") && !name.startsWith("[")) {
                                list.add(new ProcessInfo(pid, name, adj));
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }
            p.waitFor();
        } catch (Throwable ignored) {}
        return list;
    }
}
