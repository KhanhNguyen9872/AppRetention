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
        public final long memoryBytes;

        public ProcessInfo(int pid, String processName, int adj, long memoryBytes) {
            this.pid = pid;
            this.processName = processName;
            this.adj = adj;
            this.memoryBytes = memoryBytes;
        }
    }

    public static List<ProcessInfo> getRunningProcesses() {
        List<ProcessInfo> list = new ArrayList<>();
        if (!isRootAvailable()) return list;

        Process p = null;
        try {
            String script = "if ps -A -o PID,RSS,ARGS >/dev/null 2>&1; then " +
                    "ps -A -o PID,RSS,ARGS | while read -r pid rss cmd; do " +
                    "[ \"$pid\" -gt 0 ] 2>/dev/null || continue; " +
                    "[ -f \"/proc/$pid/oom_score_adj\" ] || continue; " +
                    "read -r adj < \"/proc/$pid/oom_score_adj\" || continue; " +
                    "if [ \"$rss\" = \"0\" ] || [ -z \"$rss\" ]; then " +
                    "if [ -f \"/proc/$pid/statm\" ]; then " +
                    "read -r _tot _res _rest < \"/proc/$pid/statm\"; " +
                    "[ \"$_res\" -gt 0 ] 2>/dev/null && rss=$((_res * 4)); " +
                    "fi; " +
                    "fi; " +
                    "echo \"$pid:$cmd:$adj:$rss\"; " +
                    "done; " +
                    "else " +
                    "for d in /proc/[0-9]*; do " +
                    "[ -f \"$d/oom_score_adj\" ] || continue; " +
                    "read -r adj < \"$d/oom_score_adj\" || continue; " +
                    "c=$(cat \"$d/cmdline\" 2>/dev/null | tr '\\0' ' '); " +
                    "[ -n \"$c\" ] || continue; " +
                    "rss=0; " +
                    "if [ -f \"$d/statm\" ]; then " +
                    "read -r _tot _res _rest < \"$d/statm\"; " +
                    "[ \"$_res\" -gt 0 ] 2>/dev/null && rss=$((_res * 4)); " +
                    "fi; " +
                    "echo \"${d##*/}:$c:$adj:$rss\"; " +
                    "done; " +
                    "fi";
            p = Runtime.getRuntime().exec(new String[]{"su", "-c", script});
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(":", 4);
                    if (parts.length >= 3) {
                        try {
                            int pid = Integer.parseInt(parts[0].trim());
                            String name = parts[1].trim();
                            int adj = Integer.parseInt(parts[2].trim());
                            long memBytes = 0;
                            if (parts.length >= 4) {
                                try {
                                    String rssStr = parts[3].trim().toLowerCase();
                                    if (rssStr.endsWith("g")) {
                                        double gb = Double.parseDouble(rssStr.replace("g", "").trim());
                                        memBytes = (long) (gb * 1024L * 1024L * 1024L);
                                    } else if (rssStr.endsWith("m")) {
                                        double mb = Double.parseDouble(rssStr.replace("m", "").trim());
                                        memBytes = (long) (mb * 1024L * 1024L);
                                    } else if (rssStr.endsWith("k")) {
                                        double kb = Double.parseDouble(rssStr.replace("k", "").trim());
                                        memBytes = (long) (kb * 1024L);
                                    } else {
                                        long kb = Long.parseLong(rssStr);
                                        memBytes = kb * 1024L;
                                    }
                                } catch (Throwable ignored) {}
                            }

                            if (!name.isEmpty() && !name.startsWith("/") && !name.startsWith("[")) {
                                if (name.contains(" ")) {
                                    name = name.substring(0, name.indexOf(' '));
                                }
                                int nullIdx = name.indexOf('\0');
                                if (nullIdx >= 0) {
                                    name = name.substring(0, nullIdx);
                                }
                                String cleanName = name.trim();
                                if (!cleanName.isEmpty()) {
                                    list.add(new ProcessInfo(pid, cleanName, adj, memBytes));
                                }
                            }
                        } catch (Throwable ignored) {}
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
        return list;
    }

    public static void cleanLegacyTraces() {
        sAsyncExecutor.execute(() -> {
            if (isRootAvailable()) {
                Process p = null;
                try {
                    p = Runtime.getRuntime().exec(new String[]{"su", "-c", "rm -rf /data/system/AppRetention 2>/dev/null"});
                    p.waitFor();
                } catch (Throwable ignored) {
                } finally {
                    if (p != null) {
                        try {
                            p.destroy();
                        } catch (Throwable ignored) {}
                    }
                }
            }
        });
    }

    public static void killProcess(int pid, String packageName) {
        sAsyncExecutor.execute(() -> {
            if (isRootAvailable()) {
                Process p = null;
                try {
                    String cmd = "";
                    if (pid > 0) {
                        cmd += "kill -9 " + pid + " 2>/dev/null; ";
                    }
                    if (packageName != null && !packageName.trim().isEmpty()) {
                        String cleanPkg = packageName.trim();
                        if (cleanPkg.contains(":")) {
                            cleanPkg = cleanPkg.substring(0, cleanPkg.indexOf(':'));
                        }
                        cmd += "am force-stop " + cleanPkg + " 2>/dev/null; ";
                    }
                    if (!cmd.isEmpty()) {
                        p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
                        p.waitFor();
                    }
                } catch (Throwable ignored) {
                } finally {
                    if (p != null) {
                        try {
                            p.destroy();
                        } catch (Throwable ignored) {}
                    }
                }
            }
        });
    }
}
