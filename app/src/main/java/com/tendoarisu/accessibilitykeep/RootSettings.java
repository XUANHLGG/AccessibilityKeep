package com.tendoarisu.accessibilitykeep;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;

final class RootSettings {
    private RootSettings() {
    }

    static boolean isRootAvailable() {
        CommandResult result = run("id", 2500);
        return result.exitCode == 0 && result.stdout.contains("uid=0");
    }

    static String readWhitelist() {
        CommandResult result = run("/system/bin/settings --user 0 get secure "
                + ModuleConfig.SECURE_WHITELIST_KEY, 3000);
        if (result.exitCode != 0) {
            return "";
        }
        return result.stdout.trim();
    }

    static CommandResult writeWhitelist(Set<String> packages) {
        String encoded = ModuleConfig.encodePackages(packages);
        String command;
        if (encoded.isEmpty()) {
            command = "/system/bin/settings --user 0 delete secure "
                    + ModuleConfig.SECURE_WHITELIST_KEY;
        } else {
            command = "/system/bin/settings --user 0 put secure "
                    + ModuleConfig.SECURE_WHITELIST_KEY + " " + shellQuote(encoded);
        }
        return run(command, 5000);
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static CommandResult run(String command, long timeoutMs) {
        Process process = null;
        StringBuilder output = new StringBuilder();
        try {
            process = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return new CommandResult(-1, output.toString());
            }
            return new CommandResult(process.exitValue(), output.toString());
        } catch (Throwable error) {
            if (process != null) {
                process.destroyForcibly();
            }
            return new CommandResult(-1, error.toString());
        }
    }

    static final class CommandResult {
        final int exitCode;
        final String stdout;

        CommandResult(int exitCode, String stdout) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
        }
    }
}
