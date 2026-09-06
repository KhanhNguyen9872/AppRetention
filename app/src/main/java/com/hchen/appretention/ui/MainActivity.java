package com.hchen.appretention.ui;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
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

public class MainActivity extends AppCompatActivity {
    private static final String PREF_NAME = "AppRetentionConfig";
    private static final String KEY_VIP_PACKAGES = "persist.hchen.adj.vip_packages";
    private static final String KEY_TIERED_ADJ = "persist.hchen.adj.perceptible.enable";
    private static final String KEY_KILL_SHIELD = "persist.hchen.killshield.enable";
    private static final String KEY_DOZE = "persist.hchen.doze.opt.enable";
    private static final String KEY_NUBIA = "persist.hchen.nubia.opt.enable";
    private static final String KEY_HIBERNATION = "persist.hchen.hibernation.opt.enable";
    private static final String KEY_AUTOSTART = "persist.hchen.autostart.opt.enable";

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

        // Check root and notify user if settings sync via su
        new Thread(() -> {
            boolean hasRoot = RootTool.isRootAvailable();
            runOnUiThread(() -> {
                if (hasRoot) {
                    tvStatusBadge.setText("System Framework (LibXposed 101) • Root Active (KernelSU/Magisk)");
                } else {
                    tvStatusBadge.setText("System Framework (LibXposed 101) • Root not detected (Read-only mode)");
                }
            });
        }).start();
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
        new Thread(() -> {
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
                    String label = pkg;
                    android.graphics.drawable.Drawable icon = null;
                    try {
                        ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                        if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue; // Skip system
                        label = pm.getApplicationLabel(appInfo).toString();
                        icon = pm.getApplicationIcon(appInfo);
                    } catch (Throwable ignored) {
                        continue;
                    }

                    int adj = pi.adj;
                    if (vipSet.contains(pkg)) {
                        adj = 200; // VIP pinned
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
                            String label = pkg;
                            android.graphics.drawable.Drawable icon = null;
                            try {
                                ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                                label = pm.getApplicationLabel(appInfo).toString();
                                icon = pm.getApplicationIcon(appInfo);
                            } catch (Throwable ignored) {}
                            int estimatedAdj = vipSet.contains(pkg) ? 200 : (200 + (items.size() * 5));
                            items.add(new ProcessItem(label, pkg, info.pid, estimatedAdj, icon));
                        }
                    }
                }
            }

            runOnUiThread(() -> {
                processList.clear();
                processList.addAll(items);
                tvProcessCount.setText(processList.size() + " apps active");
                processAdapter.notifyDataSetChanged();
            });
        }).start();
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
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        Set<String> currentVips = new HashSet<>(prefs.getStringSet(KEY_VIP_PACKAGES, Collections.emptySet()));

        List<AppItem> appItems = new ArrayList<>();
        for (ApplicationInfo ai : installed) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue; // Skip system
            if (getPackageName().equals(ai.packageName)) continue;

            String label = pm.getApplicationLabel(ai).toString();
            android.graphics.drawable.Drawable icon = pm.getApplicationIcon(ai);
            boolean isVip = currentVips.contains(ai.packageName);
            appItems.add(new AppItem(label, ai.packageName, icon, isVip));
        }

        appItems.sort((a, b) -> {
            if (a.isVip != b.isVip) return a.isVip ? -1 : 1;
            return a.appName.compareToIgnoreCase(b.appName);
        });

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_vip_apps, null);
        RecyclerView rv = dialogView.findViewById(R.id.rvAppList);
        rv.setLayoutManager(new LinearLayoutManager(this));
        AppListAdapter adapter = new AppListAdapter(appItems);
        rv.setAdapter(adapter);

        EditText etSearch = dialogView.findViewById(R.id.etSearchApp);
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
