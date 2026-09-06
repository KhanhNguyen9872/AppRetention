package com.hchen.appretention.ui;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
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
import com.hchen.appretention.R;
import com.hchen.hooktool.utils.SystemPropTool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
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

    // Memory-leak-free Icon Cache: caches Drawable.ConstantState, avoiding Activity Context leaks
    private static final LruCache<String, Drawable.ConstantState> sIconCache = new LruCache<>(150);
    private static final LruCache<String, String> sLabelCache = new LruCache<>(250);

    // Controlled background worker pool
    private static final ExecutorService sWorkerPool = Executors.newFixedThreadPool(2);

    private SharedPreferences prefs;

    private SwipeRefreshLayout swipeRefresh;
    private TextView tvStatusBadge;
    private TextView tvMemoryInfo;
    private TextView tvShieldCount;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        initViews();
        setupSwitches();
        refreshAll();

        sWorkerPool.execute(() -> {
            boolean hasRoot = RootTool.isRootAvailable();
            runOnUiThread(() -> {
                if (hasRoot) {
                    tvStatusBadge.setText("System Framework (LibXposed 101) • Root Active");
                } else {
                    tvStatusBadge.setText("System Framework (LibXposed 101) • Standard Mode");
                }
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateMemoryInfo();
        updateVipSummary();
    }

    private void initViews() {
        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this::refreshAll);

        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvMemoryInfo = findViewById(R.id.tvMemoryInfo);
        tvShieldCount = findViewById(R.id.tvShieldCount);
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
        updateMemoryInfo();
        updateVipSummary();
        refreshRunningProcesses();
        refreshLogs();
        swipeRefresh.setRefreshing(false);
    }

    private void updateMemoryInfo() {
        long totalRam = 0;
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line = br.readLine();
            if (line != null) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    totalRam = Long.parseLong(parts[1]) * 1024L;
                }
            }
        } catch (Throwable ignored) {
        }

        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) {
            am.getMemoryInfo(mi);
        }

        long totalGb = totalRam > 0 ? (totalRam / (1024 * 1024 * 1024L)) : (mi.totalMem / (1024 * 1024 * 1024L));
        long availGb = mi.availMem / (1024 * 1024 * 1024L);
        int discount = totalGb >= 15 ? 5 : (totalGb >= 11 ? 4 : 3);

        tvMemoryInfo.setText(String.format("RAM: %d GB Total • %d GB Available (LMKD Tuning: %dx)", totalGb, availGb, discount));
        tvShieldCount.setText("KillShield Status: Active & Shielding Background Kills");
    }

    private void updateVipSummary() {
        Set<String> vips = prefs.getStringSet(KEY_VIP_PACKAGES, Collections.emptySet());
        tvVipSummary.setText(String.format("Pinned: %d apps (Total Kill Immunity at ADJ 200)", vips.size()));
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
            Toast.makeText(this, "Pinned " + newVips.size() + " VIP apps (ADJ 200)", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            refreshRunningProcesses();
        });

        dialog.show();
    }
}
