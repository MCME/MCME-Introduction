package com.mcmiddleearth.introduction.velocity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The policy of one config.yml, read again whenever the file changes, so an edit takes effect on
 * the next login without a restart or a command. A symlink is followed: the change of the file it
 * points to counts.
 */
final class VersionPolicyFile {

    private final Path file;
    private final Consumer<VersionPolicy> onChange;
    private String stamp;
    private VersionPolicy policy;

    /**
     * @param onChange told of the policy when it is first read and after each change
     */
    VersionPolicyFile(Path file, Consumer<VersionPolicy> onChange) {
        this.file = file;
        this.onChange = onChange;
    }

    Path file() {
        return file;
    }

    /** The current policy; reads the file again first if it changed since the last call. */
    synchronized VersionPolicy current() {
        String now = stamp();
        if (policy == null || !Objects.equals(now, stamp)) {
            stamp = now;
            policy = VersionPolicy.read(file);
            onChange.accept(policy);
        }
        return policy;
    }

    // What tells one version of the file from the next: its time, size and identity.
    private String stamp() {
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            return attributes.lastModifiedTime().toMillis() + "/" + attributes.size() + "/" + attributes.fileKey();
        } catch (IOException e) {
            return "missing";
        }
    }
}
