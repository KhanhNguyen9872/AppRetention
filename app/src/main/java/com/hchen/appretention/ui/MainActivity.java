package com.hchen.appretention.ui;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import android.content.ContentValues;
import android.provider.MediaStore;
import android.widget.ScrollView;
import java.io.FileWriter;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.os.BatteryManager;
import android.content.pm.ResolveInfo;
import androidx.core.content.ContextCompat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
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
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.hchen.appretention.BuildConfig;
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
    private static final String KEY_RESTRICT_PACKAGES = "persist.hchen.restrict.packages";
    private static final String KEY_RESTRICT_IMMEDIATE = "persist.hchen.restrict.immediate_kill";
    private static final String KEY_TIERED_ADJ = "persist.hchen.adj.perceptible.enable";
    private static final String KEY_KILL_SHIELD = "persist.hchen.killshield.enable";
    private static final String KEY_DOZE = "persist.hchen.doze.opt.enable";
    private static final String KEY_NUBIA = "persist.hchen.nubia.opt.enable";
    private static final String KEY_HIBERNATION = "persist.hchen.hibernation.opt.enable";
    private static final String KEY_AUTOSTART = "persist.hchen.autostart.opt.enable";
    private static final String PACKAGE_APPRETENTION = "com.hchen.appretention";

    // Memory-leak-free Caches
    private static final LruCache<String, Drawable.ConstantState> sIconCache = new LruCache<>(150);
    private static final LruCache<String, String> sLabelCache = new LruCache<>(250);

    // Background worker thread pool
    private static final ExecutorService sWorkerPool = Executors.newFixedThreadPool(2);

    private SharedPreferences prefs;

    // Navigation & Page Containers
    private BottomNavigationView bottomNavigation;
    private View pageDashboard;
    private View pageKeepAlive;
    private View pageControls;
    private View pageLogs;

    // Tab 1: Dashboard UI elements
    private SwipeRefreshLayout swipeRefreshDashboard;
    private TextView tvStatusBadge;
    private TextView tvHookScope;
    private TextView tvShieldStatus;

    private TextView tvRamDetails;
    private TextView tvRamSubtext;
    private LinearProgressIndicator pbRamUsage;

    private TextView tvCpuDetails;
    private TextView tvCpuSubtext;
    private LinearProgressIndicator pbCpuUsage;

    private TextView tvStorageDetails;
    private TextView tvStorageSubtext;
    private LinearProgressIndicator pbStorageUsage;

    private TextView tvGpuDetails;
    private com.google.android.material.progressindicator.LinearProgressIndicator pbGpuUsage;
    private TextView tvDisplaySubtext;
    private TextView tvBatteryDetails;
    private TextView tvBatterySubtext;
    private TextView tvPlatformSubtext;
    private LinearProgressIndicator pbBatteryUsage;

    static class AppMeta {
        final String label;
        final Drawable.ConstantState iconState;
        final boolean isSystemApp;
        final int uid;
        final String versionName;
        final boolean isVisible;

        AppMeta(String label, Drawable.ConstantState iconState, boolean isSystemApp, int uid, String versionName, boolean isVisible) {
            this.label = label;
            this.iconState = iconState;
            this.isSystemApp = isSystemApp;
            this.uid = uid;
            this.versionName = versionName;
            this.isVisible = isVisible;
        }
    }

    private static final Map<String, AppMeta> sMetaCache = new ConcurrentHashMap<>();
    private static volatile Set<String> sLaunchablePackages = null;

    private Set<String> getLaunchablePackages(PackageManager pm) {
        Set<String> set = sLaunchablePackages;
        if (set == null) {
            set = new HashSet<>();
            try {
                Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> list = pm.queryIntentActivities(mainIntent, 0);
                for (ResolveInfo ri : list) {
                    if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                        set.add(ri.activityInfo.packageName);
                    }
                }
            } catch (Throwable ignored) {}
            sLaunchablePackages = set;
        }
        return set;
    }

    private TextView tvProcessCount;
    private View layoutProcessesLoading;
    private final java.util.concurrent.atomic.AtomicBoolean isScanningProcesses = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean isRefreshingHardware = new java.util.concurrent.atomic.AtomicBoolean(false);
    private RecyclerView rvProcesses;
    private ProcessAdapter processAdapter;
    private final List<ProcessItem> processList = new ArrayList<>();

    // Tab 2: Keep-Alive & Restricted Dedicated Page UI elements
    private MaterialButtonToggleGroup toggleAppMode;
    private MaterialButton btnModeKeepAlive;
    private MaterialButton btnModeRestricted;
    private TextView tvKeepAliveSummaryCount;
    private TextView tvBadgeMode;
    private TextView tvModeDesc;
    private EditText etSearchKeepAlive;
    private ImageView btnClearSearch;
    private ChipGroup chipGroupFilter;
    private Chip chipFilterUser;
    private Chip chipFilterSystem;
    private Chip chipAllApps;
    private Chip chipActiveOnly;
    private ProgressBar pbLoadingKeepAlive;
    private RecyclerView rvKeepAliveApps;
    private KeepAliveAdapter keepAliveAdapter;
    private final List<AppItem> allInstalledApps = new ArrayList<>();
    private boolean isAppsLoaded = false;

    // Tab 3: Settings UI elements
    private MaterialSwitch switchImmediateKill;
    private MaterialSwitch switchTieredAdj;
    private MaterialSwitch switchKillShield;
    private MaterialSwitch switchDoze;
    private MaterialSwitch switchNubia;
    private MaterialSwitch switchHibernation;
    private MaterialSwitch switchAutoStart;

    // Tab 4: Logs UI elements
    private TextView tvLogContent;
    private ScrollView scrollLogContent;
    private MaterialButton btnRefreshLog;
    private MaterialButton btnSaveLog;
    private MaterialButton btnCopyLog;
    private MaterialButton btnClearLog;

    // 5-second recurring auto-refresh handler for Dashboard (active only when Dashboard is visible)
    private final Handler mTimerHandler = new Handler(Looper.getMainLooper());
    private final Runnable mPeriodicRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (pageDashboard != null && pageDashboard.getVisibility() == View.VISIBLE) {
                updateHardwareStats();
                refreshRunningProcesses();
            }
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
        setupNavigation();
        setupSwitches();
        checkAndPromptRoot();

        // AppRetention is a mandatory restricted package: never boost or keep its UI alive.
        Set<String> vips = new HashSet<>(prefs.getStringSet(KEY_VIP_PACKAGES, Collections.emptySet()));
        Set<String> restricted = new HashSet<>(prefs.getStringSet(KEY_RESTRICT_PACKAGES, Collections.emptySet()));
        enforceAppRetentionRestriction(vips, restricted);
        persistPolicySets(vips, restricted);
        syncPolicyFiles(vips, restricted);
        syncImmediateKillFile(prefs.getBoolean(KEY_RESTRICT_IMMEDIATE, false));
    }

    @Override
    protected void onResume() {
        super.onResume();
        mTimerHandler.removeCallbacks(mPeriodicRefreshRunnable);
        mTimerHandler.post(mPeriodicRefreshRunnable);
        updateKeepAliveHeader();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mTimerHandler.removeCallbacks(mPeriodicRefreshRunnable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mTimerHandler.removeCallbacksAndMessages(null);
        if (!isChangingConfigurations()) {
            // The configuration UI must never remain as a background process. Xposed hooks
            // run inside their target processes and remain active independently of this UI.
            finishAndRemoveTask();
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mTimerHandler.removeCallbacksAndMessages(null);
        if (isFinishing()) {
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        }
    }

    private void initViews() {
        // Top Toolbar
        TextView tvAppVersion = findViewById(R.id.tvAppVersion);
        if (tvAppVersion != null) {
            String ver = BuildConfig.VERSION_NAME;
            if (ver != null && !ver.isEmpty()) {
                if (!ver.startsWith("v") && !ver.startsWith("V")) {
                    ver = "v" + ver;
                }
                tvAppVersion.setText(ver);
            }
        }

        MaterialButton btnGithub = findViewById(R.id.btnGithub);
        if (btnGithub != null) {
            btnGithub.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/KhanhNguyen9872/AppRetentionFork"));
                    startActivity(intent);
                } catch (Throwable t) {
                    Toast.makeText(this, "Could not open browser", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Page Containers & Tab 1: Dashboard Initialization
        swipeRefreshDashboard = findViewById(R.id.swipeRefreshDashboard);
        pageDashboard = swipeRefreshDashboard;
        pageKeepAlive = findViewById(R.id.pageKeepAlive);
        pageControls = findViewById(R.id.pageControls);
        pageLogs = findViewById(R.id.pageLogs);
        bottomNavigation = findViewById(R.id.bottomNavigation);

        if (swipeRefreshDashboard != null) {
            swipeRefreshDashboard.setColorSchemeColors(0xFF38BDF8, 0xFF22C55E, 0xFFF59E0B);
            swipeRefreshDashboard.setProgressBackgroundColorSchemeColor(0xFF151C2C);
            swipeRefreshDashboard.setOnRefreshListener(() -> {
                updateHardwareStats();
                refreshRunningProcesses();
                swipeRefreshDashboard.postDelayed(() -> {
                    if (swipeRefreshDashboard != null && swipeRefreshDashboard.isRefreshing()) {
                        swipeRefreshDashboard.setRefreshing(false);
                    }
                }, 2500);
            });
        }

        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvHookScope = findViewById(R.id.tvHookScope);
        tvShieldStatus = findViewById(R.id.tvShieldStatus);
        if (tvHookScope != null) {
            tvHookScope.setOnClickListener(v -> {
                if (!RootTool.isRootAvailable()) {
                    checkAndPromptRoot();
                } else {
                    Toast.makeText(this, R.string.toast_root_active, Toast.LENGTH_SHORT).show();
                }
            });
        }

        tvRamDetails = findViewById(R.id.tvRamDetails);
        tvRamSubtext = findViewById(R.id.tvRamSubtext);
        pbRamUsage = findViewById(R.id.pbRamUsage);

        tvCpuDetails = findViewById(R.id.tvCpuDetails);
        tvCpuSubtext = findViewById(R.id.tvCpuSubtext);
        pbCpuUsage = findViewById(R.id.pbCpuUsage);

        tvStorageDetails = findViewById(R.id.tvStorageDetails);
        tvStorageSubtext = findViewById(R.id.tvStorageSubtext);
        pbStorageUsage = findViewById(R.id.pbStorageUsage);

        tvGpuDetails = findViewById(R.id.tvGpuDetails);
        pbGpuUsage = findViewById(R.id.pbGpuUsage);
        tvDisplaySubtext = findViewById(R.id.tvDisplaySubtext);
        tvBatteryDetails = findViewById(R.id.tvBatteryDetails);
        tvBatterySubtext = findViewById(R.id.tvBatterySubtext);
        tvPlatformSubtext = findViewById(R.id.tvPlatformSubtext);
        pbBatteryUsage = findViewById(R.id.pbBatteryUsage);

        sWorkerPool.execute(() -> HardwareInfo.init(getApplicationContext()));

        tvProcessCount = findViewById(R.id.tvProcessCount);
        layoutProcessesLoading = findViewById(R.id.layoutProcessesLoading);
        rvProcesses = findViewById(R.id.rvProcesses);
        if (rvProcesses != null) {
            rvProcesses.setLayoutManager(new LinearLayoutManager(this));
            processAdapter = new ProcessAdapter(processList);
            processAdapter.setOnProcessClickListener((item, position) -> showProcessDetailsDialog(item, position));
            processAdapter.setPolicyPackages(
                prefs.getStringSet(KEY_VIP_PACKAGES, Collections.emptySet()),
                prefs.getStringSet(KEY_RESTRICT_PACKAGES, Collections.emptySet())
            );
            rvProcesses.setAdapter(processAdapter);
        }

        // --- Tab 2: Keep-Alive & Restricted Page Initialization ---
        toggleAppMode = findViewById(R.id.toggleAppMode);
        btnModeKeepAlive = findViewById(R.id.btnModeKeepAlive);
        btnModeRestricted = findViewById(R.id.btnModeRestricted);
        tvKeepAliveSummaryCount = findViewById(R.id.tvKeepAliveSummaryCount);
        tvBadgeMode = findViewById(R.id.tvBadgeMode);
        tvModeDesc = findViewById(R.id.tvModeDesc);
        etSearchKeepAlive = findViewById(R.id.etSearchKeepAlive);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        chipGroupFilter = findViewById(R.id.chipGroupFilter);
        chipFilterUser = findViewById(R.id.chipFilterUser);
        chipFilterSystem = findViewById(R.id.chipFilterSystem);
        chipAllApps = findViewById(R.id.chipAllApps);
        chipActiveOnly = findViewById(R.id.chipActiveOnly);
        pbLoadingKeepAlive = findViewById(R.id.pbLoadingKeepAlive);
        rvKeepAliveApps = findViewById(R.id.rvKeepAliveApps);

        if (toggleAppMode != null) {
            toggleAppMode.check(R.id.btnModeKeepAlive);
            toggleAppMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (isChecked) {
                    int mode = (checkedId == R.id.btnModeRestricted) ? KeepAliveAdapter.MODE_RESTRICTED : KeepAliveAdapter.MODE_KEEP_ALIVE;
                    if (keepAliveAdapter != null) {
                        keepAliveAdapter.setMode(mode);
                    }
                    updateKeepAliveHeader();
                }
            });
        }

        if (rvKeepAliveApps != null) {
            rvKeepAliveApps.setLayoutManager(new LinearLayoutManager(this));
            keepAliveAdapter = new KeepAliveAdapter(allInstalledApps);
            keepAliveAdapter.setOnAppStateChangeListener(new KeepAliveAdapter.OnAppStateChangeListener() {
                @Override
                public void onAppStateChanged(AppItem item, int mode, boolean enabled, int position) {
                    applyAppStateChange(item, mode, enabled);
                }

                @Override
                public void onSystemAppRestrictedRequested(AppItem item, int position) {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                            .setTitle(R.string.dialog_system_app_restrict_title)
                            .setMessage(getString(R.string.dialog_system_app_restrict_message, item.appName))
                            .setPositiveButton(R.string.btn_yes_continue, (dialog, which) -> {
                                item.isRestricted = true;
                                item.isVip = false;
                                if (keepAliveAdapter != null) keepAliveAdapter.notifyItemChanged(position);
                                applyAppStateChange(item, KeepAliveAdapter.MODE_RESTRICTED, true);
                            })
                            .setNegativeButton(R.string.btn_no_cancel, (dialog, which) -> {
                                item.isRestricted = false;
                                if (keepAliveAdapter != null) keepAliveAdapter.notifyItemChanged(position);
                            })
                            .setCancelable(false)
                            .show();
                }

                @Override
                public void onRestrictedItemClickedInKeepAlive(AppItem item) {
                    if (item != null && PACKAGE_APPRETENTION.equals(item.packageName)) {
                        Toast.makeText(MainActivity.this, R.string.toast_appretention_restriction_locked, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                            .setTitle(R.string.dialog_quick_switch_title)
                            .setMessage(getString(R.string.dialog_quick_switch_message, item.appName))
                            .setPositiveButton(R.string.btn_quick_switch_confirm, (dialog, which) -> {
                                item.isRestricted = false;
                                item.isVip = true;
                                if (keepAliveAdapter != null) keepAliveAdapter.notifyDataSetChanged();
                                applyAppStateChange(item, KeepAliveAdapter.MODE_KEEP_ALIVE, true);
                            })
                            .setNegativeButton(R.string.btn_no_cancel, null)
                            .show();
                }
            });
            rvKeepAliveApps.setAdapter(keepAliveAdapter);
        }

        if (etSearchKeepAlive != null) {
            etSearchKeepAlive.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String query = s != null ? s.toString() : "";
                    if (btnClearSearch != null) {
                        btnClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                    if (keepAliveAdapter != null) {
                        keepAliveAdapter.setQuery(query);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        if (btnClearSearch != null) {
            btnClearSearch.setOnClickListener(v -> {
                if (etSearchKeepAlive != null) {
                    etSearchKeepAlive.setText("");
                }
            });
        }

        if (chipGroupFilter != null) {
            chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (keepAliveAdapter != null) {
                    if (checkedIds.contains(R.id.chipFilterUser)) {
                        keepAliveAdapter.setFilterType(KeepAliveAdapter.FILTER_USER_ONLY);
                    } else if (checkedIds.contains(R.id.chipActiveOnly)) {
                        keepAliveAdapter.setFilterType(KeepAliveAdapter.FILTER_ACTIVE_ONLY);
                    } else if (checkedIds.contains(R.id.chipFilterSystem)) {
                        keepAliveAdapter.setFilterType(KeepAliveAdapter.FILTER_SYSTEM_ONLY);
                    } else {
                        keepAliveAdapter.setFilterType(KeepAliveAdapter.FILTER_ALL);
                    }
                }
            });
        }

        // --- Tab 3: Controls Page Initialization ---
        switchTieredAdj = findViewById(R.id.switchTieredAdj);
        switchKillShield = findViewById(R.id.switchKillShield);
        switchDoze = findViewById(R.id.switchDoze);
        switchNubia = findViewById(R.id.switchNubia);
        switchHibernation = findViewById(R.id.switchHibernation);
        switchAutoStart = findViewById(R.id.switchAutoStart);

        // --- Tab 4: Logs Page Initialization ---
        tvLogContent = findViewById(R.id.tvLogContent);
        scrollLogContent = findViewById(R.id.scrollLogContent);

        btnRefreshLog = findViewById(R.id.btnRefreshLog);
        if (btnRefreshLog != null) {
            btnRefreshLog.setOnClickListener(v -> {
                refreshLogs();
                Toast.makeText(this, R.string.btn_refresh_log, Toast.LENGTH_SHORT).show();
            });
        }

        btnSaveLog = findViewById(R.id.btnSaveLog);
        if (btnSaveLog != null) {
            btnSaveLog.setOnClickListener(v -> saveLogToFile());
        }

        btnCopyLog = findViewById(R.id.btnCopyLog);
        if (btnCopyLog != null) {
            btnCopyLog.setOnClickListener(v -> {
                if (tvLogContent != null) {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        ClipData clip = ClipData.newPlainText("AppRetention Logs", tvLogContent.getText().toString());
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(this, R.string.toast_log_copied, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        btnClearLog = findViewById(R.id.btnClearLog);
        if (btnClearLog != null) {
            btnClearLog.setOnClickListener(v -> clearLogFiles());
        }
    }

    private void setupNavigation() {
        if (bottomNavigation != null) {
            bottomNavigation.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_dashboard) {
                    switchTab(0);
                    return true;
                } else if (id == R.id.nav_keep_alive) {
                    switchTab(1);
                    return true;
                } else if (id == R.id.nav_controls) {
                    switchTab(2);
                    return true;
                } else if (id == R.id.nav_logs) {
                    switchTab(3);
                    return true;
                }
                return false;
            });
        }
        // Start on Dashboard
        switchTab(0);
    }

    private void switchTab(int tabIndex) {
        if (pageDashboard != null) pageDashboard.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
        if (pageKeepAlive != null) pageKeepAlive.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);
        if (pageControls != null) pageControls.setVisibility(tabIndex == 2 ? View.VISIBLE : View.GONE);
        if (pageLogs != null) pageLogs.setVisibility(tabIndex == 3 ? View.VISIBLE : View.GONE);

        if (tabIndex == 0) {
            updateHardwareStats();
            refreshRunningProcesses();
        } else if (tabIndex == 1) {
            if (!isAppsLoaded) {
                loadInstalledApps(false);
            } else {
                updateKeepAliveHeader();
            }
        } else if (tabIndex == 3) {
            refreshLogs();
        }
    }

    private void setupSwitches() {
        switchImmediateKill = findViewById(R.id.switchImmediateKill);
        bindSwitch(switchImmediateKill, KEY_RESTRICT_IMMEDIATE, false);
        bindSwitch(switchTieredAdj, KEY_TIERED_ADJ, true);
        bindSwitch(switchKillShield, KEY_KILL_SHIELD, true);
        bindSwitch(switchDoze, KEY_DOZE, true);
        bindSwitch(switchNubia, KEY_NUBIA, true);
        bindSwitch(switchHibernation, KEY_HIBERNATION, true);
        bindSwitch(switchAutoStart, KEY_AUTOSTART, true);
    }

    private void bindSwitch(MaterialSwitch sw, String key, boolean defValue) {
        if (sw == null) return;
        String propertyValue = SystemPropTool.getProp(key, "");
        boolean hasSavedChoice = prefs.contains(key);
        boolean propertyChoice = propertyValue.isEmpty() ? defValue : Boolean.parseBoolean(propertyValue);
        // Once the user has made a choice, app storage is authoritative. This prevents a
        // missing or stale vendor property from turning a switch back on after reboot.
        boolean val = hasSavedChoice ? prefs.getBoolean(key, defValue) : propertyChoice;
        prefs.edit().putBoolean(key, val).commit();
        sw.setChecked(val);
        // Seed or repair the persistent property without treating restoration as a new click.
        if (propertyValue.isEmpty() || propertyChoice != val) {
            RootTool.setBooleanPropVerified(key, val, null);
        }
        java.util.concurrent.atomic.AtomicBoolean internalUpdate = new java.util.concurrent.atomic.AtomicBoolean(false);
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (internalUpdate.get()) return;
            // Persist user intent synchronously before any asynchronous root work. The UI must
            // never jump back to its default merely because the process exits or root sync fails.
            boolean saved = prefs.edit().putBoolean(key, isChecked).commit();
            if (!saved) {
                internalUpdate.set(true);
                sw.setChecked(!isChecked);
                internalUpdate.set(false);
                Toast.makeText(this, R.string.toast_setting_save_failed, Toast.LENGTH_LONG).show();
                return;
            }
            if (KEY_RESTRICT_IMMEDIATE.equals(key)) {
                syncImmediateKillFile(isChecked);
                if (isChecked) refreshRunningProcesses();
            }
            sw.setEnabled(false);
            RootTool.setBooleanPropVerified(key, isChecked, success -> runOnUiThread(() -> {
                if (!success) {
                    // Keep the saved selection. bindSwitch retries synchronization next launch.
                    Toast.makeText(this, R.string.toast_setting_sync_deferred, Toast.LENGTH_LONG).show();
                }
                sw.setEnabled(true);
            }));
        });
    }

    private void checkAndPromptRoot() {
        if (tvHookScope != null) {
            tvHookScope.setText(R.string.status_root_requesting);
        }

        sWorkerPool.execute(() -> {
            boolean hasRoot = RootTool.requestRoot();
            runOnUiThread(() -> {
                if (hasRoot) {
                    if (tvHookScope != null) tvHookScope.setText(R.string.status_root_active);
                    if (tvStatusBadge != null) {
                        tvStatusBadge.setText(R.string.badge_status_active);
                        tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_green);
                        tvStatusBadge.setTextColor(0xFF22C55E);
                    }
                } else {
                    if (tvHookScope != null) tvHookScope.setText(R.string.status_root_limited);
                    if (tvStatusBadge != null) {
                        tvStatusBadge.setText("Limited (No Root)");
                        tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_amber);
                        tvStatusBadge.setTextColor(0xFFF59E0B);
                    }
                    showRootExplanationDialog();
                }
                updateHardwareStats();
                refreshRunningProcesses();
            });
        });
    }

    private void showRootExplanationDialog() {
        if (isFinishing() || isDestroyed()) return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_root_title)
                .setMessage(R.string.dialog_root_message)
                .setPositiveButton(R.string.btn_dialog_retry_root, (dialog, which) -> {
                    dialog.dismiss();
                    checkAndPromptRoot();
                })
                .setNegativeButton(R.string.btn_dialog_continue_limited, (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }

    private void applyAppStateChange(AppItem item, int mode, boolean enabled) {
        if (item == null || PACKAGE_APPRETENTION.equals(item.packageName)) {
            Toast.makeText(this, R.string.toast_appretention_restriction_locked, Toast.LENGTH_SHORT).show();
            return;
        }
        Set<String> vipSet = new HashSet<>(prefs.getStringSet(KEY_VIP_PACKAGES, Collections.emptySet()));
        Set<String> restrictSet = new HashSet<>(prefs.getStringSet(KEY_RESTRICT_PACKAGES, Collections.emptySet()));

        if (mode == KeepAliveAdapter.MODE_KEEP_ALIVE) {
            if (enabled) {
                vipSet.add(item.packageName);
                restrictSet.remove(item.packageName);
                item.isRestricted = false;
                Toast.makeText(this, getString(R.string.toast_keep_alive_added, item.appName), Toast.LENGTH_SHORT).show();
            } else {
                vipSet.remove(item.packageName);
                Toast.makeText(this, getString(R.string.toast_keep_alive_removed, item.appName), Toast.LENGTH_SHORT).show();
            }
        } else {
            if (enabled) {
                restrictSet.add(item.packageName);
                vipSet.remove(item.packageName);
                item.isVip = false;
                Toast.makeText(this, getString(R.string.toast_restricted_added, item.appName), Toast.LENGTH_SHORT).show();
            } else {
                restrictSet.remove(item.packageName);
                Toast.makeText(this, getString(R.string.toast_restricted_removed, item.appName), Toast.LENGTH_SHORT).show();
            }
        }

        enforceAppRetentionRestriction(vipSet, restrictSet);
        persistPolicySets(vipSet, restrictSet);

        syncPolicyFiles(vipSet, restrictSet);

        if (processAdapter != null) {
            processAdapter.setPolicyPackages(vipSet, restrictSet);
        }

        updateKeepAliveHeader();
        refreshRunningProcesses();
    }


    private void applyPolicyToPackage(String packageName, String appName, boolean keepAlive, boolean restricted) {
        if (PACKAGE_APPRETENTION.equals(packageName)) {
            Toast.makeText(this, R.string.toast_appretention_restriction_locked, Toast.LENGTH_SHORT).show();
            return;
        }
        Set<String> vipSet = new HashSet<>(prefs.getStringSet(KEY_VIP_PACKAGES, Collections.emptySet()));
        Set<String> restrictSet = new HashSet<>(prefs.getStringSet(KEY_RESTRICT_PACKAGES, Collections.emptySet()));

        if (keepAlive) {
            vipSet.add(packageName);
            restrictSet.remove(packageName);
            Toast.makeText(this, getString(R.string.toast_keep_alive_added, appName), Toast.LENGTH_SHORT).show();
        } else if (restricted) {
            restrictSet.add(packageName);
            vipSet.remove(packageName);
            Toast.makeText(this, getString(R.string.toast_restricted_added, appName), Toast.LENGTH_SHORT).show();
        } else {
            vipSet.remove(packageName);
            restrictSet.remove(packageName);
        }

        enforceAppRetentionRestriction(vipSet, restrictSet);
        persistPolicySets(vipSet, restrictSet);

        syncPolicyFiles(vipSet, restrictSet);

        if (processAdapter != null) {
            processAdapter.setPolicyPackages(vipSet, restrictSet);
        }

        if (keepAliveAdapter != null) {
            keepAliveAdapter.updatePackageState(packageName, keepAlive, restricted);
        }

        updateKeepAliveHeader();
        refreshRunningProcesses();
    }

    private void showProcessDetailsDialog(ProcessItem item, int position) {
        if (item == null || isFinishing() || isDestroyed()) return;

        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.sheet_process_details, null);
        sheet.setContentView(view);

        // 1. Header bindings
        ImageView ivIcon = view.findViewById(R.id.ivDetailIcon);
        TextView tvName = view.findViewById(R.id.tvDetailAppName);
        TextView tvType = view.findViewById(R.id.tvDetailAppType);
        TextView tvPackage = view.findViewById(R.id.tvDetailPackage);
        ImageView btnCopy = view.findViewById(R.id.btnCopyPackage);
        TextView tvProcName = view.findViewById(R.id.tvDetailProcessName);
        TextView tvVersion = view.findViewById(R.id.tvDetailVersion);

        if (item.icon != null) ivIcon.setImageDrawable(item.icon);
        tvName.setText(item.appName);
        final String basePkg = item.getBasePackageName();
        tvPackage.setText(basePkg);

        if (item.isSystemApp) {
            tvType.setText(R.string.sheet_type_system);
            tvType.setTextColor(0xFFEF4444);
            tvType.setBackgroundResource(R.drawable.bg_badge_red);
        } else {
            tvType.setText(R.string.sheet_type_user);
            tvType.setTextColor(0xFF38BDF8);
            tvType.setBackgroundResource(R.drawable.bg_badge_amber);
        }

        if (item.packageName != null && !item.packageName.equals(basePkg)) {
            tvProcName.setVisibility(View.VISIBLE);
            tvProcName.setText(item.packageName);
        } else {
            tvProcName.setVisibility(View.GONE);
        }

        if (item.versionName != null && !item.versionName.isEmpty()) {
            tvVersion.setText("v" + item.versionName);
            tvVersion.setVisibility(View.VISIBLE);
        } else {
            tvVersion.setVisibility(View.GONE);
        }

        btnCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                ClipData clip = ClipData.newPlainText("Package Name", basePkg);
                cm.setPrimaryClip(clip);
                Toast.makeText(this, getString(R.string.toast_copied_to_clipboard, basePkg), Toast.LENGTH_SHORT).show();
            }
        });

        // 2. Metrics bindings
        TextView tvPid = view.findViewById(R.id.tvDetailPid);
        TextView tvUid = view.findViewById(R.id.tvDetailUid);
        TextView tvRam = view.findViewById(R.id.tvDetailRam);
        TextView tvAdj = view.findViewById(R.id.tvDetailAdj);
        TextView tvStatusDesc = view.findViewById(R.id.tvDetailStatusDesc);

        tvPid.setText(String.valueOf(item.pid));
        tvUid.setText(item.uid > 0 ? String.valueOf(item.uid) : "N/A");
        tvRam.setText(item.getFormattedMemory());
        tvAdj.setText(String.valueOf(item.adj));

        // Helper to update status description based on current policy and adj
        Runnable updateStatusBanner = () -> {
            Set<String> vips = prefs.getStringSet(KEY_VIP_PACKAGES, Collections.emptySet());
            Set<String> restricted = prefs.getStringSet(KEY_RESTRICT_PACKAGES, Collections.emptySet());
            if (vips.contains(basePkg)) {
                tvStatusDesc.setText(getString(R.string.sheet_status_vip));
                tvStatusDesc.setTextColor(0xFF22C55E);
                tvAdj.setText("200");
                tvAdj.setTextColor(0xFF22C55E);
            } else if (restricted.contains(basePkg)) {
                tvStatusDesc.setText(getString(R.string.sheet_status_restricted));
                tvStatusDesc.setTextColor(0xFFEF4444);
                tvAdj.setText(String.valueOf(item.adj));
                tvAdj.setTextColor(0xFFEF4444);
            } else if (item.adj <= 0) {
                tvStatusDesc.setText(getString(R.string.sheet_status_foreground));
                tvStatusDesc.setTextColor(0xFF22C55E);
                tvAdj.setTextColor(0xFF22C55E);
            } else if (item.adj <= 249) {
                tvStatusDesc.setText(getString(R.string.sheet_status_perceptible));
                tvStatusDesc.setTextColor(0xFF38BDF8);
                tvAdj.setTextColor(0xFF38BDF8);
            } else if (item.adj <= 800) {
                tvStatusDesc.setText(getString(R.string.sheet_status_service));
                tvStatusDesc.setTextColor(0xFFF59E0B);
                tvAdj.setTextColor(0xFFF59E0B);
            } else {
                tvStatusDesc.setText(getString(R.string.sheet_status_cached));
                tvStatusDesc.setTextColor(0xFF94A3B8);
                tvAdj.setTextColor(0xFF94A3B8);
            }
        };
        updateStatusBanner.run();

        // 3. Retention & Restriction Switches
        MaterialSwitch swKeepAlive = view.findViewById(R.id.swDetailKeepAlive);
        MaterialSwitch swRestrict = view.findViewById(R.id.swDetailRestrict);

        Set<String> currentVips = prefs.getStringSet(KEY_VIP_PACKAGES, Collections.emptySet());
        Set<String> currentRestricted = prefs.getStringSet(KEY_RESTRICT_PACKAGES, Collections.emptySet());
        boolean mandatoryRestricted = PACKAGE_APPRETENTION.equals(basePkg);
        swKeepAlive.setChecked(!mandatoryRestricted && currentVips.contains(basePkg));
        swRestrict.setChecked(mandatoryRestricted || currentRestricted.contains(basePkg));
        if (mandatoryRestricted) {
            swKeepAlive.setEnabled(false);
            swRestrict.setEnabled(false);
        }

        final boolean[] isProgrammatic = {false};

        swKeepAlive.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isProgrammatic[0]) return;
            if (isChecked) {
                if (swRestrict.isChecked()) {
                    isProgrammatic[0] = true;
                    swRestrict.setChecked(false);
                    isProgrammatic[0] = false;
                }
                applyPolicyToPackage(basePkg, item.appName, true, false);
            } else {
                applyPolicyToPackage(basePkg, item.appName, false, false);
            }
            updateStatusBanner.run();
        });

        swRestrict.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isProgrammatic[0]) return;
            if (isChecked) {
                if (item.isSystemApp) {
                    isProgrammatic[0] = true;
                    swRestrict.setChecked(false);
                    isProgrammatic[0] = false;
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                            .setTitle(R.string.dialog_system_app_restrict_title)
                            .setMessage(getString(R.string.dialog_system_app_restrict_message, item.appName))
                            .setPositiveButton(R.string.btn_yes_continue, (d, which) -> {
                                if (swKeepAlive.isChecked()) {
                                    isProgrammatic[0] = true;
                                    swKeepAlive.setChecked(false);
                                    isProgrammatic[0] = false;
                                }
                                isProgrammatic[0] = true;
                                swRestrict.setChecked(true);
                                isProgrammatic[0] = false;
                                applyPolicyToPackage(basePkg, item.appName, false, true);
                                updateStatusBanner.run();
                            })
                            .setNegativeButton(R.string.btn_no_cancel, null)
                            .show();
                    return;
                }
                if (swKeepAlive.isChecked()) {
                    isProgrammatic[0] = true;
                    swKeepAlive.setChecked(false);
                    isProgrammatic[0] = false;
                }
                applyPolicyToPackage(basePkg, item.appName, false, true);
            } else {
                applyPolicyToPackage(basePkg, item.appName, false, false);
            }
            updateStatusBanner.run();
        });

        // 4. Action Buttons
        MaterialButton btnKill = view.findViewById(R.id.btnDetailKill);
        MaterialButton btnForceStop = view.findViewById(R.id.btnDetailForceStop);
        MaterialButton btnOpenApp = view.findViewById(R.id.btnDetailOpenApp);
        MaterialButton btnAppInfo = view.findViewById(R.id.btnDetailAppInfo);

        btnKill.setOnClickListener(v -> {
            RootTool.killProcess(item.pid, item.packageName);
            try {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) am.killBackgroundProcesses(basePkg);
            } catch (Throwable ignored) {}
            Toast.makeText(this, getString(R.string.toast_killed_process, item.appName), Toast.LENGTH_SHORT).show();
            sheet.dismiss();
            mTimerHandler.postDelayed(this::refreshRunningProcesses, 500);
        });

        btnForceStop.setOnClickListener(v -> {
            sWorkerPool.execute(() -> {
                RootTool.runCommand("am force-stop " + basePkg);
                try {
                    ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                    if (am != null) am.killBackgroundProcesses(basePkg);
                } catch (Throwable ignored) {}
                runOnUiThread(() -> {
                    Toast.makeText(this, getString(R.string.toast_force_stop_success, item.appName), Toast.LENGTH_SHORT).show();
                    sheet.dismiss();
                    mTimerHandler.postDelayed(this::refreshRunningProcesses, 500);
                });
            });
        });

        PackageManager pm = getPackageManager();
        Intent launchIntent = null;
        try {
            launchIntent = pm.getLaunchIntentForPackage(basePkg);
        } catch (Throwable ignored) {}

        if (launchIntent != null) {
            final Intent intentToLaunch = launchIntent;
            btnOpenApp.setOnClickListener(v -> {
                try {
                    startActivity(intentToLaunch);
                    sheet.dismiss();
                } catch (Throwable t) {
                    Toast.makeText(this, "Cannot open app", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            btnOpenApp.setEnabled(false);
            btnOpenApp.setAlpha(0.4f);
        }

        btnAppInfo.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.fromParts("package", basePkg, null));
                startActivity(intent);
                sheet.dismiss();
            } catch (Throwable t) {
                Toast.makeText(this, "Cannot open app settings", Toast.LENGTH_SHORT).show();
            }
        });

        sheet.show();
    }

    private void updateKeepAliveHeader() {
        if (keepAliveAdapter == null) return;
        int currentMode = keepAliveAdapter.getMode();
        int vipCount = keepAliveAdapter.getVipCount();
        int restrictedCount = keepAliveAdapter.getRestrictedCount();
        int totalCount = keepAliveAdapter.getTotalCount();

        if (currentMode == KeepAliveAdapter.MODE_KEEP_ALIVE) {
            if (tvKeepAliveSummaryCount != null) {
                tvKeepAliveSummaryCount.setText(getString(R.string.format_keep_alive_summary, vipCount));
            }
            if (tvBadgeMode != null) {
                tvBadgeMode.setText(R.string.badge_keep_alive_locked);
                tvBadgeMode.setTextColor(0xFF22C55E);
                tvBadgeMode.setBackgroundResource(R.drawable.bg_badge_green);
            }
            if (tvModeDesc != null) {
                tvModeDesc.setText(R.string.tab_keep_alive_desc);
            }
            if (chipActiveOnly != null) {
                chipActiveOnly.setText(getString(R.string.chip_keep_alive_only, vipCount));
            }
        } else {
            if (tvKeepAliveSummaryCount != null) {
                tvKeepAliveSummaryCount.setText(getString(R.string.format_restricted_summary, restrictedCount));
            }
            if (tvBadgeMode != null) {
                tvBadgeMode.setText(R.string.badge_restricted);
                tvBadgeMode.setTextColor(0xFFEF4444);
                tvBadgeMode.setBackgroundResource(R.drawable.bg_badge_red);
            }
            if (tvModeDesc != null) {
                tvModeDesc.setText(R.string.summary_restricted_desc);
            }
            if (chipActiveOnly != null) {
                chipActiveOnly.setText(getString(R.string.chip_restricted_only, restrictedCount));
            }
        }

        if (chipFilterUser != null) {
            chipFilterUser.setText(getString(R.string.chip_filter_user) + " (" + keepAliveAdapter.getUserCount() + ")");
        }
        if (chipFilterSystem != null) {
            chipFilterSystem.setText(getString(R.string.chip_filter_system) + " (" + keepAliveAdapter.getSystemCount() + ")");
        }
        if (chipAllApps != null) {
            chipAllApps.setText(getString(R.string.chip_all_apps, totalCount));
        }
    }

    private void loadInstalledApps(boolean forceReload) {
        if (isAppsLoaded && !forceReload) {
            updateKeepAliveHeader();
            return;
        }

        if (pbLoadingKeepAlive != null) pbLoadingKeepAlive.setVisibility(View.VISIBLE);
        if (rvKeepAliveApps != null) rvKeepAliveApps.setVisibility(View.GONE);

        sWorkerPool.execute(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> installed = pm.getInstalledApplications(0);
            Set<String> currentVips = new HashSet<>(prefs.getStringSet(KEY_VIP_PACKAGES, Collections.emptySet()));
            Set<String> currentRestricted = new HashSet<>(prefs.getStringSet(KEY_RESTRICT_PACKAGES, Collections.emptySet()));
            Set<String> launchables = getLaunchablePackages(pm);

            List<AppItem> loadedList = new ArrayList<>(installed.size());
            for (ApplicationInfo ai : installed) {
                boolean isAppRetention = PACKAGE_APPRETENTION.equals(ai.packageName);
                boolean isPinned = currentVips.contains(ai.packageName);
                boolean isRestricted = isAppRetention || currentRestricted.contains(ai.packageName);
                boolean isUserApp = (ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
                boolean isUpdatedSystem = (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                boolean hasLauncher = launchables.contains(ai.packageName);

                if (!isPinned && !isRestricted && !isUserApp && !isUpdatedSystem && !hasLauncher) {
                    continue;
                }

                AppMeta meta = sMetaCache.get(ai.packageName);
                String label;
                Drawable.ConstantState iconState;
                if (meta != null) {
                    label = meta.label;
                    iconState = meta.iconState;
                } else {
                    try {
                        label = pm.getApplicationLabel(ai).toString();
                        Drawable rawIcon = pm.getApplicationIcon(ai);
                        iconState = rawIcon.getConstantState();
                        meta = new AppMeta(label, iconState, (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0, ai.uid, "", hasLauncher);
                        sMetaCache.put(ai.packageName, meta);
                    } catch (Throwable ignored) {
                        continue;
                    }
                }

                Drawable icon = iconState != null ? iconState.newDrawable() : null;
                boolean isSysApp = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                loadedList.add(new AppItem(label, ai.packageName, icon, isPinned, isRestricted, isSysApp));
            }

            loadedList.sort((a, b) -> {
                if (a.isVip != b.isVip) return a.isVip ? -1 : 1;
                if (a.isRestricted != b.isRestricted) return a.isRestricted ? -1 : 1;
                return a.appName.compareToIgnoreCase(b.appName);
            });

            runOnUiThread(() -> {
                isAppsLoaded = true;
                allInstalledApps.clear();
                allInstalledApps.addAll(loadedList);
                if (keepAliveAdapter != null) {
                    keepAliveAdapter.setAllApps(allInstalledApps);
                }
                updateKeepAliveHeader();
                if (pbLoadingKeepAlive != null) pbLoadingKeepAlive.setVisibility(View.GONE);
                if (rvKeepAliveApps != null) rvKeepAliveApps.setVisibility(View.VISIBLE);
            });
        });
    }

    private static long parseMeminfoKb(String line) {
        try {
            String[] parts = line.split(":");
            if (parts.length == 2) {
                return Long.parseLong(parts[1].replace("kB", "").trim());
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private void updateHardwareStats() {
        if (!isRefreshingHardware.compareAndSet(false, true)) return;
        sWorkerPool.execute(() -> {
            try {
            // 1. Ultra-fast RAM & ZRAM calculation (direct /proc/meminfo read - 0.1ms)
            long totalRamBytes = 0;
            long availRamBytes = 0;
            long swapTotalBytes = 0;

            try (BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("MemTotal:")) {
                        totalRamBytes = parseMeminfoKb(line) * 1024L;
                    } else if (line.startsWith("MemAvailable:")) {
                        availRamBytes = parseMeminfoKb(line) * 1024L;
                    } else if (line.startsWith("SwapTotal:")) {
                        swapTotalBytes = parseMeminfoKb(line) * 1024L;
                    }
                    if (totalRamBytes > 0 && availRamBytes > 0 && swapTotalBytes > 0) {
                        break;
                    }
                }
            } catch (Throwable ignored) {}

            if (totalRamBytes <= 0 || availRamBytes <= 0) {
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
            String ramType = HardwareInfo.getRamType();
            String zramText = "";
            if (swapTotalBytes > 0) {
                double swapTotalGb = swapTotalBytes / (1024.0 * 1024.0 * 1024.0);
                zramText = String.format("ZRAM: %.1f GB", swapTotalGb);
            }

            // 2. CPU Calculation
            int cpuPercent = readCpuUsage(getCpuStatLine());
            int cores = Runtime.getRuntime().availableProcessors();
            String socName = HardwareInfo.getSocName();
            String cpuMaxClock = HardwareInfo.getCpuMaxClock();
            String cpuTemp = HardwareInfo.getCpuTemp();

            // 3. Storage Calculation (Direct StatFs syscall - 0.05ms)
            long totalStorageBytes = 0;
            long usedStorageBytes = 0;
            long freeStorageBytes = 0;
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

            int storagePercent = (int) Math.min(100, (usedStorageBytes * 100) / (totalStorageBytes > 0 ? totalStorageBytes : 1));
            double usedStorageGb = usedStorageBytes / (1024.0 * 1024.0 * 1024.0);
            double totalStorageGb = totalStorageBytes / (1024.0 * 1024.0 * 1024.0);
            double freeStorageGb = freeStorageBytes / (1024.0 * 1024.0 * 1024.0);
            String storageType = HardwareInfo.getStorageType();
            long physicalStorageBytes = HardwareInfo.getPhysicalStorageBytes();
            double physicalStorageGb = physicalStorageBytes / 1_000_000_000.0;

            // 4. GPU & Display Info
            String gpuModel = HardwareInfo.getGpuModel();
            String displayInfo = HardwareInfo.getDisplayInfo(MainActivity.this);
            HardwareInfo.GpuStats gpuStats = HardwareInfo.getGpuStats();
            HardwareInfo.HardwareDetails hardwareDetails = HardwareInfo.getHardwareDetails();

            // 5. Battery & Thermal Info
            HardwareInfo.BatteryInfo bInfo = HardwareInfo.getBatteryInfo(MainActivity.this);

            final String finalZram = zramText;
            runOnUiThread(() -> {
                // RAM
                if (tvRamDetails != null) tvRamDetails.setText(getString(R.string.format_ram_details, usedRamGb, totalRamGb, ramPercent));
                if (tvRamSubtext != null) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(getString(R.string.format_ram_subtext_notype, availRamGb, discount));
                    if (ramType != null && !ramType.isEmpty()) {
                        sb.append(" • ").append(ramType);
                    }
                    if (!finalZram.isEmpty()) {
                        sb.append(" • ").append(finalZram);
                    }
                    tvRamSubtext.setText(sb.toString());
                }
                if (pbRamUsage != null) pbRamUsage.setProgress(ramPercent);

                // CPU
                if (tvCpuDetails != null) {
                    tvCpuDetails.setText(cpuPercent >= 0 ? getString(R.string.format_cpu_details, cpuPercent) : "N/A");
                }
                if (tvCpuSubtext != null) {
                    StringBuilder sb = new StringBuilder();
                    if (socName != null && !socName.isEmpty()) {
                        sb.append(getString(R.string.format_cpu_subtext, socName, cores));
                    } else {
                        sb.append(getString(R.string.format_cpu_subtext_nocores, cores + " Cores"));
                    }
                    if (!cpuMaxClock.isEmpty() || !cpuTemp.isEmpty()) {
                        sb.append(" (");
                        if (!cpuMaxClock.isEmpty()) sb.append(cpuMaxClock);
                        if (!cpuMaxClock.isEmpty() && !cpuTemp.isEmpty()) sb.append(" • ");
                        if (!cpuTemp.isEmpty()) sb.append(cpuTemp);
                        sb.append(")");
                    }
                    if (hardwareDetails != null && (!hardwareDetails.cpuModel.isEmpty() || !hardwareDetails.cpuAbi.isEmpty())) {
                        sb.append('\n').append(getString(R.string.format_cpu_identity,
                            hardwareDetails.cpuModel, hardwareDetails.cpuAbi));
                    }
                    if (hardwareDetails != null && !hardwareDetails.cpuTopology.isEmpty()) {
                        sb.append('\n').append(getString(R.string.format_cpu_topology, hardwareDetails.cpuTopology));
                    }
                    tvCpuSubtext.setText(sb.toString());
                }
                if (pbCpuUsage != null) pbCpuUsage.setProgress(Math.max(0, cpuPercent));

                // Storage
                if (tvStorageDetails != null) tvStorageDetails.setText(getString(R.string.format_storage_details, usedStorageGb, totalStorageGb, storagePercent));
                if (tvStorageSubtext != null) {
                    if (physicalStorageBytes > 0 && storageType != null && !storageType.isEmpty()) {
                        tvStorageSubtext.setText(getString(R.string.format_storage_subtext_physical_type,
                            freeStorageGb, physicalStorageGb, storageType));
                    } else if (physicalStorageBytes > 0) {
                        tvStorageSubtext.setText(getString(R.string.format_storage_subtext_physical,
                            freeStorageGb, physicalStorageGb));
                    } else if (storageType != null && !storageType.isEmpty()) {
                        tvStorageSubtext.setText(getString(R.string.format_storage_subtext_type, freeStorageGb, storageType));
                    } else {
                        tvStorageSubtext.setText(getString(R.string.format_storage_subtext, freeStorageGb));
                    }
                }
                if (pbStorageUsage != null) pbStorageUsage.setProgress(storagePercent);

                // GPU & Display
                int effectiveGpuLoad = Math.max(0, gpuStats.loadPercent);
                if (tvGpuDetails != null) {
                    if (gpuStats.clockMhz > 0) {
                        tvGpuDetails.setText(getString(R.string.format_gpu_details_load_clock, effectiveGpuLoad, gpuStats.clockMhz));
                    } else {
                        tvGpuDetails.setText(getString(R.string.format_gpu_details_load, effectiveGpuLoad));
                    }
                }
                if (pbGpuUsage != null) {
                    pbGpuUsage.setVisibility(View.VISIBLE);
                    pbGpuUsage.setProgress(effectiveGpuLoad);
                }
                if (tvDisplaySubtext != null) {
                    StringBuilder sbGpu = new StringBuilder();
                    if (gpuModel != null && !gpuModel.isEmpty()) {
                        sbGpu.append(gpuModel);
                    }
                    if (displayInfo != null && !displayInfo.isEmpty()) {
                        if (sbGpu.length() > 0) {
                            sbGpu.append(" • ");
                        }
                        sbGpu.append(displayInfo);
                    }
                    if (hardwareDetails != null && !hardwareDetails.gpuVendor.isEmpty()) {
                        sbGpu.append('\n').append(getString(R.string.format_gpu_vendor, hardwareDetails.gpuVendor));
                    }
                    if (hardwareDetails != null && !hardwareDetails.gpuDriver.isEmpty()) {
                        sbGpu.append('\n').append(getString(R.string.format_gpu_driver, hardwareDetails.gpuDriver));
                    }
                    tvDisplaySubtext.setText(sbGpu.toString());
                }

                if (tvPlatformSubtext != null && hardwareDetails != null) {
                    tvPlatformSubtext.setText(getString(R.string.format_device_platform,
                        hardwareDetails.deviceName, hardwareDetails.platform));
                }

                // Battery & Thermal
                if (bInfo != null) {
                    if (tvBatteryDetails != null) {
                        if (bInfo.totalCapacityMah > 0 && bInfo.currentCapacityMah > 0) {
                            if (bInfo.temperatureC > 0) {
                                tvBatteryDetails.setText(getString(R.string.format_battery_mah_details,
                                        bInfo.currentCapacityMah, bInfo.totalCapacityMah, bInfo.levelPercent, bInfo.temperatureC));
                            } else {
                                tvBatteryDetails.setText(getString(R.string.format_battery_mah_notemp,
                                        bInfo.currentCapacityMah, bInfo.totalCapacityMah, bInfo.levelPercent));
                            }
                        } else {
                            if (bInfo.temperatureC > 0) {
                                tvBatteryDetails.setText(getString(R.string.format_battery_details, bInfo.levelPercent, bInfo.temperatureC));
                            } else {
                                tvBatteryDetails.setText(getString(R.string.format_battery_details_notemp, bInfo.levelPercent));
                            }
                        }
                    }
                    if (pbBatteryUsage != null) {
                        pbBatteryUsage.setProgress(bInfo.levelPercent);
                        if (bInfo.levelPercent <= 15) {
                            pbBatteryUsage.setIndicatorColor(ContextCompat.getColor(MainActivity.this, R.color.accent_red));
                        } else if (bInfo.levelPercent <= 30) {
                            pbBatteryUsage.setIndicatorColor(ContextCompat.getColor(MainActivity.this, R.color.accent_amber));
                        } else {
                            pbBatteryUsage.setIndicatorColor(ContextCompat.getColor(MainActivity.this, R.color.accent_green));
                        }
                    }
                    if (tvBatterySubtext != null) {
                        String stStr;
                        if (bInfo.isCharging) {
                            String base = getString(R.string.battery_status_charging);
                            if (bInfo.currentAmperageMa > 0) {
                                stStr = getString(R.string.format_battery_rate_charging, base, bInfo.currentAmperageMa);
                            } else {
                                stStr = base;
                            }
                        } else {
                            String base = getString(R.string.battery_status_discharging);
                            if (bInfo.currentAmperageMa < 0) {
                                stStr = getString(R.string.format_battery_rate_discharging, base, Math.abs(bInfo.currentAmperageMa));
                            } else {
                                stStr = base;
                            }
                        }

                        String hStr = getString(R.string.battery_health_good);
                        if (bInfo.health == BatteryManager.BATTERY_HEALTH_OVERHEAT) hStr = getString(R.string.battery_health_overheat);
                        else if (bInfo.health == BatteryManager.BATTERY_HEALTH_DEAD) hStr = getString(R.string.battery_health_dead);
                        else if (bInfo.health == BatteryManager.BATTERY_HEALTH_COLD) hStr = getString(R.string.battery_health_cold);

                        tvBatterySubtext.setText(getString(R.string.format_battery_subtext, stStr, hStr, bInfo.technology));
                    }
                }

                if (tvShieldStatus != null) {
                    tvShieldStatus.setText(R.string.status_killshield_active);
                }
            });
            } finally {
                isRefreshingHardware.set(false);
            }
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
            String[] parts = line.trim().split("\s+");
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
                }
            }
        }
        return -1;
    }

    private void refreshRunningProcesses() {
        if (isScanningProcesses.getAndSet(true)) {
            return;
        }
        sWorkerPool.execute(() -> {
            List<ProcessItem> items = new ArrayList<>();
            PackageManager pm = getPackageManager();
            Set<String> vipSet = prefs.getStringSet(KEY_VIP_PACKAGES, Collections.emptySet());

            try {
                if (RootTool.isRootAvailable()) {
                    List<RootTool.ProcessInfo> rootProcs = RootTool.getRunningProcesses();
                    Set<String> restrictSet = prefs != null ? prefs.getStringSet(KEY_RESTRICT_PACKAGES, Collections.emptySet()) : Collections.emptySet();
                    boolean immediateKill = prefs != null && prefs.getBoolean(KEY_RESTRICT_IMMEDIATE, false);

                    Set<String> launchables = getLaunchablePackages(pm);
                    for (RootTool.ProcessInfo pi : rootProcs) {
                        String pkg = pi.processName;
                        if (pkg.contains(":")) {
                            pkg = pkg.substring(0, pkg.indexOf(':'));
                        }
                        if (immediateKill && restrictSet.contains(pkg) && pi.adj > 0) {
                            RootTool.killProcess(pi.pid, pkg);
                            continue;
                        }

                        AppMeta meta = sMetaCache.get(pkg);
                        if (meta == null) {
                            try {
                                ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                                boolean isSys = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                                int u = appInfo.uid;
                                boolean isUser = !isSys;
                                boolean isUpdated = (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                                boolean hasLaunch = launchables.contains(pkg);
                                boolean isVis = isUser || isUpdated || hasLaunch;

                                String l = pm.getApplicationLabel(appInfo).toString();
                                Drawable rawIcon = pm.getApplicationIcon(appInfo);
                                Drawable.ConstantState icState = rawIcon.getConstantState();
                                String vName = "";
                                try {
                                    PackageInfo pInfo = pm.getPackageInfo(pkg, 0);
                                    if (pInfo != null && pInfo.versionName != null) {
                                        vName = pInfo.versionName;
                                    }
                                } catch (Throwable ignored) {}

                                meta = new AppMeta(l, icState, isSys, u, vName, isVis);
                                sMetaCache.put(pkg, meta);
                            } catch (Throwable ignored) {
                                continue;
                            }
                        }

                        boolean isPinned = vipSet.contains(pkg);
                        if (!isPinned && !meta.isVisible) {
                            continue;
                        }

                        String label = meta.label != null ? meta.label : pkg;
                        Drawable icon = meta.iconState != null ? meta.iconState.newDrawable() : null;
                        boolean isSystemApp = meta.isSystemApp;
                        int uid = meta.uid;
                        String versionName = meta.versionName;
                        int adj = pi.adj;
                        if (vipSet.contains(pkg)) {
                            adj = 200;
                        }
                        long memBytes = pi.memoryBytes;
                        if (memBytes <= 0) {
                            try {
                                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                                if (am != null) {
                                    android.os.Debug.MemoryInfo[] mi = am.getProcessMemoryInfo(new int[]{pi.pid});
                                    if (mi != null && mi.length > 0 && mi[0] != null) {
                                        long pssKb = mi[0].getTotalPss();
                                        if (pssKb <= 0) pssKb = mi[0].getTotalPrivateDirty();
                                        if (pssKb > 0) memBytes = pssKb * 1024L;
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                        items.add(new ProcessItem(label, pi.processName, pi.pid, adj, icon, memBytes, isSystemApp, uid, versionName));
                    }
                } else {
                    ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                    if (am != null) {
                        List<ActivityManager.RunningAppProcessInfo> running = am.getRunningAppProcesses();
                        if (running != null) {
                            int[] pids = new int[running.size()];
                            for (int i = 0; i < running.size(); i++) {
                                pids[i] = running.get(i).pid;
                            }
                            android.os.Debug.MemoryInfo[] memInfos = null;
                            try {
                                memInfos = am.getProcessMemoryInfo(pids);
                            } catch (Throwable ignored) {}

                            for (int i = 0; i < running.size(); i++) {
                                ActivityManager.RunningAppProcessInfo info = running.get(i);
                                if (info.uid < 10000) continue;
                                String pkg = (info.pkgList != null && info.pkgList.length > 0) ? info.pkgList[0] : info.processName;
                                String label = sLabelCache.get(pkg);
                                Drawable.ConstantState iconState = sIconCache.get(pkg);
                                Drawable icon = iconState != null ? iconState.newDrawable() : null;
                                boolean isSystemApp = false;
                                int uid = info.uid;
                                String versionName = "";

                                try {
                                    ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                                    isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                                    label = pm.getApplicationLabel(appInfo).toString();
                                    Drawable rawIcon = pm.getApplicationIcon(appInfo);
                                    sLabelCache.put(pkg, label);
                                    if (rawIcon.getConstantState() != null) {
                                        sIconCache.put(pkg, rawIcon.getConstantState());
                                        icon = rawIcon.getConstantState().newDrawable();
                                    } else {
                                        icon = rawIcon;
                                    }

                                    try {
                                        PackageInfo pInfo = pm.getPackageInfo(pkg, 0);
                                        if (pInfo != null && pInfo.versionName != null) {
                                            versionName = pInfo.versionName;
                                        }
                                    } catch (Throwable ignored) {}
                                } catch (Throwable ignored) {}

                                if (label == null) label = pkg;
                                int estimatedAdj = vipSet.contains(pkg) ? 200 : (200 + (items.size() * 5));
                                long memBytes = 0;
                                if (memInfos != null && i < memInfos.length && memInfos[i] != null) {
                                    long pssKb = memInfos[i].getTotalPss();
                                    if (pssKb <= 0) pssKb = memInfos[i].getTotalPrivateDirty();
                                    if (pssKb > 0) memBytes = pssKb * 1024L;
                                }
                                items.add(new ProcessItem(label, pkg, info.pid, estimatedAdj, icon, memBytes, isSystemApp, uid, versionName));
                            }
                        }
                    }
                }

                items.sort((a, b) -> Integer.compare(a.adj, b.adj));
            } catch (Throwable t) {
                android.util.Log.e("MainActivity", "Error in refreshRunningProcesses", t);
            } finally {
                isScanningProcesses.set(false);
                runOnUiThread(() -> {
                    if (processAdapter != null) {
                        processAdapter.setPolicyPackages(
                            prefs.getStringSet(KEY_VIP_PACKAGES, Collections.emptySet()),
                            prefs.getStringSet(KEY_RESTRICT_PACKAGES, Collections.emptySet())
                        );
                        // Anti-flicker: If we already have items and a background scan returns empty, keep previous items!
                        if (!items.isEmpty() || processAdapter.getItemCount() == 0) {
                            processAdapter.updateList(items);
                        }
                    }

                    int currentCount = (processAdapter != null) ? processAdapter.getItemCount() : items.size();

                    if (currentCount > 0) {
                        if (layoutProcessesLoading != null) layoutProcessesLoading.setVisibility(View.GONE);
                        if (rvProcesses != null) rvProcesses.setVisibility(View.VISIBLE);
                        if (tvProcessCount != null) {
                            tvProcessCount.setText(getString(R.string.format_process_count, currentCount));
                        }
                    } else {
                        // 0 apps: show loading animation
                        if (layoutProcessesLoading != null) layoutProcessesLoading.setVisibility(View.VISIBLE);
                        if (rvProcesses != null) rvProcesses.setVisibility(View.GONE);
                        if (tvProcessCount != null) {
                            tvProcessCount.setText(getString(R.string.format_process_count, 0));
                        }
                    }

                    if (swipeRefreshDashboard != null && swipeRefreshDashboard.isRefreshing()) {
                        swipeRefreshDashboard.setRefreshing(false);
                    }
                });
            }
        });
    }

    private void refreshLogs() {
        sWorkerPool.execute(() -> {
            StringBuilder sb = new StringBuilder();
            boolean hasLogs = false;

            // 1. If root is available, ensure permissions on log files
            if (RootTool.hasRoot()) {
                RootTool.runCommand("chmod -R 777 /data/user_de/0/com.hchen.appretention/files/logs 2>/dev/null");
            }

            // 2. Read from candidate log files
            File[] candidateDirs = new File[]{
                    new File("/data/user_de/0/com.hchen.appretention/files/logs"),
                    new File(getFilesDir(), "logs"),
                    new File(getExternalFilesDir(null), "logs")
            };

            for (File logDir : candidateDirs) {
                if (logDir.exists() && logDir.isDirectory()) {
                    File[] files = logDir.listFiles();
                    if (files != null && files.length > 0) {
                        for (File f : files) {
                            if (f.isFile() && f.getName().endsWith(".log")) {
                                try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                                    String line;
                                    boolean fileHasContent = false;
                                    StringBuilder fileSb = new StringBuilder();
                                    while ((line = reader.readLine()) != null) {
                                        fileSb.append(line).append("\n");
                                        fileHasContent = true;
                                    }
                                    if (fileHasContent) {
                                        sb.append("--- [File: ").append(f.getName()).append("] ---\n");
                                        sb.append(fileSb);
                                        hasLogs = true;
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                }
            }

            // Fallback: If root and files were not readable via standard Java, try root cat
            if (!hasLogs && RootTool.hasRoot()) {
                String catOut = RootTool.runCommand("cat /data/user_de/0/com.hchen.appretention/files/logs/*.log 2>/dev/null");
                if (catOut != null && !catOut.trim().isEmpty()) {
                    sb.append("--- [File Logs (Root)] ---\n").append(catOut).append("\n");
                    hasLogs = true;
                }
            }

            // 3. Always capture real-time system & module logcat
            String logcatCmd = "logcat -d -v time -s AppRetention:V KillShieldOpt:V ApplyAdjOpt:V BackgroundRestrictOpt:V LogServices:V SaveLog:V ProcessScanner:V LSPosed:V Xposed:V -t 350";
            String logcatLogs = RootTool.runCommand(logcatCmd);
            if (logcatLogs != null && !logcatLogs.trim().isEmpty()) {
                if (hasLogs) {
                    sb.append("\n========================================\n");
                    sb.append("=== [REAL-TIME SYSTEM & HOOK LOGCAT] ===\n");
                    sb.append("========================================\n");
                }
                sb.append(logcatLogs.trim());
                hasLogs = true;
            }

            final boolean finalHasLogs = hasLogs;
            final String finalLogContent = sb.toString();

            runOnUiThread(() -> {
                if (tvLogContent != null) {
                    if (finalHasLogs) {
                        tvLogContent.setText(finalLogContent);
                    } else {
                        tvLogContent.setText(R.string.placeholder_log);
                    }
                }
            });
        });
    }

    private void saveLogToFile() {
        if (tvLogContent == null) return;
        String content = tvLogContent.getText().toString().trim();
        if (content.isEmpty() || content.equals(getString(R.string.placeholder_log))) {
            Toast.makeText(this, R.string.toast_log_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        sWorkerPool.execute(() -> {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "AppRetention_Log_" + timeStamp + ".txt";
            boolean saved = false;
            String savedPath = "";

            // Method 1: Android 10+ MediaStore Downloads
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                try {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AppRetention");
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri != null) {
                        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                            if (os != null) {
                                os.write(content.getBytes(StandardCharsets.UTF_8));
                                os.flush();
                                saved = true;
                                savedPath = "Download/AppRetention/" + fileName;
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }

            // Method 2: Public Download directory direct file
            if (!saved) {
                try {
                    File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!downloadDir.exists()) downloadDir.mkdirs();
                    File dest = new File(downloadDir, fileName);
                    try (FileWriter writer = new FileWriter(dest)) {
                        writer.write(content);
                        writer.flush();
                        saved = true;
                        savedPath = dest.getAbsolutePath();
                    }
                } catch (Throwable ignored) {}
            }

            // Method 3: Root write fallback
            if (!saved && RootTool.hasRoot()) {
                try {
                    File temp = new File(getCacheDir(), fileName);
                    try (FileWriter writer = new FileWriter(temp)) {
                        writer.write(content);
                        writer.flush();
                    }
                    RootTool.runCommand("cp " + temp.getAbsolutePath() + " /sdcard/Download/" + fileName + " && chmod 666 /sdcard/Download/" + fileName);
                    temp.delete();
                    saved = true;
                    savedPath = "/sdcard/Download/" + fileName;
                } catch (Throwable ignored) {}
            }

            // Method 4: App external files fallback
            if (!saved) {
                try {
                    File dest = new File(getExternalFilesDir(null), fileName);
                    try (FileWriter writer = new FileWriter(dest)) {
                        writer.write(content);
                        writer.flush();
                        saved = true;
                        savedPath = dest.getAbsolutePath();
                    }
                } catch (Throwable ignored) {}
            }

            final boolean isSuccess = saved;
            final String finalPath = savedPath;

            runOnUiThread(() -> {
                if (isSuccess) {
                    Toast.makeText(this, getString(R.string.toast_log_saved, finalPath), Toast.LENGTH_LONG).show();

                    // Offer to share the log
                    try {
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("text/plain");
                        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "AppRetention Log " + timeStamp);
                        shareIntent.putExtra(Intent.EXTRA_TEXT, content);
                        startActivity(Intent.createChooser(shareIntent, getString(R.string.title_share_log)));
                    } catch (Throwable ignored) {}
                } else {
                    Toast.makeText(this, "Không thể lưu file nhật ký!", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void clearLogFiles() {
        sWorkerPool.execute(() -> {
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
            if (RootTool.hasRoot()) {
                RootTool.runCommand("rm -rf /data/user_de/0/com.hchen.appretention/files/logs/* 2>/dev/null; logcat -c 2>/dev/null");
            } else {
                try {
                    Runtime.getRuntime().exec("logcat -c");
                } catch (Throwable ignored) {}
            }

            runOnUiThread(() -> {
                if (tvLogContent != null) {
                    tvLogContent.setText(R.string.placeholder_log);
                }
                Toast.makeText(this, R.string.toast_log_cleared, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private static void enforceAppRetentionRestriction(Set<String> vipSet, Set<String> restrictSet) {
        vipSet.remove(PACKAGE_APPRETENTION);
        restrictSet.add(PACKAGE_APPRETENTION);
    }

    private void persistPolicySets(Set<String> vipSet, Set<String> restrictSet) {
        enforceAppRetentionRestriction(vipSet, restrictSet);
        prefs.edit()
            .putStringSet(KEY_VIP_PACKAGES, new HashSet<>(vipSet))
            .putStringSet(KEY_RESTRICT_PACKAGES, new HashSet<>(restrictSet))
            .commit();
        RootTool.setProp(KEY_VIP_PACKAGES, String.join(",", vipSet));
        RootTool.setProp(KEY_RESTRICT_PACKAGES, String.join(",", restrictSet));
    }

    private void syncPolicyFiles(Set<String> vipSet, Set<String> restrictSet) {
        enforceAppRetentionRestriction(vipSet, restrictSet);
        sWorkerPool.execute(() -> {
            File[] restrictFiles = new File[]{
                new File("/data/user_de/0/com.hchen.appretention/files/restricted_packages.txt"),
                new File(getFilesDir(), "restricted_packages.txt")
            };
            for (File rf : restrictFiles) {
                try {
                    File parent = rf.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    try (FileWriter writer = new FileWriter(rf)) {
                        for (String pkg : restrictSet) {
                            writer.write(pkg + "\n");
                        }
                        writer.flush();
                    }
                    rf.setReadable(true, false);
                    rf.setWritable(true, false);
                } catch (Throwable ignored) {}
            }

            File[] vipFiles = new File[]{
                new File("/data/user_de/0/com.hchen.appretention/files/vip_packages.txt"),
                new File(getFilesDir(), "vip_packages.txt")
            };
            for (File vf : vipFiles) {
                try {
                    File parent = vf.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    try (FileWriter writer = new FileWriter(vf)) {
                        for (String pkg : vipSet) {
                            writer.write(pkg + "\n");
                        }
                        writer.flush();
                    }
                    vf.setReadable(true, false);
                    vf.setWritable(true, false);
                } catch (Throwable ignored) {}
            }

            if (RootTool.hasRoot()) {
                RootTool.runCommand("chmod -R 666 /data/user_de/0/com.hchen.appretention/files/*.txt 2>/dev/null");
            }
        });
    }

    private void syncImmediateKillFile(boolean isChecked) {
        sWorkerPool.execute(() -> {
            File[] files = new File[]{
                new File("/data/user_de/0/com.hchen.appretention/files/immediate_kill.txt"),
                new File(getFilesDir(), "immediate_kill.txt")
            };
            for (File f : files) {
                try {
                    File parent = f.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    try (FileWriter writer = new FileWriter(f)) {
                        writer.write(isChecked ? "1\n" : "0\n");
                        writer.flush();
                    }
                    f.setReadable(true, false);
                    f.setWritable(true, false);
                } catch (Throwable ignored) {}
            }
            if (RootTool.hasRoot()) {
                RootTool.runCommand("chmod 666 /data/user_de/0/com.hchen.appretention/files/immediate_kill.txt 2>/dev/null");
            }
        });
    }

}
