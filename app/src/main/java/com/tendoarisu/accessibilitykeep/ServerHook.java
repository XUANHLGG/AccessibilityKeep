package com.tendoarisu.accessibilitykeep;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class ServerHook implements IXposedHookLoadPackage {
    private static final String[] TASK_SUPERVISORS = {
            "com.android.server.wm.ActivityTaskSupervisor",
            "com.android.server.p016wm.ActivityTaskSupervisor"
    };
    private static final String[] TASK_CLASSES = {
            "com.android.server.wm.Task",
            "com.android.server.p016wm.Task"
    };

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"android".equals(lpparam.packageName)) {
            return;
        }
        ClassLoader classLoader = lpparam.classLoader;
        hookAccessibilityServiceState(classLoader);
        hookAccessibilitySettingsPersistence(classLoader);
        hookRecentTaskCleanup(classLoader);
        hookStartedServiceCleanup(classLoader);
    }

    private void hookAccessibilityServiceState(ClassLoader classLoader) {
        try {
            Class<?> managerClass = XposedHelpers.findClass(
                    "com.android.server.accessibility.AccessibilityManagerService", classLoader);
            XposedHelpers.findAndHookConstructor(managerClass, Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    WhitelistStore.init((Context) param.args[0]);
                    log("system_server context initialized");
                }
            });

            Class<?> stateClass = XposedHelpers.findClass(
                    "com.android.server.accessibility.AccessibilityUserState", classLoader);
            Class<?> connectionClass = XposedHelpers.findClass(
                    "com.android.server.accessibility.AccessibilityServiceConnection", classLoader);
            XposedHelpers.findAndHookMethod(stateClass, "serviceDisconnectedLocked", connectionClass,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object connection = param.args[0];
                            ComponentName component = componentOf(connection);
                            int userId = intField(param.thisObject, "mUserId", 0);
                            if (component == null || !WhitelistStore.isWhitelisted(
                                    component.getPackageName(), userId)) {
                                return;
                            }
                            WhitelistStore.remember(component, userId);
                            removeFromSet(param.thisObject, "mCrashedServices", component);
                            addToSet(param.thisObject, "mEnabledServices", component);
                            log("protected crashed accessibility service: " + component);
                        }
                    });
            log("accessibility hooks installed");
        } catch (Throwable error) {
            log("accessibility hook failed: " + error);
        }
    }

    private void hookAccessibilitySettingsPersistence(ClassLoader classLoader) {
        try {
            Class<?> managerClass = XposedHelpers.findClass(
                    "com.android.server.accessibility.AccessibilityManagerService", classLoader);
            XposedHelpers.findAndHookMethod(managerClass, "persistComponentNamesToSettingLocked",
                    String.class, Set.class, int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!ModuleConfig.ENABLED_SERVICES_KEY.equals(param.args[0])) {
                                return;
                            }
                            int userId = (Integer) param.args[2];
                            @SuppressWarnings("unchecked")
                            Set<ComponentName> requested = (Set<ComponentName>) param.args[1];
                            Set<ComponentName> current = WhitelistStore.readEnabledComponents(userId);
                            current.addAll(WhitelistStore.getRemembered(userId));
                            for (ComponentName component : current) {
                                if (WhitelistStore.isWhitelisted(component.getPackageName(), userId)) {
                                    requested.add(component);
                                }
                            }
                        }
                    });
            log("accessibility setting persistence hook installed");
        } catch (Throwable error) {
            log("setting persistence hook failed: " + error);
        }
    }

    private void hookRecentTaskCleanup(ClassLoader classLoader) {
        Class<?> supervisorClass = findFirst(classLoader, TASK_SUPERVISORS);
        Class<?> taskClass = findFirst(classLoader, TASK_CLASSES);
        if (supervisorClass == null || taskClass == null) {
            log("recent task classes not found");
            return;
        }
        try {
            XposedHelpers.findAndHookMethod(supervisorClass, "cleanUpRemovedTask", taskClass,
                    boolean.class, boolean.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object task = param.args[0];
                            String packageName = packageOfTask(task);
                            int userId = intField(task, "mUserId", 0);
                            if (packageName != null && WhitelistStore.isWhitelisted(packageName, userId)) {
                                // The caller still removes the task; only its process cleanup is skipped.
                                param.args[1] = false;
                                log("kept process during recent-task removal: " + packageName);
                            }
                        }
                    });
            XposedHelpers.findAndHookMethod(supervisorClass, "killTaskProcessesIfPossible", taskClass,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object task = param.args[0];
                            String packageName = packageOfTask(task);
                            int userId = intField(task, "mUserId", 0);
                            if (packageName != null && WhitelistStore.isWhitelisted(packageName, userId)) {
                                param.setResult(null);
                                log("blocked removed-task process kill: " + packageName);
                            }
                        }
                    });
            log("recent task hooks installed for " + supervisorClass.getName());
        } catch (Throwable error) {
            log("recent task hook failed: " + error);
        }
    }

    private void hookStartedServiceCleanup(ClassLoader classLoader) {
        try {
            Class<?> activeServices = findFirst(classLoader,
                    "com.android.server.am.ActiveServices", "com.android.server.p007am.ActiveServices");
            if (activeServices == null) {
                return;
            }
            XposedHelpers.findAndHookMethod(activeServices, "cleanUpServices", int.class,
                    ComponentName.class, Intent.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            ComponentName component = (ComponentName) param.args[1];
                            int userId = (Integer) param.args[0];
                            if (component != null && WhitelistStore.isWhitelisted(
                                    component.getPackageName(), userId)) {
                                param.setResult(null);
                                log("kept started services during task removal: "
                                        + component.getPackageName());
                            }
                        }
                    });
            log("started-service cleanup hook installed");
        } catch (Throwable error) {
            log("started-service hook failed: " + error);
        }
    }

    private static Class<?> findFirst(ClassLoader classLoader, String... names) {
        for (String name : names) {
            Class<?> result = XposedHelpers.findClassIfExists(name, classLoader);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static ComponentName componentOf(Object connection) {
        try {
            return (ComponentName) XposedHelpers.callMethod(connection, "getComponentName");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String packageOfTask(Object task) {
        try {
            return (String) XposedHelpers.callMethod(task, "getBasePackageName");
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void removeFromSet(Object object, String fieldName, ComponentName value) {
        try {
            Object field = XposedHelpers.getObjectField(object, fieldName);
            if (field instanceof Set) {
                ((Set<ComponentName>) field).remove(value);
            }
        } catch (Throwable error) {
            log("cannot remove " + fieldName + ": " + error);
        }
    }

    @SuppressWarnings("unchecked")
    private static void addToSet(Object object, String fieldName, ComponentName value) {
        try {
            Object field = XposedHelpers.getObjectField(object, fieldName);
            if (field instanceof Set) {
                ((Set<ComponentName>) field).add(value);
            }
        } catch (Throwable error) {
            log("cannot add " + fieldName + ": " + error);
        }
    }

    private static int intField(Object object, String fieldName, int fallback) {
        try {
            return XposedHelpers.getIntField(object, fieldName);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static void log(String message) {
        XposedBridge.log(ModuleConfig.TAG + ": " + message);
    }
}
