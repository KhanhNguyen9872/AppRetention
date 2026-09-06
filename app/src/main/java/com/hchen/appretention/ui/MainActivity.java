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

        // Remove legacy root/Xposed traces in /data/system/AppRetention
        RootTool.cleanLegacyTraces();

        initViews();
        setupSwitches();
        checkAndPromptRoot();
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
        if (tvModeDetail != null) {
            tvModeDetail.setOnClickListener(v -> {
                if (!RootTool.isRootAvailable()) {
                    checkAndPromptRoot();
                } else {
                Toast.makeText(this, R.string.toast_root_active, Toast.LENGTH_SHORT).show();
                }
            });
        }

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
        processAdapter.setOnProcessKillListener((item, position) -> {
            RootTool.killProcess(item.pid, item.packageName);
            try {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    String pkg = item.packageName;
                    if (pkg.contains(":")) {
                        pkg = pkg.substring(0, pkg.indexOf(':'));
                    }
                    am.killBackgroundProcesses(pkg);
                }
            } catch (Throwable ignored) {}

            Toast.makeText(MainActivity.this, getString(R.string.toast_killed_process, item.appName), Toast.LENGTH_SHORT).show();
            processAdapter.removeItem(position);
            tvProcessCount.setText(getString(R.string.format_process_count, processAdapter.getItemCount()));
            mTimerHandler.postDelayed(this::refreshRunningProcesses, 1000);
        });
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

    private void checkAndPromptRoot() {
        if (tvModeDetail != null) {
            tvModeDetail.setText(getString(R.string.status_root_requesting));
        }
        sWorkerPool.execute(() -> {
            boolean hasRoot = RootTool.requestRoot();
            runOnUiThread(() -> {
                if (hasRoot) {
                    tvModeDetail.setText(getString(R.string.status_root_active));
                    tvModeDetail.setTextColor(0xFF22C55E);
                } else {
                    tvModeDetail.setText(getString(R.string.status_root_limited));
                    tvModeDetail.setTextColor(0xFFF59E0B);
                    showRootExplanationDialog();
                }
                refreshAll();
            });
        });
    }

    private void showRootExplanationDialog() {
        if (isFinishing() || isDestroyed()) return;

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_root_title)
            .setMessage(R.string.dialog_root_message)
            .setPositiveButton(R.string.btn_dialog_retry_root, (dialog, which) -> {
                RootTool.resetRootCheck();
                checkAndPromptRoot();
            })
            .setNegativeButton(R.string.btn_dialog_continue_limited, (dialog, which) -> dialog.dismiss())
            .setCancelable(true)
            .show();
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
            // Read all root hardware metrics in a single high-performance atomic query
            RootTool.HardwareStats rootStats = RootTool.getHardwareStats();

            // 1. RAM Calculation (Root /proc/meminfo with 100% precision, fallback to ActivityManager)
            long totalRamBytes = 0;
            long availRamBytes = 0;
            if (rootStats != null && rootStats.memTotalBytes > 0) {
                totalRamBytes = rootStats.memTotalBytes;
                availRamBytes = rootStats.memAvailableBytes;
            } else {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                if (am != null) am.getMemoryInfo(mi);
                totalRamBytes = mi.totalMem;
                availRamBytes = mi.availMem;
            }

            long usedRamBytes = Math.max(0, totalRamBytes - availRamBytes);
            int ramPercent = (int) Math.min(100, (usedRamBytes * 100) / (totalRamBytes > 0 ? totalRamBytes : 1));
            double usedRamGb = usedRamBytes / (1024.0 * 1024.0 * 1024.0);
            double totalRamGb = totalRamBytes / (1024.0 * 1024.0 * 1024.0);
            double availRamGb = availRamBytes / (1024.0 * 1024.0 * 1024.0);
            int discount = (int) Math.round(totalRamGb) >= 15 ? 5 : ((int) Math.round(totalRamGb) >= 11 ? 4 : 3);

            // 2. CPU Calculation (Root /proc/stat atomic reading)
            int cpuPercent = readCpuUsage(rootStats != null ? rootStats.cpuLine : null);
            int cores = Runtime.getRuntime().availableProcessors();

            // 3. Storage Calculation (Root df /data filesystem statistics, fallback to StatFs)
            long totalStorageBytes = 0;
            long usedStorageBytes = 0;
            long freeStorageBytes = 0;
            if (rootStats != null && rootStats.storageTotalBytes > 0) {
                totalStorageBytes = rootStats.storageTotalBytes;
                usedStorageBytes = rootStats.storageUsedBytes;
                freeStorageBytes = rootStats.storageAvailableBytes;
            } else {
                try {
                    File dataDir = Environment.getDataDirectory();
                    StatFs stat = new StatFs(dataDir.getPath());
                    long blockSize = stat.getBlockSizeLong();
                    long totalBlocks = stat.getBlockCountLong();
                    long availBlocks = stat.getAvailableBlocksLong();
                    totalStorageBytes = totalBlocks * blockSize;
                    freeStorageBytes = availBlocks * blockSize;
                    usedStorageBytes = Math.max(0, totalStorageBytes - freeStorageBytes);
                } catch (Throwable ignored) {}
            }

            int storagePercent = (int) Math.min(100, (usedStorageBytes * 100) / (totalStorageBytes > 0 ? totalStorageBytes : 1));
            double usedStorageGb = usedStorageBytes / (1024.0 * 1024.0 * 1024.0);
            double totalStorageGb = totalStorageBytes / (1024.0 * 1024.0 * 1024.0);
            double freeStorageGb = freeStorageBytes / (1024.0 * 1024.0 * 1024.0);

            runOnUiThread(() -> {
                // Update RAM
                tvRamDetails.setText(getString(R.string.format_ram_details, usedRamGb, totalRamGb, ramPercent));
                tvRamSubtext.setText(getString(R.string.format_ram_subtext, availRamGb, discount));
                pbRam.setProgress(ramPercent);

                // Update CPU
                if (cpuPercent >= 0) {
                    tvCpuDetails.setText(getString(R.string.format_cpu_details, cpuPercent));
                    tvCpuSubtext.setText(getString(R.string.format_cpu_subtext, cores));
                    pbCpu.setProgress(cpuPercent);
                } else {
                    tvCpuDetails.setText("N/A");
                    tvCpuSubtext.setText(getString(R.string.format_cpu_root_required, cores));
                    pbCpu.setProgress(0);
                }

                // Update Storage
                tvStorageDetails.setText(getString(R.string.format_storage_details, usedStorageGb, totalStorageGb, storagePercent));
                tvStorageSubtext.setText(getString(R.string.format_storage_subtext, freeStorageGb));
                pbStorage.setProgress(storagePercent);

                tvShieldCount.setText(getString(R.string.status_killshield_active));
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

    private int readCpuUsage(String prefetchedCpuLine) {
        String line = prefetchedCpuLine != null ? prefetchedCpuLine : getCpuStatLine();
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
        tvVipSummary.setText(getString(R.string.format_keep_alive_summary, vips.size()));
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
                            boolean isPinned = vipSet.contains(pkg);
                            boolean isUserApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
                            boolean isUpdatedSystem = (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                            boolean hasLauncher = false;
                            try {
                                hasLauncher = pm.getLaunchIntentForPackage(pkg) != null;
                            } catch (Throwable ignored) {}

                            if (!isPinned && !isUserApp && !isUpdatedSystem && !hasLauncher) {
                                continue;
                            }

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

            items.sort((a, b) -> Integer.compare(a.adj, b.adj));

            runOnUiThread(() -> {
                processAdapter.updateList(items);
                tvProcessCount.setText(getString(R.string.format_process_count, items.size()));
            });
        });
    }

    private void refreshLogs() {
        StringBuilder sb = new StringBuilder();
        File[] candidateDirs = new File[]{
                new File("/data/user_de/0/com.hchen.appretention/files/logs"),
                new File(getFilesDir(), "logs"),
                new File(getExternalFilesDir(null), "logs")
        };
        boolean hasLogs = false;
        for (File logDir : candidateDirs) {
            if (logDir.exists() && logDir.isDirectory()) {
                File[] files = logDir.listFiles();
                if (files != null && files.length > 0) {
                    for (File f : files) {
                        if (f.isFile() && f.getName().endsWith(".log")) {
                            try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    sb.append(line).append("\n");
                                    hasLogs = true;
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
        }

        if (hasLogs) {
            tvLogContent.setText(sb.toString());
        } else {
            tvLogContent.setText("No Hook logs recorded yet. Logs will appear here as Android system services run.");
        }
    }

    private void clearLogFiles() {
        File[] candidateDirs = new File[]{
                new File("/data/user_de/0/com.hchen.appretention/files/logs"),
                new File(getExternalFilesDir(null), "logs"),
                new File(getFilesDir(), "logs")
        };
        for (File dir : candidateDirs) {
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
            Toast.makeText(this, getString(R.string.toast_saved_keep_alive_apps, newVips.size()), Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            refreshRunningProcesses();
        });

        dialog.show();
    }
}
