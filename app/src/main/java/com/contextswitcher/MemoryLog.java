package com.contextswitcher;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;

/// One log line of memory figures, written periodically by [Main] so a field
/// OOM leaves a growth curve in `~/.contextswitcher/logs/` instead of nothing.
/// Field crash 2026-08-19 (Windows): a native `Chunk::new` malloc failure —
/// *native* memory, invisible to heap monitoring, hence the process virtual
/// size and free-RAM figures alongside the heap.
/// [impl->dsn~memory-log~1]
public final class MemoryLog {

    private MemoryLog() {
    }

    public static String line() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        int threads = ManagementFactory.getThreadMXBean().getThreadCount();
        // The extended bean lives in jdk.management (in the jpackage image via
        // addModules); a JVM without it just loses these two figures.
        String os = "";
        if (ManagementFactory.getOperatingSystemMXBean()
                instanceof com.sun.management.OperatingSystemMXBean bean) {
            os = ", process virtual %s, free RAM %s".formatted(
                    mb(bean.getCommittedVirtualMemorySize()), mb(bean.getFreeMemorySize()));
        }
        return "heap %s/%s, non-heap %s%s, %d threads".formatted(
                mb(heap.getUsed()), mb(heap.getCommitted()), mb(nonHeap.getUsed()), os, threads);
    }

    private static String mb(long bytes) {
        return (bytes / (1024 * 1024)) + " MB";
    }
}
