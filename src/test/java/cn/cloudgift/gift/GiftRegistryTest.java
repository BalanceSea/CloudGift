package cn.cloudgift.gift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GiftRegistryTest {

    @TempDir
    Path dataFolder;

    @Test
    void loadsLegacyAndModularYamlFilesWithDirectoryPriority() throws IOException {
        Files.writeString(dataFolder.resolve("gifts.yml"), """
                gifts:
                  legacy:
                    display-name: Legacy
                    cooldown-hours: 1
                    rewards: []
                  shared:
                    display-name: Legacy shared
                    rewards: []
                """, StandardCharsets.UTF_8);
        Path giftsFolder = Files.createDirectories(dataFolder.resolve("gifts"));
        Files.writeString(giftsFolder.resolve("starter.yml"), """
                gifts:
                  starter:
                    display-name: Starter
                    rewards: []
                  shared:
                    display-name: Modular shared
                    rewards: []
                """, StandardCharsets.UTF_8);
        Files.writeString(giftsFolder.resolve("monthly.yaml"), """
                gifts:
                  monthly:
                    display-name: Monthly
                    cooldown-hours: 24
                    rewards: []
                """, StandardCharsets.UTF_8);

        GiftRegistry registry = new GiftRegistry(dataFolder.toFile(), Logger.getAnonymousLogger());

        assertEquals(4, registry.reload());
        assertTrue(registry.find("legacy").isPresent());
        assertTrue(registry.find("starter").isPresent());
        assertTrue(registry.find("monthly").isPresent());
        assertEquals("Modular shared", registry.find("shared").orElseThrow().displayName());
    }

    @Test
    void savesNewGiftAsAnIndividualFileInsideGiftDirectory() throws IOException {
        GiftRegistry registry = new GiftRegistry(dataFolder.toFile(), Logger.getAnonymousLogger());
        GiftDefinition gift = new GiftDefinition(
                "weekly", "Weekly", "", 3_600_000L, 0,
                List.of(new RewardDefinition.CommandReward("say weekly")));

        registry.save(gift);

        assertTrue(Files.isRegularFile(dataFolder.resolve("gifts/weekly.yml")));
        assertEquals("Weekly", registry.find("weekly").orElseThrow().displayName());
        assertEquals(1, registry.reload());
        assertEquals(1, registry.find("weekly").orElseThrow().rewards().size());
    }

    @Test
    void savesExistingGiftBackToItsOriginalYamlFile() throws IOException {
        Path giftsFolder = Files.createDirectories(dataFolder.resolve("gifts"));
        Path source = giftsFolder.resolve("seasonal.yaml");
        Files.writeString(source, """
                gifts:
                  seasonal:
                    display-name: Before
                    rewards: []
                """, StandardCharsets.UTF_8);
        GiftRegistry registry = new GiftRegistry(dataFolder.toFile(), Logger.getAnonymousLogger());
        registry.reload();

        registry.save(new GiftDefinition("seasonal", "After", "", 0L, 0, List.of()));

        String saved = Files.readString(source, StandardCharsets.UTF_8);
        assertTrue(saved.contains("After"));
        assertFalse(Files.exists(giftsFolder.resolve("seasonal.yml")));
        assertEquals("After", registry.find("seasonal").orElseThrow().displayName());
    }
}
