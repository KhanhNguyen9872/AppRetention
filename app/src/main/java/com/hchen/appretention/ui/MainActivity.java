package com.hchen.appretention.ui;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.hchen.appretention.R;
import com.hchen.hooktool.utils.SystemPropTool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String PREF_NAME = "AppRetentionConfig";
    private static final String KEY_VIP_PACKAGES = "persist.hchen.adj.vip_packages";
    private static final String KEY_TIERED_ADJ = "persist.hchen.adj.perceptible.enable";
    private static final String KEY_KILL_SHIELD = "persist.hchen.killshield.enable";
    private static final String KEY_DOZE = "persist.hchen.doze.opt.enable";
    private static final String KEY_NUBIA = "persist.hchen.nubia.opt.enable";
    private static final String KEY_HIBERNATION = "persist.hchen.hibernation.opt.enable";
    private static final String KEY_AUTOSTART = "persist.hchen.autostart.opt.enable";

    // Memory-leak-free Icon Cache
    private static final LruCache<String, Drawable.ConstantState> sIconCache = new LruCache<>(150);
    private static final LruCache<String, String> sLabelCache = new LruCache<>(250);

    // Background worker thread pool
    private static final ExecutorService sWorkerPool = Executors.newFixedThreadPool(2);

    private SharedPreferences prefs;

    // UI elements
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvStatusBadge;
    private TextView tvModeDetail;
    private TextView tvShieldCount;

    // Hardware monitor UI
    private TextView tvRamDetails;
    private TextView tvRamSubtext;
    private LinearProgressIndicator pbRam;

    private TextView tvCpuDetails;
    private TextView tvCpuSubtext;
    private LinearProgressIndicator pbCpu;

    private TextView tvStorageDetails;
    private TextView tvStorageSubtext;
    private LinearProgressIndicator pbStorage;

    private TextView tvProcessCount;
    private TextView tvVipSummary;
    private TextView tvLogContent;

    private MaterialSwitch switchTieredAdj;
    private MaterialSwitch switchKillShield;
    private MaterialSwitch switchDoze;
    private MaterialSwitch switchNubia;
    private MaterialSwitch switchHibernation;
    private MaterialSwitch switchAutoStart;

    private RecyclerView rvProcesses;
    private ProcessAdapter processAdapter;
    private final List<ProcessItem> processList = new ArrayList<>();

    // 5-second recurring auto-refresh handler
    private final Handler mTimerHandler = new Handler(Looper.getMainLooper());
    private final Runnable mPeriodicRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            updateHardwareStats();
            mTimerHandler.postDelayed(this, 5000);
        }
    };

    // CPU measurement state
    private long mLastTotalCpu = 0;
    private long mLastIdleCpu = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        initViews();
        setupSwitches();
        refreshAll();

        // Check root status
        sWorkerPool.execute(() -> {
            boolean hasRoot = RootTool.isRootAvailable();
            runOnUiThread(() -> {
                if (hasRoot) {
                    tvModeDetail.setText("Scope: android (system_server) • Root: Active (KernelSU/Magisk)");
                } else {
                    tvModeDetail.setText("Scope: android (system_server) • Root: Standard Mode");
                }
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mTimerHandler.removeCallbacks(mPeriodicRefreshRunnable);
        mTimerHandler.post(mPeriodicRefreshRunnable);
        updateVipSummary();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Prevent background battery drain when app is paused
        mTimerHandler.removeCallbacks(mPeriodicRefreshRunnable);
    }

    private void initViews() {
        // GitHub Action Button
        MaterialButton btnGithub = findViewById(R.id.btnGithub);
        if (btnGithub != null) {
            btnGithub.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/KhanhNguyen9872/AppRetention"));
                    startActivity(intent);
                } catch (Throwable t) {
                    Toast.makeText(this, "Could not open browser", Toast.LENGTH_SHORT).show();
                }
            });
        }

        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this::refreshAll);

        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvModeDetail = findViewById(R.id.tvModeDetail);
        tvShieldCount = findViewById(R.id.tvShieldCount);

        // Hardware monitors
        tvRamDetails = findViewById(R.id.tvRamDetails);
        tvRamSubtext = findViewById(R.id.tvRamSubtext);
        pbRam = findViewById(R.id.pbRam);

        tvCpuDetails = findViewById(R.id.tvCpuDetails);
        tvCpuSubtext = findViewById(R.id.tvCpuSubtext);
        pbCpu = findViewById(R.id.pbCpu);

        tvStorageDetails = findViewById(R.id.tvStorageDetails);
        tvStorageSubtext = findViewById(R.id.tvStorageSubtext);
        pbStorage = findViewById(R.id.pbStorage);

        tvProcessCount = findViewById(R.id.tvProcessCount);
        tvVipSummary = findViewById(R.id.tvVipSummary);
        tvLogContent = findViewById(R.id.tvLogContent);

        switchTieredAdj = findViewById(R.id.switchTieredAdj);
        switchKillShield = findViewById(R.id.switchKillShield);
        switchDoze = findViewById(R.id.switchDoze);
        switchNubia = findViewById(R.id.switchNubia);
        switchHibernation = findViewById(R.id.switchHibernation);
        switchAutoStart = findViewById(R.id.switchAutoStart);

        rvProcesses = findViewById(R.id.rvProcesses);
        rvProcesses.setLayoutManager(new LinearLayoutManager(this));
        processAdapter = new ProcessAdapter(processList);
        rvProcesses.setAdapter(processAdapter);

        MaterialButton btnManageVip = findViewById(R.id.btnManageVip);
        btnManageVip.setOnClickListener(v -> showVipAppsDialog());

        MaterialButton btnClearLog = findViewById(R.id.btnClearLog);
        btnClearLog.setOnClickListener(v -> {
            clearLogFiles();
            tvLogContent.setText("Log cleared.");
        });
    }

    private void setupSwitches() {
        bindSwitch(switchTieredAdj, KEY_TIERED_ADJ, true);
        bindSwitch(switchKillShield, KEY_KILL_SHIELD, true);
        bindSwitch(switchDoze, KEY_DOZE, true);
        bindSwitch(switchNubia, KEY_NUBIA, true);
        bindSwitch(switchHibernation, KEY_HIBERNATION, true);
        bindSwitch(switchAutoStart, KEY_AUTOSTART, true);
    }

    private void bindSwitch(MaterialSwitch sw, String key, boolean defValue) {
        boolean val = prefs.getBoolean(key, SystemPropTool.getProp(key, defValue));
        sw.setChecked(val);
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(key, isChecked).apply();
            RootTool.setProp(key, String.valueOf(isChecked));
        });
    }

    private void refreshAll() {
        updateHardwareStats();
        updateVipSummary();
        refreshRunningProcesses();
        refreshLogs();
        swipeRefresh.setRefreshing(false);
    }

    private void updateHardwareStats() {
        sWorkerPool.execute(() -> {
            // 1. RAM Calculation
            long totalRam = 0;
            try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
                String line = br.readLine();
                if (line != null) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2) {
                        totalRam = Long.parseLong(parts[1]) * 1024L;
                    }
                }
            } catch (Throwable ignored) {}

            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            if (am != null) am.getMemoryInfo(mi);

            long totalRamBytes = totalRam > 0 ? totalRam : mi.totalMem;
            long availRamBytes = mi.availMem;
            long usedRamBytes = Math.max(0, totalRamBytes - availRamBytes);
            int ramPercent = (int) Math.min(100, (usedRamBytes * 100) / (totalRamBytes > 0 ? totalRamBytes : 1));
            double usedRamGb = usedRamBytes / (1024.0 * 1024.0 * 1024.0);
            double totalRamGb = totalRamBytes / (1024.0 * 1024.0 * 1024.0);
            double availRamGb = availRamBytes / (1024.0 * 1024.0 * 1024.0);
            int discount = (int) Math.round(totalRamGb) >= 15 ? 5 : ((int) Math.round(totalRamGb) >= 11 ? 4 : 3);

            // 2. CPU Calculation
            int cpuPercent = readCpuUsage();
            int cores = Runtime.getRuntime().availableProcessors();

            // 3. Storage Calculation
            File dataDir = Environment.getDataDirectory();
            StatFs stat = new StatFs(dataDir.getPath());
            long blockSize = stat.getBlockSizeLong();
            long totalBlocks = stat.getBlockCountLong();
            long availBlocks = stat.getAvailableBlocksLong();
            long totalStorageBytes = totalBlocks * blockSize;
            long freeStorageBytes = availBlocks * blockSize;
            long usedStorageBytes = Math.max(0, totalStorageBytes - freeStorageBytes);
            int storagePercent = (int) Math.min(100, (usedStorageBytes * 100) / (totalStorageBytes > 0 ? totalStorageBytes : 1));
            double usedStorageGb = usedStorageBytes / (1024.0 * 1024.0 * 1024.0);
            double totalStorageGb = totalStorageBytes / (1024.0 * 1024.0 * 1024.0);
            double freeStorageGb = freeStorageBytes / (1024.0 * 1024.0 * 1024.0);

            runOnUiThread(() -> {
                // Update RAM
                tvRamDetails.setText(String.format("%.1f GB / %.1f GB (%d%%)", usedRamGb, totalRamGb, ramPercent));
                tvRamSubtext.setText(String.format("Available: %.1f GB • LMKD MinFree Discount: %dx", availRamGb, discount));
                pbRam.setProgress(ramPercent);

                // Update CPU
                if (cpuPercent >= 0) {
                    tvCpuDetails.setText(String.format(Locale.US, "%d%% Load", cpuPercent));
                    tvCpuSubtext.setText(String.format(Locale.US, "Active Cores: %d • Sampling rate: 5s", cores));
                    pbCpu.setProgress(cpuPercent);
                } else {
                    tvCpuDetails.setText("N/A");
                    tvCpuSubtext.setText(String.format(Locale.US, "Active Cores: %d • Root required", cores));
                    pbCpu.setProgress(0);
                }

                // Update Storage
                tvStorageDetails.setText(String.format("%.1f GB / %.1f GB (%d%%)", usedStorageGb, totalStorageGb, storagePercent));
                tvStorageSubtext.setText(String.format("Free: %.1f GB", freeStorageGb));
                pbStorage.setProgress(storagePercent);

                tvShieldCount.setText("KillShield: Active & Shielding Background Kills");
            });
        });
    }

    private static volatile Boolean sCanReadProcStatDirectly = null;

    private static String getCpuStatLine() {
        if (sCanReadProcStatDirectly == null || sCanReadProcStatDirectly) {
            try (BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"))) {
                String line = reader.readLine();
                if (line != null && line.startsWith("cpu ")) {
                    sCanReadProcStatDirectly = true;
                    return line;
                }
            } catch (Throwable e) {
                sCanReadProcStatDirectly = false;
            }
        }
        return RootTool.readCpuStatLine();
    }

    private static long[] parseCpuLine(String line) {
        try {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 5) {
                long user = Long.parseLong(parts[1]);
                long nice = Long.parseLong(parts[2]);
                long system = Long.parseLong(parts[3]);
                long idle = Long.parseLong(parts[4]);
                long iowait = parts.length > 5 ? Long.parseLong(parts[5]) : 0;
                long irq = parts.length > 6 ? Long.parseLong(parts[6]) : 0;
                long softirq = parts.length > 7 ? Long.parseLong(parts[7]) : 0;
                long steal = parts.length > 8 ? Long.parseLong(parts[8]) : 0;

                long total = user + nice + system + idle + iowait + irq + softirq + steal;
                long totalIdle = idle + iowait;
                return new long[]{total, totalIdle};
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private int readCpuUsage() {
        String line = getCpuStatLine();
        if (line != null) {
            long[] stats = parseCpuLine(line);
            if (stats != null) {
                long total = stats[0];
                long totalIdle = stats[1];

                if (mLastTotalCpu != 0) {
                    long diffTotal = total - mLastTotalCpu;
                    long diffIdle = totalIdle - mLastIdleCpu;
                    mLastTotalCpu = total;
                    mLastIdleCpu = totalIdle;
                    if (diffTotal > 0) {
                        int usage = (int) (100 * (diffTotal - diffIdle) / diffTotal);
                        return Math.max(1, Math.min(100, usage));
                    }
                } else {
                    // Initial cold-start sample: 200ms quick delta so user sees live load immediately
                    try {
                        Thread.sleep(200);
                        String line2 = getCpuStatLine();
                        if (line2 != null) {
                            long[] stats2 = parseCpuLine(line2);
                            if (stats2 != null) {
                                long diffTotal = stats2[0] - total;
                                long diffIdle = stats2[1] - totalIdle;
                                mLastTotalCpu = stats2[0];
                                mLastIdleCpu = stats2[1];
                                if (diffTotal > 0) {
                                    int usage = (int) (100 * (diffTotal - diffIdle) / diffTotal);
                                    return Math.max(1, Math.min(100, usage));
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                    mLastTotalCpu = total;
                    mLastIdleCpu = totalIdle;
                }
            }
        }

        // Secondary fallback: Hardware CPU frequency scaling
        int freqLoad = readCpuFreqLoad();
        if (freqLoad >= 0) {
            return freqLoad;
        }

        return -1;
    }

    private int readCpuFreqLoad() {
        try {
            int cores = Runtime.getRuntime().availableProcessors();
            long totalCur = 0;
            long totalMax = 0;
            for (int i = 0; i < cores; i++) {
                long cur = readLongFromFile("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
                long max = readLongFromFile("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
                if (cur > 0 && max > 0) {
                    totalCur += cur;
                    totalMax += max;
                }
            }
            if (totalMax > 0) {
                return (int) Math.max(1, Math.min(100, (totalCur * 100) / totalMax));
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    private static long readLongFromFile(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line = reader.readLine();
            if (line != null) {
                return Long.parseLong(line.trim());
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    private void updateVipSummary() {
        Set<String> vips = prefs.getStringSet(KEY_VIP_PACKAGES, Collections.emptySet());
        tvVipSummary.setText(String.format("Keep-Alive: %d apps (Locked at ADJ 200)", vips.size()));
    }

    private void refreshRunningProcesses() {
        sWorkerPool.execute(() -> {
            List<ProcessItem> items = new ArrayList<>();
            PackageManager pm = getPackageManager();
            Set<String> vipSet = prefs.getStringSet(KEY_VIP_PACKAGES, Collections.emptySet());

            if (RootTool.isRootAvailable()) {
                List<RootTool.ProcessInfo> rootProcs = RootTool.getRunningProcesses();
                for (RootTool.ProcessInfo pi : rootProcs) {
                    String pkg = pi.processName;
                    if (pkg.contains(":")) {
                        pkg = pkg.substring(0, pkg.indexOf(':'));
                    }
                    String label = sLabelCache.get(pkg);
                    Drawable.ConstantState iconState = sIconCache.get(pkg);
                    Drawable icon = iconState != null ? iconState.newDrawable() : null;

                    if (label == null || icon == null) {
                        try {
                            ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                            if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                            label = pm.getApplicationLabel(appInfo).toString();
                            Drawable rawIcon = pm.getApplicationIcon(appInfo);
                            sLabelCache.put(pkg, label);
                            if (rawIcon.getConstantState() != null) {
                                sIconCache.put(pkg, rawIcon.getConstantState());
                                icon = rawIcon.getConstantState().newDrawable();
                            } else {
                                icon = rawIcon;
                            }
                        } catch (Throwable ignored) {
                            continue;
                        }
                    }

                    int adj = pi.adj;
                    if (vipSet.contains(pkg)) {
                        adj = 200;
                    }
                    items.add(new ProcessItem(label, pi.processName, pi.pid, adj, icon));
                }
            } else {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    List<ActivityManager.RunningAppProcessInfo> running = am.getRunningAppProcesses();
                    if (running != null) {
                        for (ActivityManager.RunningAppProcessInfo info : running) {
                            if (info.uid < 10000) continue;
                            String pkg = (info.pkgList != null && info.pkgList.length > 0) ? info.pkgList[0] : info.processName;
                            String label = sLabelCache.get(pkg);
                            Drawable.ConstantState iconState = sIconCache.get(pkg);
                            Drawable icon = iconState != null ? iconState.newDrawable() : null;
                            if (label == null || icon == null) {
                                try {
                                    ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                                    label = pm.getApplicationLabel(appInfo).toString();
                                    Drawable rawIcon = pm.getApplicationIcon(appInfo);
                                    sLabelCache.put(pkg, label);
                                    if (rawIcon.getConstantState() != null) {
                                        sIconCache.put(pkg, rawIcon.getConstantState());
                                        icon = rawIcon.getConstantState().newDrawable();
                                    } else {
                                        icon = rawIcon;
                                    }
                                } catch (Throwable ignored) {}
                            }
                            if (label == null) label = pkg;
                            int estimatedAdj = vipSet.contains(pkg) ? 200 : (200 + (items.size() * 5));
                            items.add(new ProcessItem(label, pkg, info.pid, estimatedAdj, icon));
                        }
                    }
                }
            }

            runOnUiThread(() -> {
                processAdapter.updateList(items);
                tvProcessCount.setText(items.size() + " apps active");
            });
        });
    }

    private void refreshLogs() {
        StringBuilder sb = new StringBuilder();
        File logDir = new File(getExternalFilesDir(null), "logs");
        if (!logDir.exists()) {
            logDir = new File(getFilesDir(), "logs");
        }
        if (logDir.exists() && logDir.isDirectory()) {
            File[] files = logDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        if (sb.length() > 0) {
            tvLogContent.setText(sb.toString());
        } else {
            tvLogContent.setText("No Hook logs recorded yet. Logs will appear here as Android system services run.");
        }
    }

    private void clearLogFiles() {
        File[] dirs = new File[]{
            new File(getExternalFilesDir(null), "logs"),
            new File(getFilesDir(), "logs")
        };
        for (File dir : dirs) {
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        f.delete();
                    }
                }
            }
        }
    }

    private void showVipAppsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_vip_apps, null);
        RecyclerView rv = dialogView.findViewById(R.id.rvAppList);
        ProgressBar pb = dialogView.findViewById(R.id.pbLoadingApps);
        EditText etSearch = dialogView.findViewById(R.id.etSearchApp);

        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setVisibility(View.GONE);
        pb.setVisibility(View.VISIBLE);

        List<AppItem> appItems = new ArrayList<>();
        AppListAdapter adapter = new AppListAdapter(appItems);
        rv.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s != null ? s.toString() : "");
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create();

        // Async Background App Loader
        sWorkerPool.execute(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            Set<String> currentVips = new HashSet<>(prefs.getStringSet(KEY_VIP_PACKAGES, Collections.emptySet()));

            List<AppItem> loadedList = new ArrayList<>();
            for (ApplicationInfo ai : installed) {
                if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                if (getPackageName().equals(ai.packageName)) continue;

                String label = sLabelCache.get(ai.packageName);
                Drawable.ConstantState iconState = sIconCache.get(ai.packageName);
                Drawable icon = iconState != null ? iconState.newDrawable() : null;
                if (label == null || icon == null) {
                    try {
                        label = pm.getApplicationLabel(ai).toString();
                        Drawable rawIcon = pm.getApplicationIcon(ai);
                        sLabelCache.put(ai.packageName, label);
                        if (rawIcon.getConstantState() != null) {
                            sIconCache.put(ai.packageName, rawIcon.getConstantState());
                            icon = rawIcon.getConstantState().newDrawable();
                        } else {
                            icon = rawIcon;
                        }
                    } catch (Throwable ignored) {
                        continue;
                    }
                }

                boolean isVip = currentVips.contains(ai.packageName);
                loadedList.add(new AppItem(label, ai.packageName, icon, isVip));
            }

            loadedList.sort((a, b) -> {
                if (a.isVip != b.isVip) return a.isVip ? -1 : 1;
                return a.appName.compareToIgnoreCase(b.appName);
            });

            runOnUiThread(() -> {
                appItems.clear();
                appItems.addAll(loadedList);
                adapter.filter(etSearch.getText() != null ? etSearch.getText().toString() : "");
                pb.setVisibility(View.GONE);
                rv.setVisibility(View.VISIBLE);
            });
        });

        dialogView.findViewById(R.id.btnCancelVip).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnSaveVip).setOnClickListener(v -> {
            HashSet<String> newVips = new HashSet<>();
            for (AppItem item : appItems) {
                if (item.isVip) {
                    newVips.add(item.packageName);
                }
            }
            prefs.edit().putStringSet(KEY_VIP_PACKAGES, newVips).apply();
            String joined = String.join(",", newVips);
            RootTool.setProp("persist.hchen.adj.vip_packages", joined);
            updateVipSummary();
            Toast.makeText(this, "Saved " + newVips.size() + " Keep-Alive apps (ADJ 200)", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            refreshRunningProcesses();
        });

        dialog.show();
    }
}
