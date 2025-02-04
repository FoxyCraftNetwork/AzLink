package com.azuriom.azlink.velocity;

import com.azuriom.azlink.common.AzLinkPlatform;
import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.command.CommandSender;
import com.azuriom.azlink.common.logger.LoggerAdapter;
import com.azuriom.azlink.common.logger.Slf4jLoggerAdapter;
import com.azuriom.azlink.common.platform.PlatformInfo;
import com.azuriom.azlink.common.platform.PlatformType;
import com.azuriom.azlink.common.scheduler.SchedulerAdapter;
import com.azuriom.azlink.velocity.command.VelocityCommandExecutor;
import com.azuriom.azlink.velocity.command.VelocityCommandSender;
import com.azuriom.azlink.velocity.integrations.LimboAuthIntegration;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.ProxyVersion;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.stream.Stream;

@Plugin(
        id = "azlink",
        name = "AzLink",
        version = "${pluginVersion}",
        description = "The plugin to link your Azuriom website with your server.",
        url = "https://azuriom.com",
        authors = "Azuriom Team",
        dependencies = {
                @Dependency(id = "limboauth", optional = true),
        }
)
public final class AzLinkVelocityPlugin implements AzLinkPlatform {

    private final SchedulerAdapter scheduler = new VelocitySchedulerAdapter(this);

    private final ProxyServer proxy;
    private final Path dataDirectory;
    private final LoggerAdapter logger;

    private AzLinkPlugin plugin;

    @Inject
    public AzLinkVelocityPlugin(ProxyServer proxy, @DataDirectory Path dataDirectory, Logger logger) {
        this.proxy = proxy;
        this.dataDirectory = dataDirectory;
        this.logger = new Slf4jLoggerAdapter(logger);
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            Class.forName("com.velocitypowered.api.command.SimpleCommand");
        } catch (ClassNotFoundException e) {
            this.logger.error("AzLink requires Velocity 1.1.0 or higher");
            this.logger.error("You can download the latest version of Velocity on https://velocitypowered.com/downloads");
            return;
        }

        this.plugin = new AzLinkPlugin(this);
        this.plugin.init();

        this.proxy.getCommandManager()
                .register("azlink", new VelocityCommandExecutor(this.plugin), "azuriomlink");

        if (this.proxy.getPluginManager().getPlugin("limboauth").isPresent()) {
            this.proxy.getEventManager().register(this, new LimboAuthIntegration(this));
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
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
        return PlatformType.VELOCITY;
    }

    @Override
    public PlatformInfo getPlatformInfo() {
        ProxyVersion version = this.proxy.getVersion();

        return new PlatformInfo(version.getName(), version.getVersion());
    }

    @Override
    public String getPluginVersion() {
        return "${pluginVersion}";
    }

    @Override
    public Path getDataDirectory() {
        return this.dataDirectory;
    }

    @Override
    public Stream<CommandSender> getOnlinePlayers() {
        return this.proxy.getAllPlayers().stream().map(VelocityCommandSender::new);
    }

    @Override
    public int getMaxPlayers() {
        return this.proxy.getConfiguration().getShowMaxPlayers();
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        this.proxy.getCommandManager().executeAsync(this.proxy.getConsoleCommandSource(), command);
    }

    public ProxyServer getProxy() {
        return this.proxy;
    }
}
