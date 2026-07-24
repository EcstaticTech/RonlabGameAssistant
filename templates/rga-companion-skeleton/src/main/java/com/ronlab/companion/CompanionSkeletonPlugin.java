package com.ronlab.companion;

import com.ronlab.companion.listener.GameLifecycleListener;
import org.bukkit.plugin.java.JavaPlugin;

public class CompanionSkeletonPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("RGA Companion Skeleton Plugin initializing...");
        getServer().getPluginManager().registerEvents(new GameLifecycleListener(this), this);
        getLogger().info("Registered GameLifecycleListener for RGA events.");
    }

    @Override
    public void onDisable() {
        getLogger().info("RGA Companion Skeleton Plugin disabled.");
    }
}
