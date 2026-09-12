package com.hchen.appretention.ui;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class HardwareInfo {

    private static volatile String sSocName = null;
    private static volatile String sStorageType = null;
    private static volatile String sRamType = null;
    private static volatile String sGpuModel = null;
    private static volatile String sCpuMaxClock = null;
    private static volatile String sDisplayInfo = null;
    private static volatile HardwareDetails sHardwareDetails = null;
    private static volatile Long sPhysicalStorageBytes = null;
    private static volatile boolean sInitialized = false;

    private static final Map<String, String> SOC_LUT = new HashMap<>();
    private static final Map<String, String> GPU_LUT = new HashMap<>();

    static {
        // Snapdragon Flagship
        SOC_LUT.put("sm8750", "Snapdragon 8 Elite");
        SOC_LUT.put("sun", "Snapdragon 8 Elite");
        SOC_LUT.put("sm8650", "Snapdragon 8 Gen 3");
        SOC_LUT.put("pineapple", "Snapdragon 8 Gen 3");
        SOC_LUT.put("sm8635", "Snapdragon 8s Gen 3");
        SOC_LUT.put("cliffs", "Snapdragon 8s Gen 3");
        SOC_LUT.put("sm8550", "Snapdragon 8 Gen 2");
        SOC_LUT.put("kalama", "Snapdragon 8 Gen 2");
        SOC_LUT.put("sm8475", "Snapdragon 8+ Gen 1");
        SOC_LUT.put("cape", "Snapdragon 8+ Gen 1");
        SOC_LUT.put("sm8450", "Snapdragon 8 Gen 1");
        SOC_LUT.put("taro", "Snapdragon 8 Gen 1");
        SOC_LUT.put("sm8350", "Snapdragon 888");
        SOC_LUT.put("lahaina", "Snapdragon 888");
        SOC_LUT.put("sm8250", "Snapdragon 865");
        SOC_LUT.put("kona", "Snapdragon 865");
        SOC_LUT.put("sm8150", "Snapdragon 855");
        SOC_LUT.put("msmnile", "Snapdragon 855");

        // Snapdragon Midrange
        SOC_LUT.put("sm7550", "Snapdragon 7 Gen 3");
        SOC_LUT.put("lanai", "Snapdragon 7 Gen 3");
        SOC_LUT.put("sm7475", "Snapdragon 7+ Gen 2");
        SOC_LUT.put("marble", "Snapdragon 7+ Gen 2");
        SOC_LUT.put("sm7450", "Snapdragon 7 Gen 1");
        SOC_LUT.put("crow", "Snapdragon 7 Gen 1");
        SOC_LUT.put("sm7325", "Snapdragon 778G");
        SOC_LUT.put("yupik", "Snapdragon 778G");
        SOC_LUT.put("sm7250", "Snapdragon 765G");
        SOC_LUT.put("lito", "Snapdragon 765G");
        SOC_LUT.put("sm6375", "Snapdragon 695");
        SOC_LUT.put("holi", "Snapdragon 695");
        SOC_LUT.put("sm4450", "Snapdragon 4 Gen 2");
        SOC_LUT.put("clarence", "Snapdragon 4 Gen 2");

        // MediaTek Dimensity
        SOC_LUT.put("mt6991", "Dimensity 9400");
        SOC_LUT.put("mt6989", "Dimensity 9300");
        SOC_LUT.put("mt6985", "Dimensity 9200");
        SOC_LUT.put("mt6983", "Dimensity 9000");
        SOC_LUT.put("mt6897", "Dimensity 8300");
        SOC_LUT.put("mt6895", "Dimensity 8100");
        SOC_LUT.put("mt6893", "Dimensity 1200");
        SOC_LUT.put("mt6877", "Dimensity 900");
        SOC_LUT.put("mt6833", "Dimensity 700");

        // Google Tensor
        SOC_LUT.put("zumapro", "Google Tensor G4");
        SOC_LUT.put("zuma", "Google Tensor G3");
        SOC_LUT.put("cloudripper", "Google Tensor G2");
        SOC_LUT.put("whitechapel", "Google Tensor G1");

        // Samsung Exynos
        SOC_LUT.put("exynos2400", "Exynos 2400");
        SOC_LUT.put("exynos2200", "Exynos 2200");
        SOC_LUT.put("exynos2100", "Exynos 2100");
        SOC_LUT.put("exynos990", "Exynos 990");

        // Additional Qualcomm generations and aliases
        SOC_LUT.put("sm8735", "Snapdragon 8s Gen 4");
        SOC_LUT.put("sm7675", "Snapdragon 7+ Gen 3");
        SOC_LUT.put("sm7635", "Snapdragon 7s Gen 4");
        SOC_LUT.put("sm7435", "Snapdragon 7s Gen 3");
        SOC_LUT.put("sm6475", "Snapdragon 6 Gen 3");
        SOC_LUT.put("sm6450", "Snapdragon 6 Gen 1");
        SOC_LUT.put("sm4635", "Snapdragon 4s Gen 2");
        SOC_LUT.put("sm4350", "Snapdragon 480");
        SOC_LUT.put("sm6225", "Snapdragon 680");
        SOC_LUT.put("sm6115", "Snapdragon 662");
        SOC_LUT.put("sdm845", "Snapdragon 845");
        SOC_LUT.put("sdm710", "Snapdragon 710");
        SOC_LUT.put("sdm660", "Snapdragon 660");
        SOC_LUT.put("msm8998", "Snapdragon 835");

        // Additional MediaTek, Tensor, Exynos, Kirin and Unisoc families
        SOC_LUT.put("mt6993", "Dimensity 9500");
        SOC_LUT.put("mt6899", "Dimensity 8400");
        SOC_LUT.put("mt6896", "Dimensity 8200");
        SOC_LUT.put("mt6878", "Dimensity 7300");
        SOC_LUT.put("mt6835", "Dimensity 6100/6300 family");
        SOC_LUT.put("mt6789", "Helio G99");
        SOC_LUT.put("mt6781", "Helio G96");
        SOC_LUT.put("mt6785", "Helio G90/G95 family");
        SOC_LUT.put("gs101", "Google Tensor G1");
        SOC_LUT.put("gs201", "Google Tensor G2");
        SOC_LUT.put("exynos2500", "Exynos 2500");
        SOC_LUT.put("exynos1080", "Exynos 1080");
        SOC_LUT.put("exynos9820", "Exynos 9820");
        SOC_LUT.put("exynos9810", "Exynos 9810");
        SOC_LUT.put("kirin9020", "Kirin 9020");
        SOC_LUT.put("kirin9000s", "Kirin 9000S");
        SOC_LUT.put("kirin9000", "Kirin 9000");
        SOC_LUT.put("kirin990", "Kirin 990");
        SOC_LUT.put("kirin980", "Kirin 980");
        SOC_LUT.put("ums9620", "Unisoc T760 family");
        SOC_LUT.put("ums9230", "Unisoc T606/T612 family");
        SOC_LUT.put("ums512", "Unisoc T618 family");
        SOC_LUT.put("sp9863a", "Unisoc SC9863A");

        // GPU mapping
        GPU_LUT.put("sm8750", "Adreno 830");
        GPU_LUT.put("sun", "Adreno 830");
        GPU_LUT.put("sm8650", "Adreno 750");
        GPU_LUT.put("pineapple", "Adreno 750");
        GPU_LUT.put("sm8635", "Adreno 735");
        GPU_LUT.put("cliffs", "Adreno 735");
        GPU_LUT.put("sm8550", "Adreno 740");
        GPU_LUT.put("kalama", "Adreno 740");
        GPU_LUT.put("sm8475", "Adreno 730");
        GPU_LUT.put("cape", "Adreno 730");
        GPU_LUT.put("sm8450", "Adreno 730");
        GPU_LUT.put("taro", "Adreno 730");
        GPU_LUT.put("sm8350", "Adreno 660");
        GPU_LUT.put("lahaina", "Adreno 660");
        GPU_LUT.put("sm8250", "Adreno 650");
        GPU_LUT.put("kona", "Adreno 650");
        GPU_LUT.put("sm8150", "Adreno 640");
        GPU_LUT.put("msmnile", "Adreno 640");
        GPU_LUT.put("sm7550", "Adreno 720");
        GPU_LUT.put("sm7475", "Adreno 725");
        GPU_LUT.put("sm7450", "Adreno 644");
        GPU_LUT.put("sm7325", "Adreno 642L");
        GPU_LUT.put("sm7250", "Adreno 620");
        GPU_LUT.put("sm6375", "Adreno 619");
        GPU_LUT.put("mt6991", "Immortalis-G925");
        GPU_LUT.put("mt6989", "Immortalis-G720");
        GPU_LUT.put("mt6985", "Immortalis-G715");
        GPU_LUT.put("mt6983", "Mali-G710");
        GPU_LUT.put("mt6895", "Mali-G610");
        GPU_LUT.put("zuma", "Mali-G715");
        GPU_LUT.put("zumapro", "Mali-G715");
        GPU_LUT.put("sm8735", "Adreno 825");
        GPU_LUT.put("sm7675", "Adreno 732");
        GPU_LUT.put("sm7635", "Adreno 710");
        GPU_LUT.put("sm7435", "Adreno 710");
        GPU_LUT.put("sm6475", "Adreno 710");
        GPU_LUT.put("sm6450", "Adreno 710");
        GPU_LUT.put("sm4635", "Adreno 611");
        GPU_LUT.put("sm4450", "Adreno 613");
        GPU_LUT.put("sm4350", "Adreno 619");
        GPU_LUT.put("sm6225", "Adreno 610");
        GPU_LUT.put("sm6115", "Adreno 610");
        GPU_LUT.put("sdm845", "Adreno 630");
        GPU_LUT.put("sdm710", "Adreno 616");
        GPU_LUT.put("sdm660", "Adreno 512");
        GPU_LUT.put("msm8998", "Adreno 540");
        GPU_LUT.put("mt6993", "Arm Immortalis-G1 Ultra");
        GPU_LUT.put("mt6899", "Mali-G720");
        GPU_LUT.put("mt6897", "Mali-G615");
        GPU_LUT.put("mt6896", "Mali-G610");
        GPU_LUT.put("mt6878", "Mali-G615");
        GPU_LUT.put("mt6835", "Mali-G57");
        GPU_LUT.put("mt6789", "Mali-G57 MC2");
        GPU_LUT.put("mt6781", "Mali-G57 MC2");
        GPU_LUT.put("gs101", "Mali-G78");
        GPU_LUT.put("gs201", "Mali-G710");
        GPU_LUT.put("cloudripper", "Mali-G710");
        GPU_LUT.put("whitechapel", "Mali-G78");
        GPU_LUT.put("exynos2500", "Xclipse 950");
        GPU_LUT.put("exynos2400", "Xclipse 940");
        GPU_LUT.put("exynos2200", "Xclipse 920");
        GPU_LUT.put("exynos2100", "Mali-G78");
        GPU_LUT.put("kirin9000", "Mali-G78");
        GPU_LUT.put("kirin990", "Mali-G76");
        GPU_LUT.put("kirin980", "Mali-G76");
        GPU_LUT.put("ums9620", "Mali-G57");
        GPU_LUT.put("ums9230", "Mali-G57");
        GPU_LUT.put("ums512", "Mali-G52");
        GPU_LUT.put("sp9863a", "PowerVR GE8322");
    }

    public static synchronized void init(Context context) {
        if (sInitialized) return;
        detectSoc();
        detectStorage();
        detectRam();
        detectGpu();
        detectCpuMaxClock();
        detectHardwareDetails();
        if (context != null) {
            detectDisplay(context);
        }
        sInitialized = true;
    }

    public static String getSocName() {
        if (sSocName == null) detectSoc();
        return sSocName != null ? sSocName : "ARM Processor";
    }

    public static String getStorageType() {
        if (sStorageType == null) detectStorage();
        return sStorageType != null ? sStorageType : "";
    }

    public static long getPhysicalStorageBytes() {
        Long cached = sPhysicalStorageBytes;
        if (cached != null) return cached;

        long total = 0L;
        String type = getStorageType().toUpperCase();
        File blockRoot = new File("/sys/class/block");
        File[] devices = blockRoot.listFiles();
        if (devices != null) {
            for (File device : devices) {
                String name = device.getName();
                boolean candidate;
                if (type.contains("UFS")) {
                    candidate = name.matches("sd[a-z]");
                } else if (type.contains("EMMC")) {
                    candidate = "mmcblk0".equals(name);
                } else if (type.contains("NVME")) {
                    candidate = "nvme0n1".equals(name);
                } else {
                    candidate = "mmcblk0".equals(name) || "nvme0n1".equals(name)
                        || name.matches("sd[a-z]");
                }
                if (!candidate || new File(device, "partition").exists()) continue;
                long sectors = readLongFromFile(new File(device, "size").getPath());
                if (sectors > 0 && sectors <= Long.MAX_VALUE / 512L) {
                    total += sectors * 512L;
                }
            }
        }
        sPhysicalStorageBytes = total;
        return total;
    }

    public static String getRamType() {
        if (sRamType == null) detectRam();
        return sRamType != null ? sRamType : "";
    }

    public static class GpuStats {
        public final int loadPercent;
        public final int clockMhz;

        public GpuStats(int loadPercent, int clockMhz) {
            this.loadPercent = loadPercent;
            this.clockMhz = clockMhz;
        }
    }

    private static volatile String sWorkingGpuBusyPath = null;
    private static volatile String sWorkingGpuFreqPath = null;

    public static GpuStats getGpuStats() {
        int load = -1;
        int clockMhz = 0;

        // 1. GPU Busy / Load percentage
        String[] busyPaths = sWorkingGpuBusyPath != null ?
            new String[]{sWorkingGpuBusyPath} :
            new String[]{
                "/sys/class/kgsl/kgsl-3d0/gpubusy",
                "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
                "/sys/module/ged/parameters/gpu_loading",
                "/sys/kernel/gpu/gpu_busy",
                "/sys/class/misc/mali0/device/utilization",
                "/sys/devices/platform/13040000.mali/utilization",
                "/sys/devices/platform/17000000.mali/utilization",
                "/sys/devices/platform/soc/3d00000.qcom,kgsl-3d0/kgsl/kgsl-3d0/gpubusy",
                "/sys/devices/platform/soc/3d00000.qcom,kgsl-3d0/kgsl/kgsl-3d0/gpu_busy_percentage"
            };

        for (String p : busyPaths) {
            String line = readFileFirstLine(p);
            if (line != null && !line.trim().isEmpty()) {
                String clean = line.trim();
                String[] parts = clean.split("\\s+");
                if (parts.length >= 2) {
                    try {
                        long busy = Long.parseLong(parts[0].trim());
                        long total = Long.parseLong(parts[1].trim());
                        if (total > 0) {
                            load = (int) Math.min(100, Math.max(0, (busy * 100) / total));
                            sWorkingGpuBusyPath = p;
                            break;
                        }
                    } catch (Throwable ignored) {}
                } else if (parts.length == 1) {
                    try {
                        String valStr = parts[0].replace("%", "").trim();
                        int val = Integer.parseInt(valStr);
                        if (val >= 0 && val <= 100) {
                            load = val;
                            sWorkingGpuBusyPath = p;
                            break;
                        }
                    } catch (Throwable ignored) {}
                }
            }
        }

        // 2. GPU Clock frequency
        String[] freqPaths = sWorkingGpuFreqPath != null ?
            new String[]{sWorkingGpuFreqPath} :
            new String[]{
                "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
                "/sys/class/kgsl/kgsl-3d0/gpuclk",
                "/sys/module/ged/parameters/gpu_cur_freq",
                "/sys/class/misc/mali0/device/cur_freq",
                "/sys/devices/platform/soc/3d00000.qcom,kgsl-3d0/kgsl/kgsl-3d0/devfreq/3d00000.qcom,kgsl-3d0/cur_freq"
            };

        for (String p : freqPaths) {
            String line = readFileFirstLine(p);
            if (line != null && !line.trim().isEmpty()) {
                try {
                    long freq = Long.parseLong(line.trim());
                    if (freq > 100000000L) {
                        clockMhz = (int) (freq / 1000000L);
                        sWorkingGpuFreqPath = p;
                        break;
                    } else if (freq > 100000L) {
                        clockMhz = (int) (freq / 1000L);
                        sWorkingGpuFreqPath = p;
                        break;
                    } else if (freq > 100) {
                        clockMhz = (int) freq;
                        sWorkingGpuFreqPath = p;
                        break;
                    }
                } catch (Throwable ignored) {}
            }
        }

        // Batch all privileged fallbacks into one bounded root process instead of
        // spawning one `su` command per unavailable sysfs path every refresh.
        if (RootTool.hasRoot() && (load < 0 || clockMhz <= 0)) {
            String script = "for f in " + String.join(" ", busyPaths)
                + "; do if [ -r \"$f\" ]; then echo \"B:$f:$(cat \"$f\" 2>/dev/null)\"; break; fi; done; "
                + "for f in " + String.join(" ", freqPaths)
                + "; do if [ -r \"$f\" ]; then echo \"F:$f:$(cat \"$f\" 2>/dev/null)\"; break; fi; done";
            String output = RootTool.runCommand(script);
            for (String row : output.split("\\R")) {
                String[] fields = row.trim().split(":", 3);
                if (fields.length != 3 || fields[2].trim().isEmpty()) continue;
                if ("B".equals(fields[0]) && load < 0) {
                    String[] values = fields[2].trim().split("\\s+");
                    try {
                        if (values.length >= 2) {
                            long busy = Long.parseLong(values[0]);
                            long total = Long.parseLong(values[1]);
                            if (total > 0) load = (int) Math.min(100, Math.max(0, busy * 100 / total));
                        } else {
                            int value = Integer.parseInt(values[0].replace("%", ""));
                            if (value >= 0 && value <= 100) load = value;
                        }
                        if (load >= 0) sWorkingGpuBusyPath = fields[1];
                    } catch (Throwable ignored) {}
                } else if ("F".equals(fields[0]) && clockMhz <= 0) {
                    try {
                        long frequency = Long.parseLong(fields[2].trim());
                        if (frequency > 100000000L) clockMhz = (int) (frequency / 1000000L);
                        else if (frequency > 100000L) clockMhz = (int) (frequency / 1000L);
                        else if (frequency > 100L) clockMhz = (int) frequency;
                        if (clockMhz > 0) sWorkingGpuFreqPath = fields[1];
                    } catch (Throwable ignored) {}
                }
            }
        }

        return new GpuStats(load, clockMhz);
    }

    public static String getGpuModel() {
        if (sGpuModel == null) detectGpu();
        return sGpuModel != null ? sGpuModel : "Adreno GPU";
    }

    public static String getCpuMaxClock() {
        if (sCpuMaxClock == null) detectCpuMaxClock();
        return sCpuMaxClock != null ? sCpuMaxClock : "";
    }

    /** Immutable identity data. Unlike load/temperature, this is probed once and cached. */
    public static final class HardwareDetails {
        public final String cpuModel;
        public final String cpuAbi;
        public final String cpuTopology;
        public final String gpuVendor;
        public final String gpuDriver;
        public final String deviceName;
        public final String platform;

        private HardwareDetails(String cpuModel, String cpuAbi, String cpuTopology,
                                String gpuVendor, String gpuDriver, String deviceName,
                                String platform) {
            this.cpuModel = cpuModel;
            this.cpuAbi = cpuAbi;
            this.cpuTopology = cpuTopology;
            this.gpuVendor = gpuVendor;
            this.gpuDriver = gpuDriver;
            this.deviceName = deviceName;
            this.platform = platform;
        }
    }

    public static HardwareDetails getHardwareDetails() {
        HardwareDetails cached = sHardwareDetails;
        if (cached == null) {
            synchronized (HardwareInfo.class) {
                if (sHardwareDetails == null) detectHardwareDetails();
                cached = sHardwareDetails;
            }
        }
        return cached;
    }

    public static String getDisplayInfo(Context context) {
        if (sDisplayInfo == null && context != null) detectDisplay(context);
        return sDisplayInfo != null ? sDisplayInfo : "";
    }

    public static String getCpuTemp() {
        try {
            File thermalDir = new File("/sys/class/thermal");
            if (thermalDir.exists() && thermalDir.isDirectory()) {
                File[] zones = thermalDir.listFiles((dir, name) -> name.startsWith("thermal_zone"));
                if (zones != null) {
                    for (File zone : zones) {
                        String type = readFileFirstLine(new File(zone, "type").getPath());
                        if (type != null) {
                            String t = type.toLowerCase();
                            if (t.contains("cpu") || t.contains("soc") || t.contains("tsens_tz_sensor") || t.contains("mtktscpu")) {
                                String tempStr = readFileFirstLine(new File(zone, "temp").getPath());
                                if (tempStr != null) {
                                    try {
                                        float val = Float.parseFloat(tempStr.trim());
                                        if (val > 1000) val /= 1000.0f;
                                        if (val >= 20.0f && val <= 105.0f) {
                                            return String.format("%.1f°C", val);
                                        }
                                    } catch (Throwable ignored) {}
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return "";
    }

    public static class BatteryInfo {
        public int levelPercent;
        public float temperatureC;
        public int status;
        public int health;
        public String technology;
        public boolean isCharging;
        public int currentCapacityMah;
        public int totalCapacityMah;
        public int currentAmperageMa;
    }

    private static int sCachedTotalCapacityMah = 0;

    private static int detectTotalCapacityMah(Context context) {
        if (sCachedTotalCapacityMah > 0) return sCachedTotalCapacityMah;

        // 1. Android internal PowerProfile (100% reliable standard AOSP API)
        try {
            Class<?> powerProfileClass = Class.forName("com.android.internal.os.PowerProfile");
            Object powerProfile = powerProfileClass.getConstructor(Context.class).newInstance(context);
            double cap = (Double) powerProfileClass.getMethod("getBatteryCapacity").invoke(powerProfile);
            if (cap > 500) {
                sCachedTotalCapacityMah = (int) Math.round(cap);
                return sCachedTotalCapacityMah;
            }
        } catch (Throwable ignored) {}

        // 2. Read from sysfs power_supply
        String[] paths = new String[]{
            "/sys/class/power_supply/battery/charge_full_design",
            "/sys/class/power_supply/bms/charge_full_design",
            "/sys/class/power_supply/battery/charge_full",
            "/sys/class/power_supply/bms/charge_full"
        };
        for (String p : paths) {
            long val = readLongFromFile(p);
            if (val > 0) {
                if (val > 100000) val /= 1000;
                if (val >= 1000 && val <= 30000) {
                    sCachedTotalCapacityMah = (int) val;
                    return sCachedTotalCapacityMah;
                }
            }
        }

        sCachedTotalCapacityMah = 5000;
        return sCachedTotalCapacityMah;
    }

    private static int detectCurrentCapacityMah(BatteryManager bm, int totalCapacity, int levelPercent) {
        if (bm != null) {
            try {
                int chargeCounter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                if (chargeCounter > 0) {
                    if (chargeCounter > 100000) {
                        return chargeCounter / 1000;
                    } else if (chargeCounter > 500) {
                        return chargeCounter;
                    }
                }
            } catch (Throwable ignored) {}
        }

        String[] paths = new String[]{
            "/sys/class/power_supply/battery/charge_now",
            "/sys/class/power_supply/bms/charge_now"
        };
        for (String p : paths) {
            long val = readLongFromFile(p);
            if (val > 0) {
                if (val > 100000) val /= 1000;
                if (val >= 100 && val <= 30000) return (int) val;
            }
        }

        if (totalCapacity > 0 && levelPercent >= 0) {
            return Math.round(totalCapacity * (levelPercent / 100.0f));
        }
        return 0;
    }

    private static int detectCurrentAmperageMa(BatteryManager bm, boolean isCharging) {
        if (bm != null) {
            try {
                int cur = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                if (cur != Integer.MIN_VALUE && cur != 0) {
                    if (Math.abs(cur) > 10000) cur /= 1000;
                    if (Math.abs(cur) <= 25000) {
                        int absVal = Math.abs(cur);
                        return isCharging ? absVal : -absVal;
                    }
                }
            } catch (Throwable ignored) {}
        }

        String[] paths = new String[]{
            "/sys/class/power_supply/battery/current_now",
            "/sys/class/power_supply/bms/current_now"
        };
        for (String p : paths) {
            long val = readLongFromFile(p);
            if (val != 0) {
                if (Math.abs(val) > 10000) val /= 1000;
                if (Math.abs(val) <= 25000) {
                    int absVal = (int) Math.abs(val);
                    return isCharging ? absVal : -absVal;
                }
            }
        }
        return 0;
    }

    private static long readLongFromFile(String path) {
        File f = new File(path);
        if (!f.exists() || !f.canRead()) return 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            String line = reader.readLine();
            if (line != null) return Long.parseLong(line.trim());
        } catch (Throwable ignored) {}
        return 0;
    }

    public static BatteryInfo getBatteryInfo(Context context) {
        if (context == null) return null;
        try {
            Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batteryIntent != null) {
                BatteryInfo info = new BatteryInfo();
                int rawLevel = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                info.levelPercent = scale > 0 ? (int) ((rawLevel / (float) scale) * 100) : rawLevel;

                int tempTenths = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                info.temperatureC = tempTenths > 0 ? tempTenths / 10.0f : 0f;

                info.status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
                info.isCharging = info.status == BatteryManager.BATTERY_STATUS_CHARGING || info.status == BatteryManager.BATTERY_STATUS_FULL;
                info.health = batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN);
                info.technology = batteryIntent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
                if (info.technology == null || info.technology.trim().isEmpty()) {
                    info.technology = "Li-poly";
                }

                BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
                info.totalCapacityMah = detectTotalCapacityMah(context);
                info.currentCapacityMah = detectCurrentCapacityMah(bm, info.totalCapacityMah, info.levelPercent);
                info.currentAmperageMa = detectCurrentAmperageMa(bm, info.isCharging);

                return info;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void detectDisplay(Context context) {
        try {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                Display display = wm.getDefaultDisplay();
                DisplayMetrics dm = new DisplayMetrics();
                display.getRealMetrics(dm);
                int w = Math.min(dm.widthPixels, dm.heightPixels);
                int h = Math.max(dm.widthPixels, dm.heightPixels);
                int hz = Math.round(display.getRefreshRate());
                sDisplayInfo = w + " × " + h + " • " + hz + "Hz • " + dm.densityDpi + " DPI";
            }
        } catch (Throwable ignored) {
            sDisplayInfo = "";
        }
    }

    private static void detectCpuMaxClock() {
        long maxKhz = 0;
        try {
            for (int i = 0; i < 16; i++) {
                String p1 = "/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq";
                String p2 = "/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_max_freq";
                String val = readFileFirstLine(p1);
                if (val == null) val = readFileFirstLine(p2);
                if (val != null) {
                    try {
                        long khz = Long.parseLong(val.trim());
                        if (khz > maxKhz) maxKhz = khz;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        if (maxKhz > 0) {
            sCpuMaxClock = String.format("%.2f GHz", maxKhz / 1000000.0f);
        } else {
            String soc = getSocName().toLowerCase();
            if (soc.contains("8 gen 2")) sCpuMaxClock = "3.36 GHz";
            else if (soc.contains("8 gen 3")) sCpuMaxClock = "3.30 GHz";
            else if (soc.contains("8 elite")) sCpuMaxClock = "4.32 GHz";
            else sCpuMaxClock = "";
        }
    }

    private static void detectGpu() {
        String[] modelPaths = new String[]{
            "/sys/class/kgsl/kgsl-3d0/gpu_model",
            "/sys/class/drm/card0/device/product_name",
            "/sys/class/drm/card0/device/gpu_model",
            "/sys/class/misc/mali0/device/gpuinfo",
            "/sys/kernel/gpu/gpu_model",
            "/proc/gpuinfo"
        };
        for (String path : modelPaths) {
            String model = cleanIdentity(readFileFirstLine(path));
            if (!model.isEmpty()) {
                sGpuModel = model;
                return;
            }
        }

        String[] modelProps = new String[]{
            "ro.gpu.model", "ro.hardware.gpu", "ro.opengles.gpu",
            "ro.vendor.gpu.model", "vendor.gpu.model"
        };
        for (String prop : modelProps) {
            String model = cleanIdentity(getProp(prop));
            if (!model.isEmpty() && !"default".equalsIgnoreCase(model)) {
                sGpuModel = model;
                return;
            }
        }

        String mappedGpu = findMappedModel(GPU_LUT,
            getProp("ro.soc.model"), getProp("ro.board.platform"),
            getProp("ro.hardware.chipname"), getProp("ro.chipname"),
            Build.HARDWARE, readCpuinfoHardware(),
            readFileFirstLine("/proc/device-tree/compatible"),
            readFileFirstLine("/sys/firmware/devicetree/base/compatible"));
        if (!mappedGpu.isEmpty()) {
            sGpuModel = mappedGpu;
            return;
        }

        String soc = getSocName().toLowerCase();
        if (soc.contains("8 gen 2")) sGpuModel = "Adreno 740";
        else if (soc.contains("8 gen 3")) sGpuModel = "Adreno 750";
        else if (soc.contains("8 elite")) sGpuModel = "Adreno 830";
        else if (soc.contains("8+ gen 1") || soc.contains("8 gen 1")) sGpuModel = "Adreno 730";
        else if (soc.contains("888")) sGpuModel = "Adreno 660";
        else if (soc.contains("9400")) sGpuModel = "Immortalis-G925";
        else if (soc.contains("9300")) sGpuModel = "Immortalis-G720";
        else if (soc.contains("9200")) sGpuModel = "Immortalis-G715";
        else sGpuModel = "Adreno / Mali GPU";
    }

    private static void detectHardwareDetails() {
        String cpuCode = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try { cpuCode = cleanIdentity(Build.SOC_MODEL); } catch (Throwable ignored) {}
        }
        if (cpuCode.isEmpty()) cpuCode = firstNonEmpty(
            getProp("ro.soc.model"), getProp("ro.vendor.soc.model"),
            getProp("ro.board.platform"), getProp("ro.mediatek.platform"),
            getProp("ro.hardware.chipname"), getProp("ro.chipname"),
            readCpuinfoHardware(), readFileFirstLine("/proc/device-tree/model"));
        String socManufacturer = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try { socManufacturer = cleanIdentity(Build.SOC_MANUFACTURER); } catch (Throwable ignored) {}
        }
        if (!socManufacturer.isEmpty() && !cpuCode.toLowerCase(Locale.US).contains(socManufacturer.toLowerCase(Locale.US))) {
            cpuCode = socManufacturer + " " + cpuCode;
        }

        String abi = "";
        try {
            if (Build.SUPPORTED_ABIS != null) {
                StringBuilder abis = new StringBuilder();
                for (String value : Build.SUPPORTED_ABIS) {
                    if (value == null || value.trim().isEmpty()) continue;
                    if (abis.length() > 0) abis.append(", ");
                    abis.append(value.trim());
                }
                abi = abis.toString();
            }
        } catch (Throwable ignored) {}

        String gpu = getGpuModel();
        String gpuVendor = inferGpuVendor(gpu);
        String gpuDriver = firstNonEmpty(
            getProp("ro.gfx.driver.0"), getProp("ro.hardware.egl"), getProp("ro.vendor.gpu.driver")
        );

        String maker = cleanIdentity(Build.MANUFACTURER);
        String model = cleanIdentity(Build.MODEL);
        String deviceName = (maker + " " + model).trim();
        if (deviceName.isEmpty()) deviceName = cleanIdentity(Build.DEVICE);

        String board = cleanIdentity(Build.BOARD);
        String kernel = cleanIdentity(System.getProperty("os.version", ""));
        StringBuilder platform = new StringBuilder("Android ").append(Build.VERSION.RELEASE)
            .append(" (API ").append(Build.VERSION.SDK_INT).append(")");
        if (!board.isEmpty()) platform.append(" • ").append(board);
        if (!kernel.isEmpty()) platform.append(" • Linux ").append(kernel);

        sHardwareDetails = new HardwareDetails(cpuCode, abi, detectCpuTopology(),
            gpuVendor, gpuDriver, deviceName, platform.toString());
    }

    private static String detectCpuTopology() {
        TreeMap<Long, Integer> clusters = new TreeMap<>();
        for (int cpu = 0; cpu < 64; cpu++) {
            String base = "/sys/devices/system/cpu/cpu" + cpu + "/cpufreq/";
            long khz = readLongFromFile(base + "cpuinfo_max_freq");
            if (khz <= 0) khz = readLongFromFile(base + "scaling_max_freq");
            if (khz > 0) clusters.put(khz, clusters.containsKey(khz) ? clusters.get(khz) + 1 : 1);
        }
        StringBuilder result = new StringBuilder();
        for (Map.Entry<Long, Integer> entry : clusters.descendingMap().entrySet()) {
            if (result.length() > 0) result.append(" + ");
            result.append(entry.getValue()).append('×')
                .append(String.format(Locale.US, "%.2f", entry.getKey() / 1_000_000.0))
                .append(" GHz");
        }
        return result.toString();
    }

    private static String inferGpuVendor(String model) {
        String value = model == null ? "" : model.toLowerCase(Locale.US);
        if (value.contains("adreno")) return "Qualcomm";
        if (value.contains("mali") || value.contains("immortalis")) return "Arm";
        if (value.contains("powervr")) return "Imagination";
        if (value.contains("xclipse")) return "Samsung / AMD";
        return "";
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            String cleaned = cleanIdentity(value);
            if (!cleaned.isEmpty()) return cleaned;
        }
        return "";
    }

    private static String cleanIdentity(String value) {
        if (value == null) return "";
        String cleaned = value.replace('\n', ' ').replace('\r', ' ').trim();
        while (cleaned.contains("  ")) cleaned = cleaned.replace("  ", " ");
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }

    private static void detectSoc() {
        String buildSoc = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try { buildSoc = Build.SOC_MODEL; } catch (Throwable ignored) {}
        }
        String[] candidates = new String[]{
            buildSoc,
            getProp("ro.soc.model"), getProp("ro.vendor.soc.model"),
            getProp("ro.board.platform"), getProp("ro.mediatek.platform"),
            getProp("ro.hardware.chipname"), getProp("ro.chipname"),
            Build.HARDWARE, readCpuinfoHardware(),
            readFileFirstLine("/proc/device-tree/model"),
            readFileFirstLine("/proc/device-tree/compatible"),
            readFileFirstLine("/sys/firmware/devicetree/base/compatible")
        };
        String mapped = findMappedModel(SOC_LUT, candidates);
        if (!mapped.isEmpty()) {
            sSocName = mapped;
            return;
        }
        for (String candidate : candidates) {
            String value = cleanIdentity(candidate).replace("Qualcomm Technologies, Inc", "").trim();
            if (value.isEmpty() || "qcom".equalsIgnoreCase(value)) continue;
            sSocName = value;
            return;
        }
        sSocName = "ARM Processor";
    }

    /** Exact code first, otherwise the longest normalized alias wins. */
    private static String findMappedModel(Map<String, String> table, String... candidates) {
        String bestValue = "";
        int bestKeyLength = -1;
        for (String candidate : candidates) {
            String normalized = normalizeHardwareCode(candidate);
            if (normalized.isEmpty()) continue;
            String exact = table.get(normalized);
            if (exact != null) return exact;
            for (Map.Entry<String, String> entry : table.entrySet()) {
                String key = normalizeHardwareCode(entry.getKey());
                if (key.length() > bestKeyLength && normalized.contains(key)) {
                    bestKeyLength = key.length();
                    bestValue = entry.getValue();
                }
            }
        }
        return bestValue;
    }

    private static String normalizeHardwareCode(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", "");
    }

    private static void detectStorage() {
        String[] ufsSpecPaths = new String[]{
            "/sys/bus/ufs/devices/ufs_device/spec_version",
            "/sys/class/block/sda/device/spec_version",
            "/sys/class/ufs/devices/ufs_device/spec_version",
            "/sys/devices/platform/soc/1d84000.ufshc/ufs_device/spec_version",
            "/sys/devices/platform/soc/1d84000.ufshc/spec_version"
        };
        for (String p : ufsSpecPaths) {
            String val = readFileFirstLine(p);
            if (val != null && !val.trim().isEmpty()) {
                String parsed = parseUfsVersion(val.trim());
                if (parsed != null) {
                    sStorageType = parsed;
                    return;
                }
            }
        }

        String mmcRev = readFileFirstLine("/sys/block/mmcblk0/device/rev");
        if (mmcRev != null && !mmcRev.trim().isEmpty()) {
            sStorageType = parseEmmcVersion(mmcRev.trim());
            return;
        }

        if (RootTool.hasRoot()) {
            String script = "for f in /sys/bus/ufs/devices/ufs_device/spec_version /sys/class/block/sda/device/spec_version /sys/devices/platform/soc/*.ufshc/ufs_device/spec_version /sys/devices/platform/soc/*.ufshc/spec_version /sys/class/ufs/devices/ufs_device/spec_version; do if [ -f \"$f\" ]; then cat \"$f\"; break; fi; done; echo '---'; cat /sys/block/mmcblk0/device/rev 2>/dev/null; echo '---'; getprop ro.boot.bootdevice 2>/dev/null";
            String out = RootTool.runCommand(script);
            if (out != null && !out.isEmpty()) {
                String[] sections = out.split("---");
                if (sections.length >= 1 && !sections[0].trim().isEmpty()) {
                    String parsed = parseUfsVersion(sections[0].trim());
                    if (parsed != null) {
                        sStorageType = parsed;
                        return;
                    }
                }
                if (sections.length >= 2 && !sections[1].trim().isEmpty()) {
                    sStorageType = parseEmmcVersion(sections[1].trim());
                    return;
                }
                if (sections.length >= 3 && sections[2].toLowerCase().contains("ufshc")) {
                    sStorageType = inferUfsFromSoc();
                    return;
                }
            }
        }

        String bootdevice = getProp("ro.boot.bootdevice");
        if (bootdevice.toLowerCase().contains("ufshc") || new File("/sys/block/sda").exists() || new File("/dev/block/sda").exists()) {
            sStorageType = inferUfsFromSoc();
            return;
        }
        if (new File("/sys/block/mmcblk0").exists() || new File("/dev/block/mmcblk0").exists()) {
            sStorageType = "eMMC";
            return;
        }
        if (new File("/sys/block/nvme0n1").exists()) {
            sStorageType = "NVMe";
            return;
        }
        sStorageType = "";
    }

    private static String parseUfsVersion(String val) {
        try {
            val = val.trim();
            int v;
            if (val.startsWith("0x") || val.startsWith("0X")) {
                v = Integer.parseInt(val.substring(2), 16);
            } else if (val.matches("^[0-9a-fA-F]{3,4}$")) {
                v = Integer.parseInt(val, 16);
            } else {
                v = Integer.parseInt(val);
            }
            if (v >= 0x0400) return "UFS 4.0";
            if (v >= 0x0310) return "UFS 3.1";
            if (v >= 0x0300) return "UFS 3.0";
            if (v >= 0x0220) return "UFS 2.2";
            if (v >= 0x0210) return "UFS 2.1";
            if (v >= 0x0200) return "UFS 2.0";
            if (v >= 0x0110) return "UFS 1.1";
            if (v >= 0x0100) return "UFS 1.0";
        } catch (Throwable ignored) {}
        return null;
    }

    private static String parseEmmcVersion(String val) {
        try {
            val = val.trim();
            int v = val.startsWith("0x") ? Integer.parseInt(val.substring(2), 16) : Integer.parseInt(val);
            if (v >= 8) return "eMMC 5.1";
            if (v == 7) return "eMMC 5.0";
            if (v == 6) return "eMMC 4.5";
            if (v == 5) return "eMMC 4.41";
        } catch (Throwable ignored) {}
        return "eMMC";
    }

    private static String inferUfsFromSoc() {
        String soc = getSocName().toLowerCase();
        if (soc.contains("gen 2") || soc.contains("gen 3") || soc.contains("elite") || soc.contains("9400") || soc.contains("9300")) {
            return "UFS 4.0";
        }
        if (soc.contains("888") || soc.contains("865") || soc.contains("gen 1") || soc.contains("9200") || soc.contains("9000") || soc.contains("8100")) {
            return "UFS 3.1";
        }
        return "UFS";
    }

    private static void detectRam() {
        String[] ramProps = new String[]{
            "ro.boot.ddr_info",
            "ro.boot.ddr_type",
            "ro.boot.dram_type",
            "ro.boot.hardware.ddr",
            "ro.vendor.ddr_info",
            "vendor.boot.dram_type",
            "ro.boot.ram_type"
        };
        for (String p : ramProps) {
            String val = getProp(p);
            if (val != null && !val.trim().isEmpty()) {
                String parsed = parseRamType(val.trim());
                if (parsed != null) {
                    sRamType = parsed;
                    return;
                }
            }
        }

        if (RootTool.hasRoot()) {
            String dmesg = RootTool.runCommand("dmesg 2>/dev/null | grep -iE \"lpddr5x|lpddr5|lpddr4x|ddr_type|dram_type\" | tail -n 5");
            if (dmesg != null && !dmesg.isEmpty()) {
                String parsed = parseRamType(dmesg);
                if (parsed != null) {
                    sRamType = parsed;
                    return;
                }
            }
        }

        String soc = getSocName().toLowerCase();
        if (soc.contains("gen 3") || soc.contains("elite") || soc.contains("9400") || soc.contains("9300") || soc.contains("tensor g3") || soc.contains("tensor g4") || soc.contains("gen 2")) {
            sRamType = "LPDDR5X";
            return;
        }
        if (soc.contains("888") || soc.contains("865") || soc.contains("gen 1") || soc.contains("9200") || soc.contains("9000") || soc.contains("tensor g1") || soc.contains("tensor g2") || soc.contains("8100")) {
            sRamType = "LPDDR5";
            return;
        }
        if (soc.contains("778") || soc.contains("765") || soc.contains("855") || soc.contains("845") || soc.contains("695")) {
            sRamType = "LPDDR4X";
            return;
        }
        sRamType = "";
    }

    private static String parseRamType(String text) {
        if (text == null) return null;
        String upper = text.toUpperCase();
        if (upper.contains("LPDDR5X")) return "LPDDR5X";
        if (upper.contains("LPDDR5")) return "LPDDR5";
        if (upper.contains("LPDDR4X")) return "LPDDR4X";
        if (upper.contains("LPDDR4")) return "LPDDR4";
        if (upper.contains("LPDDR3")) return "LPDDR3";

        String trimmed = text.trim();
        if ("6".equals(trimmed) || "7".equals(trimmed) || "2".equals(trimmed)) return "LPDDR5X";
        if ("5".equals(trimmed) || "1".equals(trimmed)) return "LPDDR5";
        if ("4".equals(trimmed) || "0".equals(trimmed)) return "LPDDR4X";
        return null;
    }

    public static String getProp(String key) {
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            Method method = clazz.getMethod("get", String.class);
            String val = (String) method.invoke(null, key);
            if (val != null && !val.trim().isEmpty()) return val.trim();
        } catch (Throwable ignored) {}
        return "";
    }

    private static String readFileFirstLine(String path) {
        File file = new File(path);
        if (!file.exists() || !file.canRead()) return null;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.readLine();
        } catch (Throwable ignored) {}
        return null;
    }

    private static String readCpuinfoHardware() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Hardware") || line.startsWith("model name")) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2 && !parts[1].trim().isEmpty()) {
                        return parts[1].trim();
                    }
                }
            }
        } catch (Throwable ignored) {}
        return "";
    }
}
