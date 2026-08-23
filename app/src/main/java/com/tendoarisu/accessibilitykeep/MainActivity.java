package com.tendoarisu.accessibilitykeep;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Insets;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<AppEntry> allEntries = new ArrayList<>();
    private final Set<String> whitelist = new HashSet<>();
    private final Map<String, Switch> switches = new HashMap<>();
    private LinearLayout listContainer;
    private EditText searchBox;
    private TextView countText;
    private TextView rootText;
    private ProgressBar loading;
    private boolean changingSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        setContentView(buildContent());
        refreshData();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(16, 17, 20));
        window.setNavigationBarColor(Color.rgb(16, 17, 20));
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(true);
        }
    }

    private View buildContent() {
        LinearLayout root = vertical(0);
        root.setBackgroundColor(color(R.color.surface));
        root.setClipToPadding(false);
        root.setPadding(dp(18), dp(14), dp(18), 0);

        LinearLayout page = vertical(0);
        page.setClipToPadding(false);
        root.addView(page, new LinearLayout.LayoutParams(-1, -2));
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.statusBars()
                        | WindowInsets.Type.navigationBars());
                root.setPadding(dp(18), bars.top + dp(18), dp(18), 0);
                return insets;
            });
            root.post(root::requestApplyInsets);
        }

        LinearLayout toolbar = horizontal(0);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("无障碍保活", 24, Color.WHITE, Typeface.BOLD);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton refresh = new ImageButton(this);
        refresh.setImageResource(android.R.drawable.ic_popup_sync);
        refresh.setColorFilter(Color.WHITE);
        refresh.setBackground(rippleIconBackground());
        refresh.setContentDescription("刷新");
        refresh.setOnClickListener(v -> refreshData());
        toolbar.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(48)));
        page.addView(toolbar);

        TextView subtitle = text("应用关闭后保持无障碍授权", 14, color(R.color.text_secondary), Typeface.NORMAL);
        subtitle.setPadding(0, 0, 0, dp(16));
        page.addView(subtitle);

        LinearLayout statusCard = card();
        LinearLayout statusColumn = vertical(0);
        TextView statusTitle = text("运行状态", 13, color(R.color.text_secondary), Typeface.BOLD);
        statusColumn.addView(statusTitle);
        rootText = text("Root 检测中…", 15, color(R.color.text_primary), Typeface.NORMAL);
        rootText.setPadding(0, dp(5), 0, 0);
        statusColumn.addView(rootText);
        countText = text("正在读取无障碍服务…", 13, color(R.color.text_secondary), Typeface.NORMAL);
        countText.setPadding(0, dp(5), 0, 0);
        statusColumn.addView(countText);
        statusCard.addView(statusColumn);
        page.addView(statusCard, marginParams(-1, -2, 0, 0, 0, 12));

        LinearLayout iconCard = card();
        LinearLayout iconRow = horizontal(0);
        iconRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout iconLabels = vertical(0);
        iconLabels.addView(text("隐藏图标", 15, color(R.color.text_primary), Typeface.BOLD));
        iconLabels.addView(text("隐藏后可从 LSPosed 管理器打开", 12, color(R.color.text_secondary), Typeface.NORMAL));
        iconRow.addView(iconLabels, new LinearLayout.LayoutParams(0, -2, 1));
        Switch hideIcon = new Switch(this);
        hideIcon.setChecked(isLauncherAliasDisabled());
        hideIcon.setOnCheckedChangeListener((buttonView, isChecked) -> setLauncherAliasVisible(!isChecked));
        iconRow.addView(hideIcon, new LinearLayout.LayoutParams(-2, -2));
        iconCard.addView(iconRow, new LinearLayout.LayoutParams(-1, -2));
        page.addView(iconCard, marginParams(-1, -2, 0, 0, 0, 12));

        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setHint("搜索应用或包名");
        searchBox.setTextColor(color(R.color.text_primary));
        searchBox.setHintTextColor(color(R.color.text_secondary));
        searchBox.setTextSize(15);
        searchBox.setPadding(dp(14), 0, dp(14), 0);
        searchBox.setBackground(interactiveSurfaceBackground(color(R.color.surface_raised), 12));
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderEntries();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        page.addView(searchBox, marginParams(-1, dp(48), 0, 0, 0, 12));

        loading = new ProgressBar(this);
        loading.setIndeterminate(true);
        root.addView(loading, centeredParams(dp(40), dp(40)));

        listContainer = vertical(0);
        ScrollView listScroll = new ScrollView(this);
        listScroll.setFillViewport(true);
        listScroll.setClipToPadding(false);
        listScroll.setBackgroundColor(color(R.color.surface));
        listScroll.addView(listContainer, new ScrollView.LayoutParams(-1, -2));
        root.addView(listScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        return root;
    }

    private void refreshData() {
        loading.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            boolean rootAvailable = RootSettings.isRootAvailable();
            Set<String> saved = ModuleConfig.parsePackages(RootSettings.readWhitelist());
            List<AppEntry> entries = readAccessibilityApps();
            runOnUiThread(() -> {
                whitelist.clear();
                whitelist.addAll(saved);
                allEntries.clear();
                allEntries.addAll(entries);
                rootText.setText(rootAvailable ? "Root 已授权" : "未获得 Root 授权");
                rootText.setTextColor(rootAvailable ? color(R.color.accent) : color(R.color.warning));
                renderEntries();
            });
        });
    }

    private List<AppEntry> readAccessibilityApps() {
        List<AppEntry> result = new ArrayList<>();
        Map<String, AppEntry> byPackage = new HashMap<>();
        PackageManager packageManager = getPackageManager();
        Intent queryIntent = new Intent("android.accessibilityservice.AccessibilityService");
        int queryFlags = PackageManager.GET_META_DATA
                | PackageManager.MATCH_DISABLED_COMPONENTS
                | PackageManager.MATCH_DIRECT_BOOT_AWARE
                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
        for (ResolveInfo resolveInfo : packageManager.queryIntentServices(queryIntent, queryFlags)) {
            ServiceInfo serviceInfo = resolveInfo.serviceInfo;
            if (serviceInfo == null || serviceInfo.packageName == null || serviceInfo.name == null) {
                continue;
            }
            ComponentName component = new ComponentName(serviceInfo.packageName, serviceInfo.name);
            if (component == null) {
                continue;
            }
            String packageName = component.getPackageName();
            AppEntry entry = byPackage.get(packageName);
            if (entry == null) {
                entry = new AppEntry(packageName);
                try {
                    entry.label = String.valueOf(packageManager.getApplicationLabel(
                            packageManager.getApplicationInfo(packageName, 0)));
                    entry.icon = packageManager.getApplicationIcon(packageName);
                } catch (PackageManager.NameNotFoundException ignored) {
                    entry.label = packageName;
                }
                byPackage.put(packageName, entry);
                result.add(entry);
            }
            CharSequence serviceLabel = serviceInfo.loadLabel(packageManager);
            String label = serviceLabel == null ? component.getClassName() : serviceLabel.toString();
            if (!entry.services.contains(label)) {
                entry.services.add(label);
            }
        }
        Collections.sort(result, Comparator.comparing(entry -> entry.label.toLowerCase()));
        return result;
    }

    private void renderEntries() {
        if (listContainer == null) {
            return;
        }
        listContainer.removeAllViews();
        switches.clear();
        String query = searchBox == null ? "" : searchBox.getText().toString().trim().toLowerCase();
        int shown = 0;
        for (AppEntry entry : allEntries) {
            if (!query.isEmpty() && !entry.label.toLowerCase().contains(query)
                    && !entry.packageName.toLowerCase().contains(query)) {
                continue;
            }
            listContainer.addView(createEntryRow(entry));
            shown++;
        }
        loading.setVisibility(View.GONE);
        countText.setText("已发现 " + allEntries.size() + " 个应用 · 白名单 " + whitelist.size() + " 个");
        if (shown == 0) {
            TextView empty = text("没有匹配的无障碍服务", 14, color(R.color.text_secondary), Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(36), 0, dp(36));
            listContainer.addView(empty);
        }
    }

    private View createEntryRow(AppEntry entry) {
        LinearLayout row = card(true);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        if (entry.icon != null) {
            icon.setImageDrawable(entry.icon);
        }
        row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout labels = vertical(0);
        labels.setPadding(dp(12), 0, dp(8), 0);
        labels.addView(text(entry.label, 15, color(R.color.text_primary), Typeface.BOLD));
        TextView packageText = text(entry.packageName, 12, color(R.color.text_secondary), Typeface.NORMAL);
        packageText.setSingleLine(true);
        labels.addView(packageText);
        TextView serviceText = text(String.join(" · ", entry.services), 12, color(R.color.text_secondary), Typeface.NORMAL);
        serviceText.setSingleLine(true);
        labels.addView(serviceText);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        Switch enable = new Switch(this);
        enable.setChecked(whitelist.contains(entry.packageName));
        enable.setOnCheckedChangeListener((buttonView, checked) -> {
            if (changingSwitch) {
                return;
            }
            if (checked) {
                whitelist.add(entry.packageName);
            } else {
                whitelist.remove(entry.packageName);
            }
            saveWhitelist();
        });
        row.setOnClickListener(v -> enable.setChecked(!enable.isChecked()));
        switches.put(entry.packageName, enable);
        row.addView(enable, new LinearLayout.LayoutParams(-2, -2));
        return row;
    }

    private void saveWhitelist() {
        Set<String> copy = new HashSet<>(whitelist);
        executor.execute(() -> {
            RootSettings.CommandResult result = RootSettings.writeWhitelist(copy);
            runOnUiThread(() -> {
                if (result.exitCode == 0) {
                    rootText.setText("Root 已授权");
                    rootText.setTextColor(color(R.color.accent));
                    renderEntries();
                    Toast.makeText(this, "白名单已保存", Toast.LENGTH_SHORT).show();
                } else {
                    rootText.setText("白名单写入失败");
                    rootText.setTextColor(color(R.color.warning));
                    Toast.makeText(this, "请授予本应用 Root 权限", Toast.LENGTH_LONG).show();
                    refreshData();
                }
            });
        });
    }

    private boolean isLauncherAliasDisabled() {
        return componentState(ModuleConfig.MAIN_ACTIVITY)
                == PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }

    private void setLauncherAliasVisible(boolean visible) {
        PackageManager packageManager = getPackageManager();
        ComponentName mainActivity = new ComponentName(getPackageName(), ModuleConfig.MAIN_ACTIVITY);
        packageManager.setComponentEnabledSetting(mainActivity,
                visible ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    private int componentState(String className) {
        return getPackageManager().getComponentEnabledSetting(
                new ComponentName(getPackageName(), className));
    }

    private LinearLayout card() {
        return card(false);
    }

    private LinearLayout card(boolean interactive) {
        LinearLayout layout = horizontal(0);
        layout.setPadding(dp(14), dp(12), dp(14), dp(12));
        layout.setBackground(interactiveSurfaceBackground(color(R.color.surface_raised), 12, interactive));
        layout.setElevation(dp(1));
        if (interactive) {
            layout.setClickable(true);
            layout.setFocusable(true);
        }
        return layout;
    }

    private LinearLayout horizontal(int orientation) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private LinearLayout vertical(int orientation) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private GradientDrawable roundDrawable(int fillColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private Drawable interactiveSurfaceBackground(int fillColor, int radiusDp) {
        return interactiveSurfaceBackground(fillColor, radiusDp, true);
    }

    private Drawable interactiveSurfaceBackground(int fillColor, int radiusDp, boolean interactive) {
        GradientDrawable content = roundDrawable(fillColor, radiusDp);
        if (!interactive) {
            return content;
        }
        return new RippleDrawable(rippleColors(), content, roundDrawable(color(R.color.ripple), radiusDp));
    }

    private Drawable rippleIconBackground() {
        GradientDrawable content = new GradientDrawable();
        content.setColor(Color.TRANSPARENT);
        content.setShape(GradientDrawable.OVAL);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(color(R.color.ripple));
        mask.setShape(GradientDrawable.OVAL);
        return new RippleDrawable(rippleColors(), content, mask);
    }

    private ColorStateList rippleColors() {
        return new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_pressed}, new int[]{}},
                new int[]{color(R.color.ripple), Color.TRANSPARENT});
    }

    private LinearLayout.LayoutParams marginParams(int width, int height, int left, int top,
                                                   int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams centeredParams(int width, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int color(int resourceId) {
        return getResources().getColor(resourceId, getTheme());
    }

    private static final class AppEntry {
        final String packageName;
        final List<String> services = new ArrayList<>();
        String label;
        android.graphics.drawable.Drawable icon;

        AppEntry(String packageName) {
            this.packageName = packageName;
            this.label = packageName;
        }
    }
}
