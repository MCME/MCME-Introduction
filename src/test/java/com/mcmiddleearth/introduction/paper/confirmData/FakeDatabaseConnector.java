package com.mcmiddleearth.introduction.paper.confirmData;

import java.sql.SQLException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A connector that is set up, with no database behind it: it hands out one row, or none, and
 * records what is written. Writes happen in async tasks, so a test waits for them.
 */
public class FakeDatabaseConnector extends DatabaseConnector {

    private final ConfirmRow row;
    private final BlockingQueue<String> writes = new LinkedBlockingQueue<>();
    private volatile Thread readOn;
    private final AtomicInteger reads = new AtomicInteger();
    private volatile boolean unreachable = false;

    public FakeDatabaseConnector(ConfirmRow row) {
        this.row = row;
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    @Override
    public ConfirmRow readRow(String playerUUID) throws SQLException {
        readOn = Thread.currentThread();
        reads.incrementAndGet();
        if (unreachable) {
            throw new SQLException("Communications link failure");
        }
        return row;
    }

    /** From now on the database can't be reached. */
    public void goUnreachable() {
        unreachable = true;
    }

    /** How many rows were asked for. */
    public int reads() {
        return reads.get();
    }

    @Override
    public void upsertRow(String playerUUID, boolean optifine, boolean ignore) {
        writes.add("optifine=" + optifine + ",ignore=" + ignore);
    }

    @Override
    public void saveOptifine(String playerUUID, boolean optifine) {
        writes.add("optifine=" + optifine);
    }

    @Override
    public void saveIgnore(String playerUUID, boolean ignore) {
        writes.add("ignore=" + ignore);
    }

    /** The thread that last read a row, or null. */
    public Thread readOn() {
        return readOn;
    }

    /** The next write, waiting up to 5 seconds for it; null if none comes. */
    public String nextWrite() throws InterruptedException {
        return writes.poll(5, TimeUnit.SECONDS);
    }

    /** Whether another write comes within a moment. */
    public boolean moreWrites() throws InterruptedException {
        return writes.poll(200, TimeUnit.MILLISECONDS) != null;
    }
}
