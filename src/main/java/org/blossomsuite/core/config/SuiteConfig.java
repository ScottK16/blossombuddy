package org.blossomsuite.core.config;

import net.minecraft.client.MinecraftClient;

public final class SuiteConfig {
   public static final SuiteConfig INSTANCE = new SuiteConfig();
   public static final float HUD_MIN_SCALE = 0.1F;
   public static final float HUD_MAX_SCALE = 2.0F;
   public SuiteConfig.ActivationMode activationMode = SuiteConfig.ActivationMode.MULTIPLAYER_ONLY;
   public SuiteConfig.HudScalingMode hudScalingMode = SuiteConfig.HudScalingMode.CLASSIC;
   public ChatConfig ChatConfig = new ChatConfig();
   public JobsConfig JobsConfig = new JobsConfig();
   public CooldownsConfig CooldownsConfig = new CooldownsConfig();
   public DungeonConfig DungeonConfig = new DungeonConfig();
   public RelayConfig RelayConfig = new RelayConfig();
   public RemoteConfig RemoteConfig = new RemoteConfig();
   public QolConfig QolConfig = new QolConfig();
   public VoteConfig VoteConfig = new VoteConfig();
   public CoordsConfig CoordsConfig = new CoordsConfig();
   public BiomeHudConfig BiomeHudConfig = new BiomeHudConfig();
   public AutoSwapperHudConfig AutoSwapperHudConfig = new AutoSwapperHudConfig();
   public HolePuncherHudConfig HolePuncherHudConfig = new HolePuncherHudConfig();
   public KeybindsConfig KeybindsConfig = new KeybindsConfig();
   public MiningHudConfig MiningHudConfig = new MiningHudConfig();
   public AltResourcesConfig AltResourcesConfig = new AltResourcesConfig();
   public RentalsConfig RentalsConfig = new RentalsConfig();
   private transient boolean dirty = false;

   private SuiteConfig() {
   }

   public boolean isEnabled() {
      return this.activationMode != SuiteConfig.ActivationMode.OFF;
   }

   public boolean isEnabledForCurrentWorld() {
      if (this.activationMode == SuiteConfig.ActivationMode.OFF) {
         return false;
      }

      if (this.activationMode == SuiteConfig.ActivationMode.ON) {
         return true;
      }

      MinecraftClient client = MinecraftClient.getInstance();
      return client == null ? true : !client.isInSingleplayer();
   }

   public void cycleActivationMode() {
      this.activationMode = switch (this.activationMode) {
         case ON -> SuiteConfig.ActivationMode.OFF;
         case MULTIPLAYER_ONLY -> SuiteConfig.ActivationMode.ON;
         case OFF -> SuiteConfig.ActivationMode.MULTIPLAYER_ONLY;
      };
      this.markDirty();
   }

   public String activationModeLabel() {
      return switch (this.activationMode) {
         case ON -> "ON";
         case MULTIPLAYER_ONLY -> "MULTIPLAYER";
         case OFF -> "OFF";
      };
   }

   public void cycleHudScalingMode() {
      this.hudScalingMode = switch (this.hudScalingMode) {
         case CLASSIC, AUTO_FIT -> SuiteConfig.HudScalingMode.GUI_ADAPTIVE;
         case GUI_ADAPTIVE -> SuiteConfig.HudScalingMode.CLASSIC;
      };
      this.markDirty();
   }

   public String hudScalingModeLabel() {
      return switch (this.hudScalingMode) {
         case CLASSIC, AUTO_FIT -> "CLASSIC";
         case GUI_ADAPTIVE -> "GUI-ADAPT";
      };
   }

   public void markDirty() {
      this.dirty = true;
   }

   public boolean isDirty() {
      return this.dirty;
   }

   public void clearDirty() {
      this.dirty = false;
   }

   public enum ActivationMode {
      ON,
      MULTIPLAYER_ONLY,
      OFF;
   }

   public enum HudScalingMode {
      CLASSIC,
      AUTO_FIT,
      GUI_ADAPTIVE;
   }
}
