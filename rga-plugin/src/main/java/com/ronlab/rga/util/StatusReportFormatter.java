package com.ronlab.rga.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class StatusReportFormatter {

    private StatusReportFormatter() {}

    public record SessionEntry(String id, String worldName, int players) {}

    public record WorldEntry(String name, String state, int players, String gamemode, boolean pvp) {}

    public static List<String> buildLines(
            List<SessionEntry> sessions,
            List<WorldEntry> worlds,
            boolean recoveryPending,
            int pendingRecoveryCount,
            Collection<String> recoveryWorlds) {

        List<String> lines = new ArrayList<>();
        lines.add("======= RGA Status =======");
        lines.add("");

        lines.add("Active sessions:");
        if (sessions.isEmpty()) {
            lines.add(" - None");
        } else {
            for (SessionEntry session : sessions) {
                lines.add(" - " + session.id()
                        + " | World: " + session.worldName()
                        + " | Players: " + session.players());
            }
        }

        lines.add("");
        lines.add("Loaded managed worlds:");
        if (worlds.isEmpty()) {
            lines.add(" - None");
        } else {
            for (WorldEntry world : worlds) {
                lines.add(" - " + world.name()
                        + " | State: " + world.state()
                        + " | Players: " + world.players()
                        + " | Gamemode: " + world.gamemode()
                        + " | PVP: " + (world.pvp() ? "on" : "off"));
            }
        }

        lines.add("");
        lines.add("Recovery state:");
        if (recoveryPending) {
            lines.add(" - Pending orphaned recovery data: yes (" + pendingRecoveryCount + " player(s))");
            if (!recoveryWorlds.isEmpty()) {
                lines.add(" - Recovery worlds: " + String.join(", ", recoveryWorlds));
            }
        } else {
            lines.add(" - Pending orphaned recovery data: no");
        }

        return lines;
    }
}
