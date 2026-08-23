package com.tendoarisu.accessibilitykeep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ModuleConfig {
    static final String TAG = "A11yKeep";
    static final String SECURE_WHITELIST_KEY = "a11y_keep_whitelist_v1";
    static final String ENABLED_SERVICES_KEY = "enabled_accessibility_services";
    static final String MAIN_ACTIVITY = "com.tendoarisu.accessibilitykeep.MainActivity";

    private ModuleConfig() {
    }

    static Set<String> parsePackages(String raw) {
        if (raw == null || raw.trim().isEmpty() || "null".equalsIgnoreCase(raw.trim())) {
            return new HashSet<>();
        }
        Set<String> result = new HashSet<>();
        String[] values = raw.split("[,;:\\s]+", -1);
        for (String value : values) {
            String packageName = value.trim();
            if (isPackageName(packageName)) {
                result.add(packageName);
            }
        }
        return result;
    }

    static boolean isPackageName(String value) {
        return value != null && value.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");
    }

    static String encodePackages(Set<String> packages) {
        List<String> values = new ArrayList<>();
        if (packages != null) {
            for (String packageName : packages) {
                if (isPackageName(packageName)) {
                    values.add(packageName);
                }
            }
        }
        Collections.sort(values);
        return String.join(",", values);
    }
}
