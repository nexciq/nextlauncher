package com.nextlauncher.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NextLauncher Mod – client-side Fabric mod.
 *
 * What it does:
 *  • Fetches a list of NextLauncher user UUIDs from a public JSON file (NLUserRegistry).
 *  • Appends a green "[NL]" badge to the nametag of any registered player (EntityRendererMixin).
 *  • Registers the local player's UUID with the backend when joining a world.
 *
 * Setup:
 *  1. Edit NLUserRegistry.REGISTRY_URL to point at your nl-users.json on GitHub.
 *  2. (Optional) Set NLUserRegistry.REGISTER_URL to an API endpoint for auto-registration.
 *  3. Build with: ./gradlew build
 *  4. Upload the JAR from build/libs/ to your GitHub release as "nextlauncher-mod-1.0.0.jar".
 *  5. Set NLModInstaller.MOD_URL in the launcher to that release URL.
 */
public class NextLauncherMod implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("nextlauncher-mod");

    @Override
    public void onInitializeClient() {
        LOGGER.info("[NextLauncher] Mod initialized – fetching NL user registry…");

        // Start polling the public registry every 60 s
        NLUserRegistry.startPolling();

        // Register own UUID when joining any world/server
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.player != null) {
                NLUserRegistry.registerSelf(client.player.getUuid());
                LOGGER.info("[NextLauncher] Registered as NL user: {}", client.player.getUuid());
            }
        });
    }
}
