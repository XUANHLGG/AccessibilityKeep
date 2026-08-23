package com.tendoarisu.accessibilitykeep;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class WhitelistStore {
    private static final Object LOCK = new Object();
    private static final Map<Integer, Set<ComponentName>> PROTECTED_COMPONENTS = new HashMap<>();
    private static Context context;
    private static Set<String> cachedPackages = new HashSet<>();
    private static String cachedRaw;
    private static long cachedAt;

    private WhitelistStore() {
    }

    static void init(Context serviceContext) {
        synchronized (LOCK) {
            context = serviceContext;
            cachedAt = 0;
        }
    }

    static boolean isWhitelisted(String packageName, int userId) {
        if (packageName == null) {
            return false;
        }
        refresh(userId);
        synchronized (LOCK) {
            return cachedPackages.contains(packageName);
        }
    }

    static void remember(ComponentName componentName, int userId) {
        if (componentName == null) {
            return;
        }
        synchronized (LOCK) {
            Set<ComponentName> components = PROTECTED_COMPONENTS.get(userId);
            if (components == null) {
                components = new HashSet<>();
                PROTECTED_COMPONENTS.put(userId, components);
            }
            components.add(componentName);
        }
    }

    static Set<ComponentName> getRemembered(int userId) {
        synchronized (LOCK) {
            Set<ComponentName> components = PROTECTED_COMPONENTS.get(userId);
            return components == null ? new HashSet<>() : new HashSet<>(components);
        }
    }

    static Set<ComponentName> readEnabledComponents(int userId) {
        String raw;
        synchronized (LOCK) {
            if (context == null) {
                return new HashSet<>();
            }
            try {
                raw = Settings.Secure.getString(
                        context.getContentResolver(), ModuleConfig.ENABLED_SERVICES_KEY);
            } catch (Throwable ignored) {
                return new HashSet<>();
            }
        }
        Set<ComponentName> result = new HashSet<>();
        if (raw == null || raw.isEmpty()) {
            return result;
        }
        String[] values = raw.split(":");
        for (String value : values) {
            ComponentName component = ComponentName.unflattenFromString(value);
            if (component != null) {
                result.add(component);
            }
        }
        return result;
    }

    private static void refresh(int userId) {
        Context currentContext;
        synchronized (LOCK) {
            currentContext = context;
            if (currentContext == null || System.currentTimeMillis() - cachedAt < 750) {
                return;
            }
        }
        String raw = null;
        try {
            raw = Settings.Secure.getString(
                    currentContext.getContentResolver(), ModuleConfig.SECURE_WHITELIST_KEY);
        } catch (Throwable ignored) {
        }
        synchronized (LOCK) {
            cachedRaw = raw;
            cachedPackages = ModuleConfig.parsePackages(raw);
            cachedAt = System.currentTimeMillis();
        }
    }
}
