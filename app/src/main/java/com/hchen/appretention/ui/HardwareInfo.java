package com.hchen.appretention.ui;

import android.os.Build;
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
    private static volatile boolean sInitialized = false;

    private static final Map<String, String> SOC_LUT = new HashMap<>();

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
    }

    public static synchronized void init() {
        if (sInitialized) return;
        detectSoc();
        detectStorage();
        detectRam();
        sInitialized = true;
    }

    public static String getSocName() {
        if (sSocName == null) init();
        return sSocName != null ? sSocName : "ARM Processor";
    }

    public static String getStorageType() {
        if (sStorageType == null) init();
        return sStorageType != null ? sStorageType : "";
    }

    public static String getRamType() {
        if (sRamType == null) init();
        return sRamType != null ? sRamType : "";
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