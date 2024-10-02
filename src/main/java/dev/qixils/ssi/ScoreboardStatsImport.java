package dev.qixils.ssi;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.stat.ServerStatHandler;
import net.minecraft.stat.Stat;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public class ScoreboardStatsImport implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("ScoreboardStatsImport");
	public static @Nullable ScoreboardStatsImport INSTANCE;

	private final Executor executor = Executors.newSingleThreadExecutor();
	private @Nullable MinecraftServer server;
	private final Map<UUID, GameProfile> webResults = new HashMap<>();

	@Override
	public void onInitialize() {
		INSTANCE = this;
		ServerLifecycleEvents.SERVER_STARTING.register(server -> this.server = server);
	}

	public void importStats(Scoreboard scoreboard, ScoreboardObjective objective, ScoreboardCriterion criterion) {
		if (server == null) return;
		if (!(criterion instanceof Stat<?> stat)) return;

		executor.execute(() -> {
            UserCache userCache = server.getUserCache();
			if (userCache == null) return;
			MinecraftSessionService sessionService = server.getSessionService();
			if (sessionService == null) return;

			Path statsFolder = server.getSavePath(WorldSavePath.STATS);
			try (Stream<Path> statsStream = Files.list(statsFolder)) {
				statsStream.forEach(statsFile -> {
					String fileName = statsFile.getFileName().toString();
					UUID uuid = UUID.fromString(fileName.substring(0, fileName.length() - ".json".length()));
					GameProfile gameProfile = server.getUserCache().getByUuid(uuid)
							.or(() -> {
								if (webResults.containsKey(uuid)) return Optional.ofNullable(webResults.get(uuid));
								var profile = sessionService.fillProfileProperties(new GameProfile(uuid, null), true);
								webResults.put(uuid, profile);
								return Optional.of(profile);
							})
							.orElse(null);
					if (gameProfile == null) return;

					var name = gameProfile.getName();
					if (name == null) return;

					var handler = new ServerStatHandler(server, statsFile.toFile());
					int value = handler.getStat(stat);
					var score = scoreboard.getPlayerScore(name, objective);
					score.setScore(value);
				});

				// TODO: i18n
				broadcastToOps(Text.literal("Imported prior statistic data for ")
						.formatted(Formatting.GRAY, Formatting.ITALIC)
						.append(objective.toHoverableText()));
			} catch (Exception e) {
                LOGGER.warn("Failed to import stats for {}", objective.getName(), e);
				broadcastToOps(Text.literal("Failed to import stats for ")
						.formatted(Formatting.GRAY, Formatting.ITALIC)
						.append(objective.toHoverableText()));
			}
		});
	}

	private void broadcastToOps(Text text) {
		// TODO: send to sender too
		if (server == null) return;
		PlayerManager playerManager = server.getPlayerManager();
		playerManager.broadcast(text, player -> playerManager.isOperator(player.getGameProfile()) ? text : null, false);
	}
}