package com.azuriom.azlink.bukkit;

import com.azuriom.azlink.bukkit.command.BukkitCommandExecutor;
import com.azuriom.azlink.bukkit.command.BukkitCommandSender;
import com.azuriom.azlink.bukkit.injector.InjectedHttpServer;
import com.azuriom.azlink.bukkit.injector.NettyLibraryLoader;
import com.azuriom.azlink.bukkit.integrations.AuthMeIntegration;
import com.azuriom.azlink.bukkit.integrations.FoliaSchedulerAdapter;
import com.azuriom.azlink.bukkit.integrations.MoneyPlaceholderExpansion;
import com.azuriom.azlink.bukkit.integrations.SkinRestorerIntegration;
import com.azuriom.azlink.common.AzLinkPlatform;
import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.command.CommandSender;
import com.azuriom.azlink.common.data.WorldData;
import com.azuriom.azlink.common.http.server.HttpServer;
import com.azuriom.azlink.common.logger.JavaLoggerAdapter;
import com.azuriom.azlink.common.logger.LoggerAdapter;
import com.azuriom.azlink.common.platform.PlatformInfo;
import com.azuriom.azlink.common.platform.PlatformType;
import com.azuriom.azlink.common.scheduler.JavaSchedulerAdapter;
import com.azuriom.azlink.common.scheduler.SchedulerAdapter;
import com.azuriom.azlink.common.tasks.TpsTask;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

public final class AzLinkBukkitPlugin extends JavaPlugin implements AzLinkPlatform {

    private final TpsTask tpsTask = new TpsTask();
    private final SchedulerAdapter scheduler = createSchedulerAdapter();

    private AzLinkPlugin plugin;
    private LoggerAdapter logger;

    @Override
    public void onLoad() {
        this.logger = new JavaLoggerAdapter(getLogger());
    }

    @Override
    public void onEnable() {
        try {
            Class.forName("com.google.gson.JsonObject");
            Class.forName("io.netty.channel.Channel");
        } catch (ClassNotFoundException e) {
            this.logger.error("Your server version is not compatible with this version of AzLink !");
            this.logger.error("Please download AzLink Legacy on https://azuriom.com/azlink");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.plugin = new AzLinkPlugin(this) {
            @Override
            protected HttpServer createHttpServer() {
                NettyLibraryLoader libraryLoader = new NettyLibraryLoader(this);

                try {
                    libraryLoader.loadRequiredLibraries();
                } catch (Exception e) {
                    getLogger().error("Unable to load required libraries for instant commands", e);
                    return null;
                }

                if (plugin.getConfig().getHttpPort() == getServer().getPort()) {
                    return new InjectedHttpServer(AzLinkBukkitPlugin.this);
                }

                return super.createHttpServer();
            }
        };
        this.plugin.init();

        getCommand("azlink").setExecutor(new BukkitCommandExecutor(this.plugin));

        scheduleTpsTask();

        saveDefaultConfig();

        if (getConfig().getBoolean("authme-integration")
                && getServer().getPluginManager().getPlugin("AuthMe") != null) {
            getServer().getPluginManager().registerEvents(new AuthMeIntegration(this), this);
        }

        if (getConfig().getBoolean("skinrestorer-integration")
                && getServer().getPluginManager().getPlugin("SkinsRestorer") != null) {
            getServer().getPluginManager().registerEvents(new SkinRestorerIntegration(this), this);
        }

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            MoneyPlaceholderExpansion.enable(this);
        }
    }

    @Override
    public void onDisable() {
        if (this.plugin != null) {
            this.plugin.shutdown();
        }
    }

    @Override
    public AzLinkPlugin getPlugin() {
        return this.plugin;
    }

    @Override
    public LoggerAdapter getLoggerAdapter() {
        return this.logger;
    }

    @Override
    public SchedulerAdapter getSchedulerAdapter() {
        return this.scheduler;
    }

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.BUKKIT;
    }

    @Override
    public PlatformInfo getPlatformInfo() {
        return new PlatformInfo(getServer().getName(), getServer().getVersion());
    }

    @Override
    @SuppressWarnings("deprecation") // Folia support
    public String getPluginVersion() {
        return getDescription().getVersion();
    }

    @Override
    public Path getDataDirectory() {
        return getDataFolder().toPath();
    }

    @Override
    public Optional<WorldData> getWorldData() {
        int loadedChunks = getServer().getWorlds().stream()
                .mapToInt(w -> w.getLoadedChunks().length)
                .sum();

        int entities = getServer().getWorlds().stream()
                .mapToInt(w -> w.getEntities().size())
                .sum();

        return Optional.of(new WorldData(this.tpsTask.getTps(), loadedChunks, entities));
    }

    @Override
    public Stream<CommandSender> getOnlinePlayers() {
        if (getConfig().getBoolean("ignore-vanished-players", false)) {
            return getServer().getOnlinePlayers()
                    .stream()
                    .filter(this::isPlayerVisible)
                    .map(BukkitCommandSender::new);
        }

        return getServer().getOnlinePlayers().stream().map(BukkitCommandSender::new);
    }

    @Override
    public int getMaxPlayers() {
        return getServer().getMaxPlayers();
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        getServer().dispatchCommand(getServer().getConsoleSender(), command);
    }

    private boolean isPlayerVisible(Player player) {
        for (MetadataValue meta : player.getMetadata("vanished")) {
            if (meta.asBoolean()) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("deprecation") // Folia support
    private void scheduleTpsTask() {
        if (this.scheduler instanceof FoliaSchedulerAdapter) {
            FoliaSchedulerAdapter.scheduleSyncTask(this, this.tpsTask, 1, 1);

            return;
        }

        getServer().getScheduler().runTaskTimer(this, this.tpsTask, 1, 1);
    }

    @SuppressWarnings("deprecation") // Folia support
    private SchedulerAdapter createSchedulerAdapter() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");

            return FoliaSchedulerAdapter.create(this);
        } catch (ClassNotFoundException e) {
            return new JavaSchedulerAdapter(
                    r -> getServer().getScheduler().runTask(this, r),
                    r -> getServer().getScheduler().runTaskAsynchronously(this, r)
            );
        }
    }
}
