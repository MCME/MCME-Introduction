package com.mcmiddleearth.introduction.velocity;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.ScalarNode;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Which client versions the proxy lets in: the backends' {@code supportedVersions}, read from the
 * same config.yml, plus the texts of the {@code proxy} section.
 * <p>
 * A policy that can't be read is <em>inactive</em> and lets every version in: a typo in the file
 * must never lock the network.
 */
public final class VersionPolicy {

    static final String PLACEHOLDER = "<supported_versions>";
    static final String DEFAULT_MESSAGE = "Your Minecraft version is unsupported. MCME supports Minecraft "
            + PLACEHOLDER + ". Please switch to one of these in the Minecraft launcher and join again.";
    static final String DEFAULT_SERVER_LIST_VERSION = "Minecraft " + PLACEHOLDER;

    private final List<String> versions;
    private final String problem;
    private final String message;
    private final String serverListVersion;

    private VersionPolicy(List<String> versions, String problem, String message, String serverListVersion) {
        this.versions = List.copyOf(versions);
        this.problem = problem;
        String joined = String.join(", ", versions);
        this.message = message.replace(PLACEHOLDER, joined);
        this.serverListVersion = serverListVersion.replace(PLACEHOLDER, joined);
    }

    private static VersionPolicy inactive(String problem) {
        return new VersionPolicy(List.of(), problem, DEFAULT_MESSAGE, DEFAULT_SERVER_LIST_VERSION);
    }

    /** Reads the policy from a config.yml. Never throws: a file that can't be read gives an inactive policy. */
    public static VersionPolicy read(Path file) {
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return inactive(file + " does not exist. Link it to a backend's plugins/MCME-Introduction/config.yml.");
        } catch (IOException | RuntimeException e) {
            return inactive(file + " can't be read: " + e);
        }
        return parse(content, file.toString());
    }

    /** Parses a config.yml's text; {@code source} names it in problems. */
    static VersionPolicy parse(String content, String source) {
        Node root;
        try {
            root = new Yaml().compose(new StringReader(content));
        } catch (YAMLException e) {
            return inactive(source + " is not valid YAML: " + YamlNodes.firstLine(e.getMessage()));
        }
        if (!(root instanceof MappingNode map)) {
            return inactive(source + " is empty or not a YAML map.");
        }

        MappingNode proxy = YamlNodes.child(map, "proxy") instanceof MappingNode section ? section : null;
        String message = YamlNodes.scalar(proxy, "messageUnsupportedVersion", DEFAULT_MESSAGE);
        String serverListVersion = YamlNodes.scalar(proxy, "serverListVersion", DEFAULT_SERVER_LIST_VERSION);
        if ("false".equalsIgnoreCase(YamlNodes.scalar(proxy, "versionCheck", "true").strip())) {
            return inactive("proxy.versionCheck is false in " + source + ".");
        }

        Node listNode = YamlNodes.child(map, "supportedVersions");
        if (listNode == null) {
            return inactive("there is no supportedVersions in " + source + ".");
        }
        if (!(listNode instanceof SequenceNode list)) {
            return inactive("supportedVersions in " + source + " is not a list.");
        }
        List<String> versions = new ArrayList<>();
        for (Node item : list.getValue()) {
            if (!(item instanceof ScalarNode scalar) || scalar.getValue().isBlank()) {
                return inactive("supportedVersions in " + source + " has an entry that is not a version.");
            }
            versions.add(scalar.getValue().strip());
        }
        if (versions.isEmpty()) {
            // On a backend, [] means "only the server's own version", which the proxy can't know.
            return inactive("supportedVersions in " + source + " is empty.");
        }
        return new VersionPolicy(versions, null, message, serverListVersion);
    }

    /** Whether the check is on. An inactive policy lets every version in. */
    public boolean isActive() {
        return problem == null;
    }

    /** Why the check is off, or null when it is on. */
    public String problem() {
        return problem;
    }

    /** The supported versions as listed; empty when inactive. */
    public List<String> versions() {
        return versions;
    }

    /**
     * Whether a client may join. Releases that share a protocol can't be told apart, so the client
     * is described by all the release names of its protocol, and one listed name lets them all in,
     * as on the backends: "26.1.2" also accepts 26.1 and 26.1.1.
     */
    public boolean accepts(Collection<String> releasesOfClientProtocol) {
        return !isActive() || versions.stream().anyMatch(releasesOfClientProtocol::contains);
    }

    /** The disconnect text for a refused client, placeholder filled in. JSON or plain text. */
    public String message() {
        return message;
    }

    /** The version name for the server list, placeholder filled in. */
    public String serverListVersion() {
        return serverListVersion;
    }
}
