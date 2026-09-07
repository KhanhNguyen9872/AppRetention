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
import java.util.Map;

public class HardwareInfo {

    private static volatile String sSocName = null;
    private static volatile String sStorageType = null;
    private static volatile String sRamType = null;
    private static volatile String sGpuModel = null;
    private static volatile String sCpuMaxClock = null;
    private static volatile String sDisplayInfo = null;
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
    }

    public static synchronized void init(Context context) {
        if (sInitialized) return;
        detectSoc();
        detectStorage();
        detectRam();
        detectGpu();
        detectCpuMaxClock();
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
            if (line == null && RootTool.hasRoot()) {
                line = RootTool.runCommand("cat " + p + " 2>/dev/null");
            }
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
            if (line == null && RootTool.hasRoot()) {
                line = RootTool.runCommand("cat " + p + " 2>/dev/null");
            }
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
        String model = readFileFirstLine("/sys/class/kgsl/kgsl-3d0/gpu_model");
        if (model != null && !model.trim().isEmpty()) {
            sGpuModel = model.trim();
            return;
        }

        String socCode = getProp("ro.soc.model");
        if (socCode.isEmpty()) socCode = getProp("ro.board.platform");
        String clean = socCode.toLowerCase().split("-")[0].replace(" ", "");
        if (GPU_LUT.containsKey(clean)) {
            sGpuModel = GPU_LUT.get(clean);
            return;
        }
        for (Map.Entry<String, String> entry : GPU_LUT.entrySet()) {
            if (clean.contains(entry.getKey())) {
                sGpuModel = entry.getValue();
                return;
            }
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

    private static void detectSoc() {
        String socModel = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                socModel = Build.SOC_MODEL;
            } catch (Throwable ignored) {}
        }
        if (socModel == null || socModel.isEmpty()) {
            socModel = getProp("ro.soc.model");
        }
        String platform = getProp("ro.board.platform");
        String hardware = Build.HARDWARE;
        String chipName = getProp("ro.hardware.chipname");
        if (chipName == null || chipName.isEmpty()) {
            chipName = getProp("ro.chipname");
        }
        String cpuinfoHw = readCpuinfoHardware();

        sSocName = resolveSoc(socModel, platform, hardware, chipName, cpuinfoHw);
    }

    private static String resolveSoc(String socModel, String platform, String hardware, String chipName, String cpuinfoHw) {
        String[] candidates = new String[]{socModel, platform, chipName, cpuinfoHw, hardware};
        for (String cand : candidates) {
            if (cand == null || cand.trim().isEmpty()) continue;
            String clean = cand.trim().toLowerCase().split("-")[0].replace(" ", "");
            if (SOC_LUT.containsKey(clean)) {
                return SOC_LUT.get(clean);
            }
            for (Map.Entry<String, String> entry : SOC_LUT.entrySet()) {
                if (clean.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }

        for (String cand : candidates) {
            if (cand == null || cand.trim().isEmpty()) continue;
            String upper = cand.toUpperCase();
            if (upper.contains("SNAPDRAGON") || upper.contains("DIMENSITY") || upper.contains("TENSOR") || upper.contains("EXYNOS")) {
                return cand.replace("Qualcomm Technologies, Inc", "").trim();
            }
        }

        if (socModel != null && !socModel.trim().isEmpty()) {
            return socModel.replace("Qualcomm Technologies, Inc", "").trim();
        }
        if (platform != null && !platform.trim().isEmpty()) {
            return platform.toUpperCase().trim();
        }
        if (hardware != null && !hardware.trim().isEmpty() && !hardware.equalsIgnoreCase("qcom")) {
            return hardware.trim();
        }
        return "ARM Processor";
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