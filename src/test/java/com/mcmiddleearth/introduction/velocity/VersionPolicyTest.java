package com.mcmiddleearth.introduction.velocity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which client versions the proxy lets in, read from the backends' config.yml. A file that can't
 * be read lets every version in: a typo must never lock the network.
 */
class VersionPolicyTest {

    // Velocity's release names per protocol, as ProtocolVersion.getVersionsSupportedBy() gives them.
    private static final List<String> V26_3 = List.of("26.3");
    private static final List<String> V26_2 = List.of("26.2");
    private static final List<String> V26_1 = List.of("26.1", "26.1.1", "26.1.2");
    private static final List<String> V1_21_11 = List.of("1.21.11");

    private static VersionPolicy parse(String yaml) {
        return VersionPolicy.parse(yaml, "config.yml");
    }

    @Test
    void listedVersionsGetInOthersDont() {
        VersionPolicy policy = parse("supportedVersions: ['26.2', '26.3']\n");

        assertTrue(policy.isActive());
        assertNull(policy.problem());
        assertEquals(List.of("26.2", "26.3"), policy.versions());
        assertTrue(policy.accepts(V26_2));
        assertTrue(policy.accepts(V26_3));
        assertFalse(policy.accepts(V26_1));
        assertFalse(policy.accepts(V1_21_11));
    }

    @Test
    void oneNameCoversEveryReleaseOfItsProtocol() {
        // As on the backends: 26.1.2 also covers 26.1 and 26.1.1.
        assertTrue(parse("supportedVersions: ['26.1.2']\n").accepts(V26_1));
    }

    @Test
    void unquotedVersionsStayText() {
        // The YAML is read as nodes, so 26.20 is not read as the number 26.2.
        VersionPolicy policy = parse("supportedVersions: [26.2, 26.20]\n");
        assertEquals(List.of("26.2", "26.20"), policy.versions());
        assertTrue(policy.accepts(V26_2));
    }

    @Test
    void theRealConfigShapeParses() {
        // Other sections, JSON texts with quotes and escapes, a database section: only the two keys matter.
        String yaml = """
                rpMessages:
                  wrongVersion: '{"text":"Press \\"t\\" for help."}'
                secondRoom:
                  messageUnsupportedVanilla: '{"extra":[{"bold":true,"color":"#EDB02B","text":"MCME"}," currently supports: ",{"color":"#44D63C","text":"<supported_versions>"}],"text":""}'
                introductionChain:
                  advancementDisplays:
                  - '{"icon":{"id":"minecraft:clay_ball"},"title":{"text":"Use /fly to fly"}}'

                # Client versions that get no compatibility warning.
                supportedVersions: ['26.2', '26.3']
                database:
                  user: someone
                  password: 'p@ss: "word"'
                """;
        VersionPolicy policy = parse(yaml);
        assertTrue(policy.isActive(), policy.problem());
        assertEquals(List.of("26.2", "26.3"), policy.versions());
    }

    @Test
    void textsHaveTheVersionsFilledIn() {
        VersionPolicy defaults = parse("supportedVersions: ['26.2', '26.3']\n");
        assertTrue(defaults.message().contains("Minecraft 26.2, 26.3."), defaults.message());
        assertTrue(defaults.message().contains("unsupported"), defaults.message());
        assertEquals("Minecraft 26.2, 26.3", defaults.serverListVersion());

        VersionPolicy custom = parse("""
                supportedVersions: ['26.2', '26.3']
                proxy:
                  messageUnsupportedVersion: '{"text":"Use <supported_versions>","color":"red"}'
                  serverListVersion: 'MCME <supported_versions>'
                """);
        assertEquals("{\"text\":\"Use 26.2, 26.3\",\"color\":\"red\"}", custom.message());
        assertEquals("MCME 26.2, 26.3", custom.serverListVersion());
    }

    @Test
    void versionCheckFalseTurnsItOff() {
        VersionPolicy policy = parse("""
                supportedVersions: ['26.2']
                proxy:
                  versionCheck: false
                """);
        assertInactive(policy, "versionCheck");
    }

    @Test
    void everyUnreadableListLetsEveryoneIn() {
        assertInactive(parse("joinDelay: 10\n"), "no supportedVersions");
        assertInactive(parse("supportedVersions: []\n"), "empty");
        assertInactive(parse("supportedVersions: '26.2'\n"), "not a list");
        assertInactive(parse("supportedVersions: ['26.2', [26.3]]\n"), "not a version");
        assertInactive(parse("supportedVersions: ['26.2'\n"), "not valid YAML");
        assertInactive(parse(""), "empty or not a YAML map");
        assertInactive(parse("- 26.2\n"), "empty or not a YAML map");
    }

    @Test
    void aListThisVelocityKnowsNothingOfLetsEveryoneIn() {
        // A typo such as 26.22, or a version newer than this Velocity: no client could match it.
        VersionPolicy policy = VersionPolicy.parse("supportedVersions: ['26.22']\n", "config.yml", release -> false);
        assertInactive(policy, "none of");
    }

    @Test
    void aListWithOneKnownVersionStaysOn() {
        VersionPolicy policy = VersionPolicy.parse("supportedVersions: ['26.2', '26.22']\n", "config.yml", "26.2"::equals);
        assertTrue(policy.isActive(), policy.problem());
        assertEquals(List.of("26.2", "26.22"), policy.versions());
    }

    @Test
    void blankTextsFallBackToTheDefaults() {
        VersionPolicy policy = parse("""
                supportedVersions: ['26.2']
                proxy:
                  messageUnsupportedVersion: ''
                  serverListVersion: '  '
                """);
        assertTrue(policy.message().contains("Minecraft 26.2."), policy.message());
        assertEquals("Minecraft 26.2", policy.serverListVersion());
    }

    @Test
    void offAndNoAlsoTurnTheCheckOff() {
        // As in Bukkit's YAML: off and no are false too.
        assertInactive(parse("supportedVersions: ['26.2']\nproxy:\n  versionCheck: off\n"), "versionCheck");
        assertInactive(parse("supportedVersions: ['26.2']\nproxy:\n  versionCheck: No\n"), "versionCheck");
    }

    @Test
    void aMissingFileLetsEveryoneIn(@TempDir Path dir) {
        assertInactive(VersionPolicy.read(dir.resolve("config.yml")), "does not exist");
    }

    @Test
    void theFileIsReadAgainWhenItChanges(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, "supportedVersions: ['26.2']\n");
        List<VersionPolicy> logged = new ArrayList<>();
        VersionPolicyFile policyFile = new VersionPolicyFile(file, logged::add);

        VersionPolicy first = policyFile.current();
        assertSame(first, policyFile.current(), "unchanged file, same policy");
        assertEquals(1, logged.size());

        Files.writeString(file, "supportedVersions: ['26.2', '26.3']\n");
        assertEquals(List.of("26.2", "26.3"), policyFile.current().versions());
        assertEquals(2, logged.size());
    }

    @Test
    void aLinkedFileIsFollowed(@TempDir Path dir) throws IOException {
        // The proxy's config.yml is a symlink to the backends' file; editing that file must count.
        Path target = dir.resolve("baseline-config.yml");
        Files.writeString(target, "supportedVersions: ['26.2']\n");
        Path link;
        try {
            link = Files.createSymbolicLink(dir.resolve("config.yml"), target);
        } catch (IOException | UnsupportedOperationException e) {
            // Windows without the right to create links: nothing to test here.
            org.junit.jupiter.api.Assumptions.abort("symbolic links are not available: " + e);
            return;
        }
        VersionPolicyFile policyFile = new VersionPolicyFile(link, policy -> { });

        assertEquals(List.of("26.2"), policyFile.current().versions());
        Files.writeString(target, "supportedVersions: ['26.3']\n");
        Files.setLastModifiedTime(target, FileTime.fromMillis(Files.getLastModifiedTime(target).toMillis() + 2000));
        assertEquals(List.of("26.3"), policyFile.current().versions());
    }

    @Test
    void aFileThatAppearsLaterIsPickedUp(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.yml");
        VersionPolicyFile policyFile = new VersionPolicyFile(file, policy -> { });
        assertFalse(policyFile.current().isActive());

        Files.writeString(file, "supportedVersions: ['26.2']\n");
        assertTrue(policyFile.current().isActive());
    }

    private static void assertInactive(VersionPolicy policy, String expectedProblem) {
        assertFalse(policy.isActive());
        assertNotNull(policy.problem());
        assertTrue(policy.problem().contains(expectedProblem), policy.problem());
        assertTrue(policy.accepts(V1_21_11), "an inactive policy lets every version in");
        assertTrue(policy.versions().isEmpty());
    }
}
