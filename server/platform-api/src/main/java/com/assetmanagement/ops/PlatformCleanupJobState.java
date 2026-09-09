package com.assetmanagement.ops;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

final class PlatformCleanupJobState {

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.idle());

    Snapshot get() {
        return snapshot.get();
    }

    void start(List<String> targets) {
        snapshot.set(new Snapshot(
                "running",
                Instant.now(),
                null,
                targets.isEmpty() ? null : targets.getFirst(),
                0,
                List.of(),
                null,
                targets
        ));
    }

    void progress(String currentTable, long deleted, List<Map<String, Object>> items) {
        Snapshot current = snapshot.get();
        snapshot.set(new Snapshot(
                "running",
                current.startedAt(),
                null,
                currentTable,
                deleted,
                copy(items),
                null,
                current.targets()
        ));
    }

    void succeed(long deleted, List<Map<String, Object>> items) {
        Snapshot current = snapshot.get();
        snapshot.set(new Snapshot(
                "succeeded",
                current.startedAt(),
                Instant.now(),
                null,
                deleted,
                copy(items),
                null,
                current.targets()
        ));
    }

    void fail(String error, long deleted, List<Map<String, Object>> items) {
        Snapshot current = snapshot.get();
        snapshot.set(new Snapshot(
                "failed",
                current.startedAt(),
                Instant.now(),
                current.currentTable(),
                deleted,
                copy(items),
                error,
                current.targets()
        ));
    }

    Map<String, Object> toMap() {
        return snapshot.get().toMap();
    }

    private static List<Map<String, Object>> copy(List<Map<String, Object>> items) {
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> item : items) {
            copy.add(new LinkedHashMap<>(item));
        }
        return List.copyOf(copy);
    }

    record Snapshot(
            String status,
            Instant startedAt,
            Instant finishedAt,
            String currentTable,
            long deleted,
            List<Map<String, Object>> items,
            String error,
            List<String> targets
    ) {
        static Snapshot idle() {
            return new Snapshot("idle", null, null, null, 0, List.of(), null, List.of());
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("status", status);
            map.put("startedAt", startedAt == null ? null : startedAt.toString());
            map.put("finishedAt", finishedAt == null ? null : finishedAt.toString());
            map.put("currentTable", currentTable);
            map.put("deleted", deleted);
            map.put("total", deleted);
            map.put("items", items);
            map.put("error", error);
            map.put("targets", targets);
            map.put("running", "running".equals(status));
            return map;
        }
    }
}
