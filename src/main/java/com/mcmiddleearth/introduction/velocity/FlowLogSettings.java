package com.mcmiddleearth.introduction.velocity;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The proxy's flow log follows {@code flowLog.enabled} and {@code flowLog.keepDays} of the backends'
 * config.yml, read once when the proxy starts. Whatever can't be read takes the shipped value: on,
 * kept 90 days.
 */
record FlowLogSettings(boolean enabled, int keepDays) {

    static final FlowLogSettings SHIPPED = new FlowLogSettings(true, 90);

    static FlowLogSettings read(Path configFile) {
        Node root;
        try {
            root = new Yaml().compose(new StringReader(Files.readString(configFile, StandardCharsets.UTF_8)));
        } catch (IOException | RuntimeException | StackOverflowError | LinkageError e) {
            // Read while the proxy constructs the plugin: nothing may escape, or the plugin is lost.
            return SHIPPED;
        }
        if (!(root instanceof MappingNode map) || !(YamlNodes.child(map, "flowLog") instanceof MappingNode flowLog)) {
            return SHIPPED;
        }
        boolean enabled = !YamlNodes.isFalse(YamlNodes.scalar(flowLog, "enabled", "true"));
        int keepDays;
        try {
            keepDays = Integer.parseInt(YamlNodes.scalar(flowLog, "keepDays", "90").strip());
        } catch (NumberFormatException e) {
            keepDays = SHIPPED.keepDays();
        }
        return new FlowLogSettings(enabled, keepDays);
    }
}
