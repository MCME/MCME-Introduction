package com.mcmiddleearth.introduction.velocity;

import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;

/**
 * Reading the backends' config.yml on the proxy, as a tree of YAML nodes.
 * <p>
 * Velocity 4.2.1 bundles SnakeYAML 1.26, while the build compiles against Paper's SnakeYAML 2.x,
 * and their safe constructors differ: code that compiles can fail on the proxy. Composing nodes
 * ({@code new Yaml().compose(reader)}) is the one way to load that both share, and it constructs
 * no objects. It also keeps every scalar as written: an unquoted 26.20 stays "26.20".
 */
final class YamlNodes {

    private YamlNodes() {
    }

    /** The value node under a key of a map, or null. */
    static Node child(MappingNode map, String key) {
        for (NodeTuple tuple : map.getValue()) {
            if (tuple.getKeyNode() instanceof ScalarNode k && key.equals(k.getValue())) {
                return tuple.getValueNode();
            }
        }
        return null;
    }

    /** A scalar under a key of a map, as written, or the fallback; the map may be null. */
    static String scalar(MappingNode map, String key, String fallback) {
        if (map != null && child(map, key) instanceof ScalarNode value) {
            return value.getValue();
        }
        return fallback;
    }

    /** A text under a key of a map, or the fallback when it is missing or blank; the map may be null. */
    static String text(MappingNode map, String key, String fallback) {
        String value = scalar(map, key, fallback);
        return value.isBlank() ? fallback : value;
    }

    /**
     * Whether a scalar says false the way YAML 1.1, and so Bukkit's config, does: false, no or off,
     * in any case.
     */
    static boolean isFalse(String value) {
        String word = value.strip().toLowerCase(java.util.Locale.ROOT);
        return word.equals("false") || word.equals("no") || word.equals("off");
    }

    static String firstLine(String text) {
        if (text == null) {
            return "";
        }
        int newline = text.indexOf('\n');
        return newline < 0 ? text : text.substring(0, newline);
    }
}
