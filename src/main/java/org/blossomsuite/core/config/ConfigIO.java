package org.blossomsuite.core.config;

import com.google.gson.JsonParser;
import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.SuiteServer;
import org.blossomsuite.core.chat.ChatChannel;
import org.blossomsuite.core.cooldowns.CooldownsMode;
import org.blossomsuite.core.jobs.JobsActionBarMode;
import org.blossomsuite.core.jobs.JobsAutoSegmentMode;
import org.blossomsuite.core.jobs.JobsMode;
import org.blossomsuite.core.jobs.overflow.OverflowDisplayMode;
import org.blossomsuite.core.keybinds.KeybindUtil;
import org.blossomsuite.core.util.CrosshairShapeRenderer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;

public final class ConfigIO {
   private static Consumer<List<String>> altResourceSnapshotLoader = snapshots -> {};
   private static BiConsumer<String, Throwable> errorReporter = (message, throwable) -> {};
   private static boolean saveBlockedByLoadFailure = false;

   private ConfigIO() {
   }

   public static void setAltResourceSnapshotLoader(Consumer<List<String>> loader) {
      altResourceSnapshotLoader = loader != null ? loader : snapshots -> {};
   }

   public static void setErrorReporter(BiConsumer<String, Throwable> reporter) {
      errorReporter = reporter != null ? reporter : (message, throwable) -> {};
   }

   private static Path file() {
      return FabricLoader.getInstance().getConfigDir().resolve(SuiteRuntime.profile().configFileName());
   }

   public static void load() {
      Path file = file();
      if (!Files.exists(file)) {
         saveBlockedByLoadFailure = false;
         AutoDropperConfigIO.loadInto(SuiteConfig.INSTANCE.QolConfig);
      } else {
         try {
            String json = readValidConfigText(file);
            if (json == null) {
               saveBlockedByLoadFailure = true;
               AutoDropperConfigIO.loadInto(SuiteConfig.INSTANCE.QolConfig);
               return;
            }

            saveBlockedByLoadFailure = false;
            SuiteConfig config = SuiteConfig.INSTANCE;
            config.activationMode = parseEnum(
               extractStringCompat(json, "MULTIPLAYER_ONLY", "general.activationMode", "activationMode"),
               SuiteConfig.ActivationMode.class,
               SuiteConfig.ActivationMode.MULTIPLAYER_ONLY
            );
            config.hudScalingMode = parseEnum(
               extractString(json, "general.hudScalingMode", "CLASSIC"), SuiteConfig.HudScalingMode.class, SuiteConfig.HudScalingMode.CLASSIC
            );
            if (config.hudScalingMode == SuiteConfig.HudScalingMode.AUTO_FIT) {
               config.hudScalingMode = SuiteConfig.HudScalingMode.CLASSIC;
            }

            config.RemoteConfig.enabled = extractBoolCompat(json, true, "remote.enabled", "remoteEnabled");
            config.RemoteConfig.cooldownVersion = extractStringCompat(json, "0", "remote.cooldowns.version", "remoteCooldownVersion");
            config.RemoteConfig.cooldownsSha256 = extractStringCompat(json, "", "remote.cooldowns.sha256", "remoteCooldownsSha256");
            config.RemoteConfig.lastCheckMs = extractLongCompat(json, 0L, "remote.lastCheckMs", "remoteLastCheckMs");
            config.RelayConfig.linkId = extractStringCompat(json, "", "relay.linkId", "linkId");
            config.RelayConfig.publishCooldowns = extractBoolCompat(json, false, "relay.publish", "publishCooldowns");
            config.RelayConfig.subscribeCooldowns = extractBoolCompat(json, false, "relay.subscribe", "subscribeCooldowns");
            config.QolConfig.autoSwapperEnabled = extractBoolCompat(json, false, "qol.autoSwapper", "autoSwapperEnabled");
            config.QolConfig.autoSwapperDebounceMs = extractIntCompat(json, 100, "qol.autoSwapper.debounceMs", "autoSwapperDebounceMs");
            if (config.QolConfig.autoSwapperDebounceMs < 0) {
               config.QolConfig.autoSwapperDebounceMs = 0;
            }

            if (config.QolConfig.autoSwapperDebounceMs > 1000) {
               config.QolConfig.autoSwapperDebounceMs = 1000;
            }

            config.QolConfig.autoSwapperActiveProfile = extractInt(json, "qol.autoSwapper.activeProfile", 0);
            config.QolConfig.autoSwapperProfiles.clear();

            for (String enc : extractStringArrayCompat(json, "qol.autoSwapper.profiles", "autoSwapperProfiles")) {
               QolConfig.AutoSwapperProfile profile = decodeAutoSwapperProfile(enc);
               if (profile != null) {
                  config.QolConfig.autoSwapperProfiles.add(profile);
               }
            }

            if (config.QolConfig.autoSwapperProfiles.isEmpty()) {
               boolean oldUseDefaultTool = extractBoolCompat(json, true, "qol.autoSwapper.useDefaultTool", "autoSwapperUseDefaultTool");
               int oldDefaultSlot = clampSlot(extractIntCompat(json, 1, "qol.autoSwapper.defaultSlot", "autoSwapperDefaultSlot"));
               List<QolConfig.AutoSwapRule> oldRules = new ArrayList<>();

               for (String enc : extractStringArrayCompat(json, "qol.autoSwapper.rules", "autoSwapperRules")) {
                  oldRules.add(decodeAutoSwapRule(enc));
               }

               if (oldUseDefaultTool || !oldRules.isEmpty()) {
                  QolConfig.AutoSwapperProfile profile = new QolConfig.AutoSwapperProfile();
                  profile.name = "Default";
                  profile.useDefaultTool = oldUseDefaultTool;
                  profile.defaultSlot = oldDefaultSlot;
                  profile.rules.addAll(oldRules);
                  config.QolConfig.autoSwapperProfiles.add(profile);
                  config.QolConfig.autoSwapperActiveProfile = 0;
               }
            }

            if (config.QolConfig.autoSwapperActiveProfile < 0) {
               config.QolConfig.autoSwapperActiveProfile = 0;
            }

            if (config.QolConfig.autoSwapperActiveProfile >= config.QolConfig.autoSwapperProfiles.size()) {
               config.QolConfig.autoSwapperActiveProfile = Math.max(0, config.QolConfig.autoSwapperProfiles.size() - 1);
            }

            config.QolConfig.autoSwapperActiveCustomGroup = extractInt(json, "qol.autoSwapper.activeCustomGroup", 0);
            config.QolConfig.autoSwapperCustomGroups.clear();

            for (String enc : extractStringArrayCompat(json, "qol.autoSwapper.customGroups")) {
               QolConfig.AutoSwapCustomGroup group = decodeAutoSwapCustomGroup(enc);
               if (group != null) {
                  config.QolConfig.autoSwapperCustomGroups.add(group);
               }
            }

            if (config.QolConfig.autoSwapperActiveCustomGroup < 0) {
               config.QolConfig.autoSwapperActiveCustomGroup = 0;
            }

            if (config.QolConfig.autoSwapperActiveCustomGroup >= config.QolConfig.autoSwapperCustomGroups.size()) {
               config.QolConfig.autoSwapperActiveCustomGroup = Math.max(0, config.QolConfig.autoSwapperCustomGroups.size() - 1);
            }

            config.QolConfig.autoDropperEnabled = extractBoolCompat(json, false, "qol.autoDropper.enabled", "autoDropperEnabled");
            config.QolConfig.autoDropperOnPickup = extractBoolCompat(json, true, "qol.autoDropper.onPickup", "autoDropperOnPickup");
            config.QolConfig.autoDropperOnSneak = extractBoolCompat(json, false, "qol.autoDropper.onSneak", "autoDropperOnSneak");
            config.QolConfig.autoDropperDelayMs = extractIntCompat(json, 50, "qol.autoDropper.delayMs", "autoDropperDelayMs");
            config.QolConfig.autoDropperMaxStacksPerTick = Math.max(
               1, Math.min(64, extractIntCompat(json, 8, "qol.autoDropper.maxStacksPerTick", "autoDropperMaxStacksPerTick"))
            );
            config.QolConfig.autoDropperIncludeHotbar = extractBoolCompat(json, false, "qol.autoDropper.includeHotbar", "autoDropperIncludeHotbar");
            config.QolConfig.autoDropperProtectSelectedSlot = extractBoolCompat(
               json, true, "qol.autoDropper.protectSelectedSlot", "autoDropperProtectSelectedSlot"
            );
            config.QolConfig.autoDropperPauseWhileScreenOpen = extractBoolCompat(
               json, true, "qol.autoDropper.pauseWhileScreenOpen", "autoDropperPauseWhileScreenOpen"
            );
            config.QolConfig.autoDropperPauseWhileSneaking = extractBoolCompat(
               json, false, "qol.autoDropper.pauseWhileSneaking", "autoDropperPauseWhileSneaking"
            );
            config.QolConfig.autoDropperPauseOnPlayerAttack = extractBoolCompat(
               json, true, "qol.autoDropper.pauseOnPlayerAttack", "autoDropperPauseOnPlayerAttack"
            );
            config.QolConfig.autoDropperPauseOnAttackingPlayer = extractBoolCompat(
               json, true, "qol.autoDropper.pauseOnAttackingPlayer", "autoDropperPauseOnAttackingPlayer"
            );
            config.QolConfig.autoDropperPauseOnTargetingPlayer = extractBoolCompat(
               json, true, "qol.autoDropper.pauseOnTargetingPlayer", "autoDropperPauseOnTargetingPlayer"
            );
            config.QolConfig.autoDropperActiveCustomGroup = extractInt(json, "qol.autoDropper.activeCustomGroup", 0);
            config.QolConfig.autoDropperRules.clear();

            for (String enc : extractStringArrayCompat(json, "qol.autoDropper.rules")) {
               QolConfig.AutoDropRule rule = decodeAutoDropRule(enc);
               if (rule != null) {
                  config.QolConfig.autoDropperRules.add(rule);
               }
            }

            config.QolConfig.autoDropperCustomGroups.clear();

            for (String enc : extractStringArrayCompat(json, "qol.autoDropper.customGroups")) {
               QolConfig.AutoDropCustomGroup group = decodeAutoDropCustomGroup(enc);
               if (group != null) {
                  config.QolConfig.autoDropperCustomGroups.add(group);
               }
            }

            if (config.QolConfig.autoDropperActiveCustomGroup < 0) {
               config.QolConfig.autoDropperActiveCustomGroup = 0;
            }

            if (config.QolConfig.autoDropperActiveCustomGroup >= config.QolConfig.autoDropperCustomGroups.size()) {
               config.QolConfig.autoDropperActiveCustomGroup = Math.max(0, config.QolConfig.autoDropperCustomGroups.size() - 1);
            }

            AutoDropperConfigIO.loadInto(config.QolConfig);
            config.QolConfig.inventorySortEnabled = extractBoolCompat(json, true, "qol.inventorySort.enabled");
            config.QolConfig.inventorySortStackMatching = extractBoolCompat(json, false, "qol.inventorySort.stackMatching");
            config.QolConfig.inventoryManagementShowContainerButtons = extractBoolCompat(json, true, "qol.inventoryManagement.showContainerButtons");
            config.QolConfig.inventoryManagementDepositIgnoresLockedSlots = extractBoolCompat(json, true, "qol.inventoryManagement.depositIgnoresLockedSlots");
            config.QolConfig.inventorySortLockedSlotsMask = extractLongCompat(json, 0L, "qol.inventorySort.lockedSlotsMask");
            if (config.QolConfig.inventorySortLockedSlotsMask < 0L) {
               config.QolConfig.inventorySortLockedSlotsMask = 0L;
            }

            config.QolConfig.inventorySortLockedSlotsMask &= 68719476735L;
            config.QolConfig.autoFlyOnRwWorldLoad = extractBoolCompat(json, false, "qol.autoFly.onRwWorldLoad");
            config.QolConfig.fishingEnabled = extractBool(json, "qol.fishing", false);
            config.QolConfig.fishingVolume = extractFloat(json, "qol.fishing.volume", 0.5F);
            if (config.QolConfig.fishingVolume < 0.0F) {
               config.QolConfig.fishingVolume = 0.0F;
            }

            if (config.QolConfig.fishingVolume > 1.0F) {
               config.QolConfig.fishingVolume = 1.0F;
            }

            config.QolConfig.fishingPitch = extractFloat(json, "qol.fishing.pitch", 0.5F);
            if (config.QolConfig.fishingPitch < 0.5F) {
               config.QolConfig.fishingPitch = 0.5F;
            }

            if (config.QolConfig.fishingPitch > 2.0F) {
               config.QolConfig.fishingPitch = 2.0F;
            }

            config.QolConfig.fishingSoundId = extractString(json, "qol.fishing.soundId", "minecraft:entity.experience_orb.pickup");
            config.QolConfig.fishingCooldownMs = extractInt(json, "qol.fishing.cooldown", 250);
            if (config.QolConfig.fishingCooldownMs < 0) {
               config.QolConfig.fishingCooldownMs = 0;
            }

            if (config.QolConfig.fishingCooldownMs > 1000) {
               config.QolConfig.fishingCooldownMs = 1000;
            }

            config.QolConfig.toolLockEnabled = extractBoolCompat(json, false, "qol.toolLock.enabled", "toolLockEnabled");
            config.QolConfig.toolLockBlockOnInteractBlock = extractBoolCompat(json, false, "qol.toolLock.blockOnBlock", "toolLockBlockOnInteractBlock");
            config.QolConfig.toolLockLockedSlotsMask = extractIntCompat(json, 0, "qol.toolLock.lockedSlotsMask", "toolLockLockedSlotsMask");
            if (config.QolConfig.toolLockLockedSlotsMask < 0) {
               config.QolConfig.toolLockLockedSlotsMask = 0;
            }

            if (config.QolConfig.toolLockLockedSlotsMask > 511) {
               config.QolConfig.toolLockLockedSlotsMask = 511;
            }

            config.QolConfig.toolLockLeftClickLockedSlotsMask = extractInt(json, "qol.toolLock.leftClickLockedSlotsMask", 0);
            if (config.QolConfig.toolLockLeftClickLockedSlotsMask < 0) {
               config.QolConfig.toolLockLeftClickLockedSlotsMask = 0;
            }

            if (config.QolConfig.toolLockLeftClickLockedSlotsMask > 511) {
               config.QolConfig.toolLockLeftClickLockedSlotsMask = 511;
            }

            config.QolConfig.toolLockReportMode = parseEnum(
               extractString(json, "qol.toolLock.reportMode", "NOTICE"), QolConfig.ToolLockReportMode.class, QolConfig.ToolLockReportMode.NOTICE
            );
            config.QolConfig.toolLockActiveProfile = extractInt(json, "qol.toolLock.activeProfile", 0);
            config.QolConfig.toolLockProfiles.clear();

            for (String enc : extractStringArrayCompat(json, "qol.toolLock.profiles")) {
               QolConfig.ToolLockProfile profile = decodeToolLockProfile(enc);
               if (profile != null) {
                  config.QolConfig.toolLockProfiles.add(profile);
               }
            }

            if (config.QolConfig.toolLockProfiles.isEmpty()) {
               QolConfig.ToolLockProfile profile = new QolConfig.ToolLockProfile();
               profile.name = "Default";
               profile.blockOnInteractBlock = config.QolConfig.toolLockBlockOnInteractBlock;
               profile.lockedSlotsMask = QolConfig.sanitizeToolLockMask(config.QolConfig.toolLockLockedSlotsMask);
               profile.leftClickLockedSlotsMask = QolConfig.sanitizeToolLockMask(config.QolConfig.toolLockLeftClickLockedSlotsMask);
               profile.reportMode = config.QolConfig.toolLockReportMode == null ? QolConfig.ToolLockReportMode.NOTICE : config.QolConfig.toolLockReportMode;
               config.QolConfig.toolLockProfiles.add(profile);
               config.QolConfig.toolLockActiveProfile = 0;
            }

            config.QolConfig.applyActiveToolLockProfile();
            if ((config.QolConfig.toolLockLockedSlotsMask != 0 || config.QolConfig.toolLockLeftClickLockedSlotsMask != 0) && !config.QolConfig.toolLockEnabled) {
               config.QolConfig.toolLockEnabled = true;
            }

            config.QolConfig.holePuncherGuided = extractBoolCompat(json, false, "qol.holePuncher.guided", "holePuncherGuided");
            config.QolConfig.holePuncherVisualMode = extractIntCompat(json, 0, "qol.holePuncher.visualMode", "holePuncherVisualMode");
            if (config.QolConfig.holePuncherVisualMode < 0) {
               config.QolConfig.holePuncherVisualMode = 0;
            }

            if (config.QolConfig.holePuncherVisualMode > 1) {
               config.QolConfig.holePuncherVisualMode = 1;
            }

            config.QolConfig.holePuncherEnabled = extractBoolCompat(json, true, "qol.holePuncher.enabled", "holePuncherEnabled");
            config.QolConfig.holePuncherMarkersEnabled = extractBoolCompat(json, true, "qol.holePuncher.markers", "holePuncherMarkersEnabled");
            config.QolConfig.holePuncherAutoSwapSlot = clampSlot(extractIntCompat(json, 1, "qol.holePuncher.autoSwap.slot", "holePuncherAutoSwapSlot"));
            config.QolConfig.holePuncherFailureScreenMessage = extractBoolCompat(
               json, false, "qol.holePuncher.failure.screenMessage", "holePuncherFailureScreenMessage"
            );
            config.QolConfig.holePuncherFailureChatMessage = extractBoolCompat(
               json, false, "qol.holePuncher.failure.chatMessage", "holePuncherFailureChatMessage"
            );
            config.QolConfig.holePuncherFailureNoticeMessage = extractBoolCompat(
               json, false, "qol.holePuncher.failure.noticeMessage", "holePuncherFailureNoticeMessage"
            );
            config.QolConfig.miningResumeAfterDrops = extractBoolCompat(json, true, "qol.mining.resumeAfterDrops", "miningResumeAfterDrops");
            config.QolConfig.miningTrackIndicator = extractBoolCompat(json, false, "qol.miningTrack.indicator", "miningTrackIndicator");
            config.QolConfig.miningTrackDir = extractStringCompat(json, "", "qol.miningTrack.dir", "miningTrackDir");
            config.QolConfig.miningTrackCoord = extractIntCompat(json, 0, "qol.miningTrack.coord", "miningTrackCoord");
            config.QolConfig.miningTrackRangeBlocks = extractIntCompat(json, 0, "qol.miningTrack.rangeBlocks", "miningTrackRangeBlocks");
            if (config.QolConfig.miningTrackRangeBlocks < 0) {
               config.QolConfig.miningTrackRangeBlocks = 0;
            }

            if (config.QolConfig.miningTrackRangeBlocks > 2048) {
               config.QolConfig.miningTrackRangeBlocks = 2048;
            }

            config.QolConfig.miningTrackLineThickness = extractIntCompat(json, 1, "qol.miningTrack.lineThickness", "miningTrackLineThickness");
            if (config.QolConfig.miningTrackLineThickness < 1) {
               config.QolConfig.miningTrackLineThickness = 1;
            }

            if (config.QolConfig.miningTrackLineThickness > 4) {
               config.QolConfig.miningTrackLineThickness = 4;
            }

            if (config.QolConfig.miningTrackDir == null) {
               config.QolConfig.miningTrackDir = "";
            }

            config.QolConfig.miningTrackOnTrackColorMode = parseEnum(
               extractString(json, "qol.miningTrack.onTrack.colorMode", "SOLID"), QolConfig.MiningTrackColorMode.class, QolConfig.MiningTrackColorMode.SOLID
            );
            config.QolConfig.miningTrackOnTrackR = clampColor(extractInt(json, "qol.miningTrack.onTrack.r", 38));
            config.QolConfig.miningTrackOnTrackG = clampColor(extractInt(json, "qol.miningTrack.onTrack.g", 255));
            config.QolConfig.miningTrackOnTrackB = clampColor(extractInt(json, "qol.miningTrack.onTrack.b", 51));
            config.QolConfig.miningTrackOffTrackColorMode = parseEnum(
               extractString(json, "qol.miningTrack.offTrack.colorMode", "SOLID"), QolConfig.MiningTrackColorMode.class, QolConfig.MiningTrackColorMode.SOLID
            );
            config.QolConfig.miningTrackOffTrackR = clampColor(extractInt(json, "qol.miningTrack.offTrack.r", 255));
            config.QolConfig.miningTrackOffTrackG = clampColor(extractInt(json, "qol.miningTrack.offTrack.g", 51));
            config.QolConfig.miningTrackOffTrackB = clampColor(extractInt(json, "qol.miningTrack.offTrack.b", 51));
            config.QolConfig.crosshairTintEnabled = extractBoolCompat(json, false, "qol.crosshair.tint.enabled", "crosshairTintEnabled");
            config.QolConfig.crosshairRainbow = extractBoolCompat(json, false, "qol.crosshair.rainbow", "crosshairRainbow");
            config.QolConfig.crosshairR = extractIntCompat(json, 255, "qol.crosshair.r", "crosshairR");
            config.QolConfig.crosshairG = extractIntCompat(json, 255, "qol.crosshair.g", "crosshairG");
            config.QolConfig.crosshairB = extractIntCompat(json, 255, "qol.crosshair.b", "crosshairB");
            config.QolConfig.crosshairA = extractFloatCompat(json, 1.0F, "qol.crosshair.a", "crosshairA");
            config.QolConfig.crosshairRainbowPeriodMs = extractIntCompat(json, 2000, "qol.crosshair.rainbowPeriodMs", "crosshairRainbowPeriodMs");
            config.QolConfig.crosshairShape = parseEnum(
               extractStringCompat(json, "VANILLA", "qol.crosshair.shape", "crosshairShape"), QolConfig.CrosshairShape.class, QolConfig.CrosshairShape.VANILLA
            );
            config.QolConfig.crosshairSize = extractIntCompat(json, 8, "qol.crosshair.size", "crosshairSize");
            config.QolConfig.crosshairGap = extractIntCompat(json, 2, "qol.crosshair.gap", "crosshairGap");
            config.QolConfig.crosshairThickness = extractIntCompat(json, 2, "qol.crosshair.thickness", "crosshairThickness");
            config.QolConfig.targetBlockOutlineEnabled = extractBoolCompat(json, false, "qol.targetBlockOutline.enabled", "targetBlockOutlineEnabled");
            config.QolConfig.targetBlockOutlineColorMode = parseEnum(
               extractStringCompat(json, "SOLID", "qol.targetBlockOutline.colorMode", "targetBlockOutlineColorMode"),
               QolConfig.TargetBlockOutlineColorMode.class,
               QolConfig.TargetBlockOutlineColorMode.SOLID
            );
            config.QolConfig.targetBlockOutlineR = extractIntCompat(json, 255, "qol.targetBlockOutline.r", "targetBlockOutlineR");
            config.QolConfig.targetBlockOutlineG = extractIntCompat(json, 255, "qol.targetBlockOutline.g", "targetBlockOutlineG");
            config.QolConfig.targetBlockOutlineB = extractIntCompat(json, 255, "qol.targetBlockOutline.b", "targetBlockOutlineB");
            config.QolConfig.targetBlockOutlineA = extractFloatCompat(json, 1.0F, "qol.targetBlockOutline.a", "targetBlockOutlineA");
            if (config.QolConfig.crosshairR < 0) {
               config.QolConfig.crosshairR = 0;
            }

            if (config.QolConfig.crosshairR > 255) {
               config.QolConfig.crosshairR = 255;
            }

            if (config.QolConfig.crosshairG < 0) {
               config.QolConfig.crosshairG = 0;
            }

            if (config.QolConfig.crosshairG > 255) {
               config.QolConfig.crosshairG = 255;
            }

            if (config.QolConfig.crosshairB < 0) {
               config.QolConfig.crosshairB = 0;
            }

            if (config.QolConfig.crosshairB > 255) {
               config.QolConfig.crosshairB = 255;
            }

            if (config.QolConfig.crosshairA < 0.0F) {
               config.QolConfig.crosshairA = 0.0F;
            }

            if (config.QolConfig.crosshairA > 1.0F) {
               config.QolConfig.crosshairA = 1.0F;
            }

            if (config.QolConfig.crosshairRainbowPeriodMs < 250) {
               config.QolConfig.crosshairRainbowPeriodMs = 250;
            }

            if (config.QolConfig.crosshairRainbowPeriodMs > 30000) {
               config.QolConfig.crosshairRainbowPeriodMs = 30000;
            }

            if (config.QolConfig.crosshairSize < 1) {
               config.QolConfig.crosshairSize = 1;
            }

            if (config.QolConfig.crosshairSize > 24) {
               config.QolConfig.crosshairSize = 24;
            }

            if (config.QolConfig.crosshairGap < 0) {
               config.QolConfig.crosshairGap = 0;
            }

            if (config.QolConfig.crosshairGap > 16) {
               config.QolConfig.crosshairGap = 16;
            }

            if (config.QolConfig.crosshairThickness < 1) {
               config.QolConfig.crosshairThickness = 1;
            }

            if (config.QolConfig.crosshairThickness > 8) {
               config.QolConfig.crosshairThickness = 8;
            }

            QolConfig.CrosshairShape shape = config.QolConfig.crosshairShape == null ? QolConfig.CrosshairShape.VANILLA : config.QolConfig.crosshairShape;
            config.QolConfig.crosshairSize = Math.min(config.QolConfig.crosshairSize, CrosshairShapeRenderer.maxSize(shape));
            config.QolConfig.crosshairGap = CrosshairShapeRenderer.supportsGap(shape)
               ? Math.min(config.QolConfig.crosshairGap, CrosshairShapeRenderer.maxGap(shape))
               : 0;
            config.QolConfig.crosshairThickness = CrosshairShapeRenderer.normalizeThickness(shape, config.QolConfig.crosshairThickness);
            config.QolConfig.targetBlockOutlineR = clampColor(config.QolConfig.targetBlockOutlineR);
            config.QolConfig.targetBlockOutlineG = clampColor(config.QolConfig.targetBlockOutlineG);
            config.QolConfig.targetBlockOutlineB = clampColor(config.QolConfig.targetBlockOutlineB);
            if (config.QolConfig.targetBlockOutlineA < 0.0F) {
               config.QolConfig.targetBlockOutlineA = 0.0F;
            }

            if (config.QolConfig.targetBlockOutlineA > 1.0F) {
               config.QolConfig.targetBlockOutlineA = 1.0F;
            }

            config.JobsConfig.showHud = extractBoolCompat(json, true, "jobs.show.hud", "jobsShowHud");
            config.JobsConfig.showStopwatch = extractBoolCompat(json, true, "jobs.show.stopwatch", "jobsShowStopwatch");
            config.JobsConfig.showSegmentLines = extractBoolCompat(json, true, "jobs.show.segmentLines", "jobsShowSegmentLines");
            config.JobsConfig.showSessionLines = extractBoolCompat(json, true, "jobs.show.sessionLines", "jobsShowSessionLines");
            config.JobsConfig.showInChat = extractBoolCompat(json, false, "jobs.show.inChat", "jobsShowInChat", "jobs.show.chat", "jobsShowChat");
            String abMode = extractStringCompat(json, "", "jobs.actionBar.mode", "jobs.actionbar.mode");
            if (abMode != null && !abMode.isBlank()) {
               config.JobsConfig.actionBarMode = parseEnum(abMode, JobsActionBarMode.class, JobsActionBarMode.HIDE);
            } else {
               boolean legacy = extractBoolCompat(json, false, "jobs.show.actionBar", "jobs.show.chat", "jobsShowChat");
               config.JobsConfig.actionBarMode = legacy ? JobsActionBarMode.RUNNING_TOTAL : JobsActionBarMode.HIDE;
            }

            config.JobsConfig.capture = extractBoolCompat(json, true, "jobs.capture", "jobsCapture");
            config.JobsConfig.overflowEnabled = extractBoolCompat(json, true, "jobs.overflow.enabled");
            config.JobsConfig.overflowDisplay = parseEnum(
               extractStringCompat(json, "XP", "jobs.overflow.display"), OverflowDisplayMode.class, OverflowDisplayMode.XP
            );
            config.JobsConfig.overflowMaxLevel = Math.max(1, Math.min(10000, extractIntCompat(json, 200, "jobs.overflow.maxLevel")));
            config.JobsConfig.overflowLevelUpMessage = extractBoolCompat(json, true, "jobs.overflow.levelUpMessage");
            config.JobsConfig.showLifetime = extractBoolCompat(json, true, "jobs.show.lifetime", "jobsLifetime");
            config.JobsConfig.showPauseIconNearCrosshair = extractBoolCompat(json, false, "jobs.show.pauseIconNearCrosshair", "jobsPauseIconNearCrosshair");
            config.JobsConfig.positionX = extractNormalizedFloatCompat(json, 0.5F, "jobs.position.x", "jobsPosX");
            config.JobsConfig.positionY = extractNormalizedFloatCompat(json, 0.5F, "jobs.position.y", "jobsPosY");
            config.JobsConfig.scale = extractFloatCompat(json, 1.0F, "jobs.scale", "jobsScale");
            config.JobsConfig.backgroundOpacity = extractFloat(json, "jobs.backgroundOpacity", 0.33F);
            String jobMode = extractStringCompat(json, "MONEY", "jobs.mode", "jobsMode");
            config.JobsConfig.mode = parseEnum(jobMode, JobsMode.class, JobsMode.MONEY);
            config.JobsConfig.sessionRollover = extractBoolCompat(json, false, "jobs.session.rollover", "jobsRolloverSession");
            config.JobsConfig.sessionRolloverHasData = extractBoolCompat(json, false, "jobs.session.rollover.hasData", "jobsRolloverHasData");
            config.JobsConfig.sessionRolloverActiveMs = extractLongCompat(json, 0L, "jobs.session.rollover.activeMs", "jobsRolloverSessionActiveMs");
            config.JobsConfig.sessionRolloverMoney = extractDoubleCompat(json, 0.0, "jobs.session.rollover.money", "jobsRolloverSessionMoney");
            config.JobsConfig.sessionRolloverExp = extractDoubleCompat(json, 0.0, "jobs.session.rollover.xp", "jobsRolloverSessionExp");
            config.JobsConfig.autoSegment = extractBoolCompat(json, false, "jobs.segment.auto", "jobsAutoSegment");
            config.JobsConfig.autoSegmentInSecs = extractIntCompat(json, 3600, "jobs.segment.auto.inSecs", "jobsAutoSegmentInSecs");
            String segMode = extractStringCompat(json, "TIME_ACTIVE", "jobs.segment.auto.mode", "jobsAutoSegmentMode");
            config.JobsConfig.autoSegmentMode = parseEnum(segMode, JobsAutoSegmentMode.class, JobsAutoSegmentMode.TIME_ACTIVE);
            config.JobsConfig.autoSegmentMoneyThreshold = extractDoubleCompat(json, 100000.0, "jobs.segment.auto.money", "jobsAutoSegmentMoney");
            config.JobsConfig.autoSegmentExpThreshold = extractDoubleCompat(json, 0.0, "jobs.segment.auto.exp", "jobsAutoSegmentExp");
            config.JobsConfig.pauseReminder = extractBoolCompat(json, false, "jobs.pause.reminder", "jobsPauseReminder");
            config.JobsConfig.pauseAutoUnpause = extractBoolCompat(json, false, "jobs.pause.autoUnpause", "jobsPauseAutoUnpause");
            config.JobsConfig.pauseReminderThresholdSecs = extractIntCompat(json, 300, "jobs.pause.thresholdSecs", "jobsPauseReminderThresholdSecs");
            if (config.JobsConfig.pauseReminderThresholdSecs < 60) {
               config.JobsConfig.pauseReminderThresholdSecs = 60;
            }

            if (config.JobsConfig.pauseReminderThresholdSecs > 3600) {
               config.JobsConfig.pauseReminderThresholdSecs = 3600;
            }

            config.JobsConfig.pauseReminderThresholdSecs = config.JobsConfig.pauseReminderThresholdSecs / 60 * 60;
            config.JobsConfig.pauseAutoUnpauseMoneyThreshold = Math.max(
               0.0, extractDoubleCompat(json, 0.0, "jobs.pause.autoUnpause.money", "jobsPauseAutoUnpauseMoney")
            );
            config.JobsConfig.cherryLifetime = extractDoubleCompat(json, 0.0, "jobs.lifetime.cherry", "cherryLifetime");
            config.JobsConfig.spiritLifetime = extractDoubleCompat(json, 0.0, "jobs.lifetime.spirit", "spiritLifetime");
            config.JobsConfig.lotusLifetime = extractDoubleCompat(json, 0.0, "jobs.lifetime.lotus", "lotusLifetime");
            config.JobsConfig.tulipLifetime = extractDoubleCompat(json, 0.0, "jobs.lifetime.tulip", "tulipLifetime");

            for (SuiteServer server : SuiteRuntime.profile().servers()) {
               config.JobsConfig
                  .loadLifetimeForServer(
                     server.key(),
                     extractDoubleCompat(json, config.JobsConfig.lifetimeForServer(server.key()), "jobs.lifetime." + server.key(), server.key() + "Lifetime")
                  );
            }

            config.CooldownsConfig.showHud = extractBoolCompat(json, true, "cooldowns.show.hud", "cooldownsShowHud");
            config.CooldownsConfig.showHotbar = extractBoolCompat(json, true, "cooldowns.show.hotbar", "cooldownsShowHotbar");
            config.CooldownsConfig.hotbarIndicatorStyle = parseEnum(
               extractStringCompat(json, "TOP_BAR", "cooldowns.hotbar.indicatorStyle"),
               CooldownsConfig.HotbarIndicatorStyle.class,
               CooldownsConfig.HotbarIndicatorStyle.TOP_BAR
            );
            config.CooldownsConfig.legacyActiveOnly = extractBoolCompat(json, false, "cooldowns.legacy.activeOnly", "cooldownsLegacyActiveOnly");
            config.CooldownsConfig.trackInventory = extractBoolCompat(json, true, "cooldowns.track.inventory", "cooldownsTrackInventory");
            config.CooldownsConfig.trackOffhand = extractBoolCompat(json, true, "cooldowns.track.offhand", "cooldownsTrackOffhand");
            config.CooldownsConfig.trackArmor = extractBoolCompat(json, true, "cooldowns.track.armor", "cooldownsTrackArmor");
            String invHudMode = extractStringCompat(json, "ONLY_USABLE", "cooldowns.track.inventory.mode", "cooldownsTrackInventoryMode");
            config.CooldownsConfig.inventoryHudMode = parseEnum(
               invHudMode, CooldownsConfig.InventoryHudMode.class, CooldownsConfig.InventoryHudMode.ONLY_USABLE
            );
            config.CooldownsConfig.positionX = extractNormalizedFloatCompat(json, 0.5F, "cooldowns.position.x", "cooldownsPosX");
            config.CooldownsConfig.positionY = extractNormalizedFloatCompat(json, 0.5F, "cooldowns.position.y", "cooldownsPosY");
            config.CooldownsConfig.scale = extractFloatCompat(json, 1.0F, "cooldowns.scale", "cooldownsScale");
            config.CooldownsConfig.backgroundOpacity = extractFloat(json, "cooldowns.backgroundOpacity", 0.33F);
            String cooldownsMode = extractStringCompat(json, "FULL", "cooldowns.mode", "cooldownsHudMode");
            config.CooldownsConfig.mode = parseEnum(cooldownsMode, CooldownsMode.class, CooldownsMode.FULL);
            config.CooldownsConfig.completeSound = extractBoolCompat(json, true, "cooldowns.notify.sound", "cooldownDoneSoundEnabled");
            config.CooldownsConfig.completeSoundVolume = extractFloatCompat(json, 0.6F, "cooldowns.notify.sound.volume", "cooldownDoneSoundVolume");
            config.CooldownsConfig.completeMessage = extractBoolCompat(json, true, "cooldowns.notify.message", "cooldownDoneMessageEnabled");
            config.CooldownsConfig.completeMessageLocation = parseEnum(
               extractString(json, "cooldowns.notify.message.location", "CHAT"),
               CooldownsConfig.CompleteMessageLocation.class,
               CooldownsConfig.CompleteMessageLocation.CHAT
            );
            config.CooldownsConfig.completeThresholdInSecs = extractIntCompat(json, 60, "cooldowns.notify.threshold", "cooldownDoneThresholdSec");
            config.DungeonConfig.enabled = extractBoolCompat(json, true, "dungeon.enabled");
            config.DungeonConfig.showHud = extractBoolCompat(json, true, "dungeon.showHud", "dungeon.showOnCooldownsHud");
            config.DungeonConfig.showHeader = extractBoolCompat(json, true, "dungeon.showHeader");
            config.DungeonConfig.hudServerMode = parseEnum(
               extractStringCompat(json, "CURRENT_SERVER", "dungeon.hudServerMode"),
               DungeonConfig.HudServerMode.class,
               DungeonConfig.HudServerMode.CURRENT_SERVER
            );
            config.DungeonConfig.reportRunsInChat = extractBoolCompat(json, true, "dungeon.reportRunsInChat");
            config.DungeonConfig.reportRunsHttp = true;
            config.DungeonConfig.cooldownHours = Math.max(1, Math.min(72, extractIntCompat(json, 8, "dungeon.cooldownHours")));
            config.DungeonConfig.cooldownTotalMs = config.DungeonConfig.cooldownHours * 60L * 60L * 1000L;
            config.DungeonConfig.positionX = extractNormalizedFloatCompat(json, -1.0F, "dungeon.position.x");
            config.DungeonConfig.positionY = extractNormalizedFloatCompat(json, -1.0F, "dungeon.position.y");
            config.DungeonConfig.scale = extractFloatCompat(json, 1.0F, "dungeon.scale");
            config.DungeonConfig.backgroundOpacity = extractFloatCompat(json, 0.2F, "dungeon.backgroundOpacity");
            if (config.DungeonConfig.backgroundOpacity < 0.0F) {
               config.DungeonConfig.backgroundOpacity = 0.0F;
            }

            if (config.DungeonConfig.backgroundOpacity > 1.0F) {
               config.DungeonConfig.backgroundOpacity = 1.0F;
            }

            config.DungeonConfig.cooldownEndsByServer.clear();
            long legacyDungeonEndsAt = extractLongCompat(json, 0L, "dungeon.cooldownEndsAtMs");
            if (legacyDungeonEndsAt > 0L) {
               config.DungeonConfig.cooldownEndsByServer.put(DungeonConfig.serverKey(null), legacyDungeonEndsAt);
            }

            for (String enc : extractStringArrayCompat(json, "dungeon.cooldownsByServer")) {
               String[] parts = enc == null ? new String[0] : enc.split("\\|", 2);
               if (parts.length == 2) {
                  try {
                     long endsAt = Long.parseLong(parts[1]);
                     if (endsAt > 0L) {
                        config.DungeonConfig.cooldownEndsByServer.merge(DungeonConfig.serverKey(parts[0]), endsAt, Math::max);
                     }
                  } catch (NumberFormatException var16) {
                  }
               }
            }

            config.ChatConfig.showTrackedChannelHud = extractBool(json, "chat.show.trackedChannelHud", false);
            config.ChatConfig.showAdvertisementHud = extractBool(json, "chat.show.advertisementHud", false);
            config.ChatConfig.trackStaffChat = extractBool(json, "chat.track.staffChat", true);
            config.ChatConfig.showStaffHud = extractBool(json, "chat.show.staffHud", true);
            config.ChatConfig.cleanUpCrateMessages = extractBool(json, "chat.show.cleanedUpCrateMessages", true);
            String crateMessageMode = extractString(json, "chat.crateMessages.mode", config.ChatConfig.cleanUpCrateMessages ? "UPDATED" : "NORMAL");
            config.ChatConfig
               .setCrateMessageMode(
                  parseEnum(
                     crateMessageMode,
                     ChatConfig.CrateMessageMode.class,
                     config.ChatConfig.cleanUpCrateMessages ? ChatConfig.CrateMessageMode.UPDATED : ChatConfig.CrateMessageMode.NORMAL
                  )
               );
            config.ChatConfig.hideInventoryFullVoucherMessages = extractBoolCompat(
               json, false, "chat.hide.inventoryFullVoucherMessages", "chat.hideInventoryFullVoucherMessages"
            );
            config.ChatConfig.hideCooldownMessages = extractBoolCompat(json, false, "chat.hide.cooldownMessages", "chat.hideCooldownMessages");
            config.ChatConfig.hideInventoryCompactMessages = extractBoolCompat(
               json, false, "chat.hide.inventoryCompactMessages", "chat.hideInventoryCompactMessages"
            );
            config.ChatConfig.hidePinataBalanceMessages = extractBoolCompat(json, false, "chat.hide.pinataBalanceMessages", "chat.hidePinataBalanceMessages");
            config.ChatConfig.pinataBalanceHideBelowDollars = Math.max(
               0, extractIntCompat(json, 100, "chat.hide.pinataBalanceBelowDollars", "chat.pinataBalanceHideBelowDollars")
            );
            String trackedChannel = extractString(json, "chat.trackedChannel", "UNKNOWN");
            config.ChatConfig.trackedChannel = parseEnum(trackedChannel, ChatChannel.class, ChatChannel.UNKNOWN);
            if (config.ChatConfig.trackedChannel == ChatChannel.STAFF || config.ChatConfig.trackedChannel == ChatChannel.PARTY) {
               config.ChatConfig.trackedChannel = ChatChannel.PUBLIC;
            }

            config.ChatConfig.advertisementMessage = extractString(json, "chat.advertisement.message", "");
            config.ChatConfig.welcomeMessage = extractString(json, "chat.welcome.message", "");
            config.ChatConfig.advertisementReadyAtEpochMs = extractLong(json, "chat.advertisement.readyAt", 0L);
            config.ChatConfig.advertiserActiveProfile = extractIntCompat(
               json, 0, "chat.advertisement.activeProfile", "chat.advertisement.profile.active", "chatAdvertisementActiveProfile"
            );
            config.ChatConfig.advertiserProfiles.clear();

            for (String enc : extractStringArrayCompat(json, "chat.advertisement.profiles", "chatAdvertisementProfiles")) {
               ChatConfig.AdvertiserProfile p = decodeAdvertiserProfile(enc);
               if (p != null) {
                  config.ChatConfig.advertiserProfiles.add(p);
               }
            }

            config.ChatConfig.ensureAdvertiserProfilesInitialized();
            config.ChatConfig.compact = extractBool(json, "chat.trackedChannel.compact", false);
            config.ChatConfig.positionX = extractNormalizedFloat(json, "chat.position.x", 0.5F);
            config.ChatConfig.positionY = extractNormalizedFloat(json, "chat.position.y", 0.5F);
            config.ChatConfig.scale = extractFloat(json, "chat.scale", 1.0F);
            config.ChatConfig.backgroundOpacity = extractFloat(json, "chat.backgroundOpacity", 0.33F);
            loadChord(json, "keybinds.openSettings", config.KeybindsConfig.openSettings);
            loadChord(json, "keybinds.toggleEditMode", config.KeybindsConfig.toggleEditMode);
            loadChord(json, "keybinds.pauseResume", config.KeybindsConfig.pauseResume);
            loadChord(json, "keybinds.resetSession", config.KeybindsConfig.resetSession);
            loadChord(json, "keybinds.resetSegment", config.KeybindsConfig.resetSegment);
            loadChord(json, "keybinds.toggleAutoSwapper", config.KeybindsConfig.toggleAutoSwapper);
            loadChord(json, "keybinds.toggleAutoDropper", config.KeybindsConfig.toggleAutoDropper);
            loadChord(json, "keybinds.toggleRentalsPause", config.KeybindsConfig.toggleRentalsPause);
            loadChord(json, "keybinds.clearExpiredRentals", config.KeybindsConfig.clearExpiredRentals);
            loadChord(json, "keybinds.runAutoDropper", config.KeybindsConfig.runAutoDropper);
            loadChord(json, "keybinds.runCondense", config.KeybindsConfig.runCondense);
            loadChord(json, "keybinds.sortInventory", config.KeybindsConfig.sortInventory);
            loadChord(json, "keybinds.sortContainer", config.KeybindsConfig.sortContainer);
            loadChord(json, "keybinds.sortAll", config.KeybindsConfig.sortAll);
            loadChord(json, "keybinds.depositAllToContainer", config.KeybindsConfig.depositAllToContainer);
            loadChord(json, "keybinds.depositMatchingToContainer", config.KeybindsConfig.depositMatchingToContainer);
            loadChord(json, "keybinds.withdrawAllFromContainer", config.KeybindsConfig.withdrawAllFromContainer);
            loadChord(json, "keybinds.withdrawMatchingFromContainer", config.KeybindsConfig.withdrawMatchingFromContainer);
            loadChord(json, "keybinds.toggleMarryChat", config.KeybindsConfig.toggleMarryChat);
            loadChord(json, "keybinds.toggleHolePuncherMode", config.KeybindsConfig.toggleHolePuncherMode);
            loadChord(json, "keybinds.toggleHolePuncherEnabled", config.KeybindsConfig.toggleHolePuncherEnabled);
            loadChord(json, "keybinds.toggleHolePuncherMarkers", config.KeybindsConfig.toggleHolePuncherMarkers);
            loadChord(json, "keybinds.setMiningTrack", config.KeybindsConfig.setMiningTrack);
            loadChord(json, "keybinds.clearMiningTrack", config.KeybindsConfig.clearMiningTrack);
            loadChord(json, "keybinds.toggleMiningTrackOverlay", config.KeybindsConfig.toggleMiningTrackIndicator);
            loadChord(json, "keybinds.toggleToolLock", config.KeybindsConfig.toggleToolLock);
            loadChord(json, "keybinds.toggleToolLockSlot", config.KeybindsConfig.toggleToolLockSlot);
            loadChord(json, "keybinds.useAdvertiser", config.KeybindsConfig.useAdvertiser);
            loadChord(json, "keybinds.cycleAdvertiserProfile", config.KeybindsConfig.cycleAdvertiserProfile);
            loadChord(json, "keybinds.sendWelcomeMessage", config.KeybindsConfig.sendWelcomeMessage);
            loadChord(json, "keybinds.toggleStaffChat", config.KeybindsConfig.toggleStaffChat);
            config.VoteConfig.showHud = extractBoolCompat(json, false, "vote.show.hud", "voteShowHud");
            config.VoteConfig.sendData = extractBoolCompat(json, false, "vote.send.data", "voteSendData");
            config.VoteConfig.displayCurrentServer = extractBoolCompat(json, false, "vote.display.current.server", "voteDisplayCurrentServer");
            config.VoteConfig.notifyWhen = parseEnum(extractString(json, "vote.notify.when", "OFF"), VoteConfig.NotifyWhen.class, VoteConfig.NotifyWhen.OFF);
            config.VoteConfig.countdownScreenNotification = extractBool(json, "vote.notify.countdownOnScreen", false);
            if (config.VoteConfig.notifyWhen == VoteConfig.NotifyWhen.OFF && config.VoteConfig.countdownScreenNotification) {
               config.VoteConfig.notifyWhen = VoteConfig.NotifyWhen.COUNTDOWN;
            }

            config.VoteConfig.positionX = extractNormalizedFloat(json, "vote.position.x", 0.5F);
            config.VoteConfig.positionY = extractNormalizedFloat(json, "vote.position.y", 0.5F);
            config.VoteConfig.scale = extractFloat(json, "vote.scale", 1.0F);
            config.VoteConfig.backgroundOpacity = extractFloat(json, "vote.backgroundOpacity", 0.0F);
            config.AutoSwapperHudConfig.showHud = extractBoolCompat(json, false, "autoswapperHud.show.hud", "autoswapperHudShowHud");
            config.AutoSwapperHudConfig.showHeader = extractBoolCompat(json, true, "autoswapperHud.show.header", "autoswapperHudShowHeader");
            config.AutoSwapperHudConfig.positionX = extractNormalizedFloat(json, "autoswapperHud.position.x", -1.0F);
            config.AutoSwapperHudConfig.positionY = extractNormalizedFloat(json, "autoswapperHud.position.y", -1.0F);
            config.AutoSwapperHudConfig.scale = extractFloatCompat(json, 1.0F, "autoswapperHud.scale", "autoswapperHudScale");
            config.AutoSwapperHudConfig.backgroundOpacity = extractFloatCompat(
               json, 0.2F, "autoswapperHud.backgroundOpacity", "autoswapperHudBackgroundOpacity"
            );
            if (config.AutoSwapperHudConfig.backgroundOpacity < 0.0F) {
               config.AutoSwapperHudConfig.backgroundOpacity = 0.0F;
            }

            if (config.AutoSwapperHudConfig.backgroundOpacity > 1.0F) {
               config.AutoSwapperHudConfig.backgroundOpacity = 1.0F;
            }

            config.HolePuncherHudConfig.showHud = extractBoolCompat(json, false, "holePuncherHud.show.hud", "holePuncherHudShowHud");
            config.HolePuncherHudConfig.showHeader = extractBoolCompat(json, true, "holePuncherHud.show.header", "holePuncherHudShowHeader");
            config.HolePuncherHudConfig.positionX = extractNormalizedFloat(json, "holePuncherHud.position.x", -1.0F);
            config.HolePuncherHudConfig.positionY = extractNormalizedFloat(json, "holePuncherHud.position.y", -1.0F);
            config.HolePuncherHudConfig.scale = extractFloatCompat(json, 1.0F, "holePuncherHud.scale", "holePuncherHudScale");
            config.HolePuncherHudConfig.backgroundOpacity = extractFloatCompat(
               json, 0.2F, "holePuncherHud.backgroundOpacity", "holePuncherHudBackgroundOpacity"
            );
            if (config.HolePuncherHudConfig.backgroundOpacity < 0.0F) {
               config.HolePuncherHudConfig.backgroundOpacity = 0.0F;
            }

            if (config.HolePuncherHudConfig.backgroundOpacity > 1.0F) {
               config.HolePuncherHudConfig.backgroundOpacity = 1.0F;
            }

            config.MiningHudConfig.showHud = extractBoolCompat(json, false, "miningHud.show.hud", "miningHudShowHud");
            config.MiningHudConfig.positionX = extractNormalizedFloatCompat(json, 0.5F, "miningHud.position.x", "miningHudPosX");
            config.MiningHudConfig.positionY = extractNormalizedFloatCompat(json, 0.55F, "miningHud.position.y", "miningHudPosY");
            config.MiningHudConfig.scale = extractFloatCompat(json, 1.0F, "miningHud.scale", "miningHudScale");
            config.MiningHudConfig.backgroundOpacity = extractFloatCompat(json, 0.15F, "miningHud.backgroundOpacity", "miningHudBackgroundOpacity");
            if (config.MiningHudConfig.backgroundOpacity < 0.0F) {
               config.MiningHudConfig.backgroundOpacity = 0.0F;
            }

            if (config.MiningHudConfig.backgroundOpacity > 1.0F) {
               config.MiningHudConfig.backgroundOpacity = 1.0F;
            }

            config.CoordsConfig.showHud = extractBoolCompat(json, true, "coords.show.hud", "coordsShowHud");
            config.CoordsConfig.showHeader = extractBoolCompat(json, true, "coords.show.header", "coordsShowHeader");
            config.CoordsConfig.positionX = extractNormalizedFloat(json, "coords.position.x", -1.0F);
            config.CoordsConfig.positionY = extractNormalizedFloat(json, "coords.position.y", -1.0F);
            config.CoordsConfig.scale = extractFloat(json, "coords.scale", 1.0F);
            config.CoordsConfig.backgroundOpacity = extractFloat(json, "coords.backgroundOpacity", 0.0F);
            config.BiomeHudConfig.showHud = extractBoolCompat(json, true, "biome.show.hud", "biomeShowHud");
            config.BiomeHudConfig.showHeader = extractBoolCompat(json, true, "biome.show.header", "biomeShowHeader");
            config.BiomeHudConfig.positionX = extractNormalizedFloat(json, "biome.position.x", -1.0F);
            config.BiomeHudConfig.positionY = extractNormalizedFloat(json, "biome.position.y", -1.0F);
            config.BiomeHudConfig.scale = extractFloat(json, "biome.scale", 1.0F);
            config.BiomeHudConfig.backgroundOpacity = extractFloat(json, "biome.backgroundOpacity", 0.0F);
            config.RentalsConfig.entries.clear();

            for (String enc : extractStringArrayCompat(json, "rentals.entries")) {
               RentalsConfig.RentalEntry entry = decodeRentalEntry(enc);
               if (entry != null) {
                  config.RentalsConfig.entries.add(entry);
               }
            }

            config.RentalsConfig.showHud = extractBoolCompat(json, true, "rentals.show.hud", "rentalsShowHud");
            config.RentalsConfig.positionX = extractNormalizedFloatCompat(json, -1.0F, "rentals.position.x", "rentalsPosX");
            config.RentalsConfig.positionY = extractNormalizedFloatCompat(json, -1.0F, "rentals.position.y", "rentalsPosY");
            config.RentalsConfig.scale = extractFloatCompat(json, 1.0F, "rentals.scale", "rentalsScale");
            config.RentalsConfig.backgroundOpacity = extractFloatCompat(json, 0.2F, "rentals.backgroundOpacity", "rentalsBackgroundOpacity");
            if (config.RentalsConfig.backgroundOpacity < 0.0F) {
               config.RentalsConfig.backgroundOpacity = 0.0F;
            }

            if (config.RentalsConfig.backgroundOpacity > 1.0F) {
               config.RentalsConfig.backgroundOpacity = 1.0F;
            }

            config.AltResourcesConfig.snapshots = extractStringArrayCompat(json, "alts.resources");
            altResourceSnapshotLoader.accept(config.AltResourcesConfig.snapshots);
            config.clearDirty();
         } catch (Exception e) {
            saveBlockedByLoadFailure = true;
            errorReporter.accept("[ConfigIO] Failed to load config from " + file + ": " + e, e);
         }
      }
   }

   public static void save() {
      try {
         Path file = file();
         SuiteConfig config = SuiteConfig.INSTANCE;
         config.QolConfig.syncActiveToolLockProfile();
         String json = "{\n  \"general.activationMode\": \""
            + config.activationMode
            + "\",\n  \"remote.enabled\": "
            + config.RemoteConfig.enabled
            + ",\n  \"remote.cooldowns.version\": \""
            + config.RemoteConfig.cooldownVersion
            + "\",\n  \"remote.cooldowns.sha256\": \""
            + config.RemoteConfig.cooldownsSha256
            + "\",\n  \"remote.lastCheckMs\": "
            + Math.max(0L, config.RemoteConfig.lastCheckMs)
            + ",\n  \"relay.linkId\": \""
            + config.RelayConfig.linkId
            + "\",\n  \"relay.publish\": "
            + config.RelayConfig.publishCooldowns
            + ",\n  \"relay.subscribe\": "
            + config.RelayConfig.subscribeCooldowns
            + ",\n  \"qol.autoSwapper\": "
            + config.QolConfig.autoSwapperEnabled
            + ",\n  \"qol.autoSwapper.debounceMs\": "
            + config.QolConfig.autoSwapperDebounceMs
            + ",\n  \"qol.autoSwapper.activeProfile\": "
            + config.QolConfig.autoSwapperActiveProfile
            + ",\n  \"qol.autoSwapper.profiles\": ["
            + encodeProfilesJson(config.QolConfig)
            + "],\n  \"qol.autoSwapper.activeCustomGroup\": "
            + config.QolConfig.autoSwapperActiveCustomGroup
            + ",\n  \"qol.autoSwapper.customGroups\": ["
            + encodeAutoSwapCustomGroupsJson(config.QolConfig)
            + "],\n  \"qol.inventorySort.enabled\": "
            + config.QolConfig.inventorySortEnabled
            + ",\n  \"qol.inventorySort.stackMatching\": "
            + config.QolConfig.inventorySortStackMatching
            + ",\n  \"qol.inventoryManagement.showContainerButtons\": "
            + config.QolConfig.inventoryManagementShowContainerButtons
            + ",\n  \"qol.inventoryManagement.depositIgnoresLockedSlots\": "
            + config.QolConfig.inventoryManagementDepositIgnoresLockedSlots
            + ",\n  \"qol.inventorySort.lockedSlotsMask\": "
            + Math.max(0L, config.QolConfig.inventorySortLockedSlotsMask & 68719476735L)
            + ",\n  \"qol.autoFly.onRwWorldLoad\": "
            + config.QolConfig.autoFlyOnRwWorldLoad
            + ",\n  \"qol.fishing\": "
            + config.QolConfig.fishingEnabled
            + ",\n  \"qol.fishing.volume\": "
            + config.QolConfig.fishingVolume
            + ",\n  \"qol.fishing.pitch\": "
            + config.QolConfig.fishingPitch
            + ",\n  \"qol.fishing.soundId\": \""
            + config.QolConfig.fishingSoundId
            + "\",\n  \"qol.fishing.cooldown\": "
            + config.QolConfig.fishingCooldownMs
            + ",\n  \"qol.toolLock.enabled\": "
            + config.QolConfig.toolLockEnabled
            + ",\n  \"qol.toolLock.blockOnBlock\": "
            + config.QolConfig.toolLockBlockOnInteractBlock
            + ",\n  \"qol.toolLock.lockedSlotsMask\": "
            + Math.max(0, Math.min(511, config.QolConfig.toolLockLockedSlotsMask))
            + ",\n  \"qol.toolLock.leftClickLockedSlotsMask\": "
            + Math.max(0, Math.min(511, config.QolConfig.toolLockLeftClickLockedSlotsMask))
            + ",\n  \"qol.toolLock.reportMode\": \""
            + (config.QolConfig.toolLockReportMode == null ? QolConfig.ToolLockReportMode.NOTICE : config.QolConfig.toolLockReportMode).name()
            + "\",\n  \"qol.toolLock.activeProfile\": "
            + Math.max(0, config.QolConfig.toolLockActiveProfile)
            + ",\n  \"qol.toolLock.profiles\": ["
            + encodeToolLockProfilesJson(config.QolConfig)
            + "],\n  \"qol.holePuncher.guided\": "
            + config.QolConfig.holePuncherGuided
            + ",\n  \"qol.holePuncher.visualMode\": "
            + config.QolConfig.holePuncherVisualMode
            + ",\n  \"qol.holePuncher.enabled\": "
            + config.QolConfig.holePuncherEnabled
            + ",\n  \"qol.holePuncher.markers\": "
            + config.QolConfig.holePuncherMarkersEnabled
            + ",\n  \"qol.holePuncher.autoSwap.slot\": "
            + clampSlot(config.QolConfig.holePuncherAutoSwapSlot)
            + ",\n  \"qol.holePuncher.failure.screenMessage\": "
            + config.QolConfig.holePuncherFailureScreenMessage
            + ",\n  \"qol.holePuncher.failure.chatMessage\": "
            + config.QolConfig.holePuncherFailureChatMessage
            + ",\n  \"qol.holePuncher.failure.noticeMessage\": "
            + config.QolConfig.holePuncherFailureNoticeMessage
            + ",\n  \"qol.mining.resumeAfterDrops\": "
            + config.QolConfig.miningResumeAfterDrops
            + ",\n  \"qol.miningTrack.indicator\": "
            + config.QolConfig.miningTrackIndicator
            + ",\n  \"qol.miningTrack.dir\": \""
            + escapeJson(config.QolConfig.miningTrackDir)
            + "\",\n  \"qol.miningTrack.coord\": "
            + config.QolConfig.miningTrackCoord
            + ",\n  \"qol.miningTrack.rangeBlocks\": "
            + Math.max(0, Math.min(2048, config.QolConfig.miningTrackRangeBlocks))
            + ",\n  \"qol.miningTrack.lineThickness\": "
            + Math.max(1, Math.min(4, config.QolConfig.miningTrackLineThickness))
            + ",\n  \"qol.miningTrack.onTrack.colorMode\": \""
            + config.QolConfig.miningTrackOnTrackColorMode
            + "\",\n  \"qol.miningTrack.onTrack.r\": "
            + clampColor(config.QolConfig.miningTrackOnTrackR)
            + ",\n  \"qol.miningTrack.onTrack.g\": "
            + clampColor(config.QolConfig.miningTrackOnTrackG)
            + ",\n  \"qol.miningTrack.onTrack.b\": "
            + clampColor(config.QolConfig.miningTrackOnTrackB)
            + ",\n  \"qol.miningTrack.offTrack.colorMode\": \""
            + config.QolConfig.miningTrackOffTrackColorMode
            + "\",\n  \"qol.miningTrack.offTrack.r\": "
            + clampColor(config.QolConfig.miningTrackOffTrackR)
            + ",\n  \"qol.miningTrack.offTrack.g\": "
            + clampColor(config.QolConfig.miningTrackOffTrackG)
            + ",\n  \"qol.miningTrack.offTrack.b\": "
            + clampColor(config.QolConfig.miningTrackOffTrackB)
            + ",\n  \"qol.crosshair.tint.enabled\": "
            + config.QolConfig.crosshairTintEnabled
            + ",\n  \"qol.crosshair.rainbow\": "
            + config.QolConfig.crosshairRainbow
            + ",\n  \"qol.crosshair.r\": "
            + config.QolConfig.crosshairR
            + ",\n  \"qol.crosshair.g\": "
            + config.QolConfig.crosshairG
            + ",\n  \"qol.crosshair.b\": "
            + config.QolConfig.crosshairB
            + ",\n  \"qol.crosshair.a\": "
            + config.QolConfig.crosshairA
            + ",\n  \"qol.crosshair.rainbowPeriodMs\": "
            + config.QolConfig.crosshairRainbowPeriodMs
            + ",\n  \"qol.crosshair.shape\": \""
            + config.QolConfig.crosshairShape
            + "\",\n  \"qol.crosshair.size\": "
            + config.QolConfig.crosshairSize
            + ",\n  \"qol.crosshair.gap\": "
            + config.QolConfig.crosshairGap
            + ",\n  \"qol.crosshair.thickness\": "
            + config.QolConfig.crosshairThickness
            + ",\n  \"qol.targetBlockOutline.enabled\": "
            + config.QolConfig.targetBlockOutlineEnabled
            + ",\n  \"qol.targetBlockOutline.colorMode\": \""
            + config.QolConfig.targetBlockOutlineColorMode
            + "\",\n  \"qol.targetBlockOutline.r\": "
            + config.QolConfig.targetBlockOutlineR
            + ",\n  \"qol.targetBlockOutline.g\": "
            + config.QolConfig.targetBlockOutlineG
            + ",\n  \"qol.targetBlockOutline.b\": "
            + config.QolConfig.targetBlockOutlineB
            + ",\n  \"qol.targetBlockOutline.a\": "
            + config.QolConfig.targetBlockOutlineA
            + ",\n  \"general.hudScalingMode\": \""
            + config.hudScalingMode
            + "\",\n  \"jobs.show.hud\": "
            + config.JobsConfig.showHud
            + ",\n  \"jobs.show.stopwatch\": "
            + config.JobsConfig.showStopwatch
            + ",\n  \"jobs.show.segmentLines\": "
            + config.JobsConfig.showSegmentLines
            + ",\n  \"jobs.show.sessionLines\": "
            + config.JobsConfig.showSessionLines
            + ",\n  \"jobs.show.inChat\": "
            + config.JobsConfig.showInChat
            + ",\n  \"jobs.show.actionBar\": "
            + (config.JobsConfig.actionBarMode != JobsActionBarMode.HIDE)
            + ",\n  \"jobs.actionBar.mode\": \""
            + config.JobsConfig.actionBarMode
            + "\",\n  \"jobs.capture\": "
            + config.JobsConfig.capture
            + ",\n  \"jobs.overflow.enabled\": "
            + config.JobsConfig.overflowEnabled
            + ",\n  \"jobs.overflow.display\": \""
            + config.JobsConfig.overflowDisplay
            + "\",\n  \"jobs.overflow.maxLevel\": "
            + config.JobsConfig.overflowMaxLevel
            + ",\n  \"jobs.overflow.levelUpMessage\": "
            + config.JobsConfig.overflowLevelUpMessage
            + ",\n  \"jobs.show.lifetime\": "
            + config.JobsConfig.showLifetime
            + ",\n  \"jobs.show.pauseIconNearCrosshair\": "
            + config.JobsConfig.showPauseIconNearCrosshair
            + ",\n  \"jobs.position.x\": "
            + config.JobsConfig.positionX
            + ",\n  \"jobs.position.y\": "
            + config.JobsConfig.positionY
            + ",\n  \"jobs.scale\": "
            + config.JobsConfig.scale
            + ",\n  \"jobs.backgroundOpacity\": "
            + config.JobsConfig.backgroundOpacity
            + ",\n  \"jobs.mode\": \""
            + config.JobsConfig.mode
            + "\",\n  \"jobs.session.rollover\": "
            + config.JobsConfig.sessionRollover
            + ",\n  \"jobs.session.rollover.hasData\": "
            + config.JobsConfig.sessionRolloverHasData
            + ",\n  \"jobs.session.rollover.activeMs\": "
            + config.JobsConfig.sessionRolloverActiveMs
            + ",\n  \"jobs.session.rollover.money\": "
            + config.JobsConfig.sessionRolloverMoney
            + ",\n  \"jobs.session.rollover.xp\": "
            + config.JobsConfig.sessionRolloverExp
            + ",\n  \"jobs.segment.auto\": "
            + config.JobsConfig.autoSegment
            + ",\n  \"jobs.segment.auto.inSecs\": "
            + config.JobsConfig.autoSegmentInSecs
            + ",\n  \"jobs.segment.auto.mode\": \""
            + config.JobsConfig.autoSegmentMode
            + "\",\n  \"jobs.segment.auto.money\": "
            + config.JobsConfig.autoSegmentMoneyThreshold
            + ",\n  \"jobs.segment.auto.exp\": "
            + config.JobsConfig.autoSegmentExpThreshold
            + ",\n  \"jobs.pause.reminder\": "
            + config.JobsConfig.pauseReminder
            + ",\n  \"jobs.pause.autoUnpause\": "
            + config.JobsConfig.pauseAutoUnpause
            + ",\n  \"jobs.pause.thresholdSecs\": "
            + config.JobsConfig.pauseReminderThresholdSecs
            + ",\n  \"jobs.pause.autoUnpause.money\": "
            + config.JobsConfig.pauseAutoUnpauseMoneyThreshold
            + ",\n"
            + jobLifetimeJson(config.JobsConfig)
            + "  \"cooldowns.show.hud\": "
            + config.CooldownsConfig.showHud
            + ",\n  \"cooldowns.show.hotbar\": "
            + config.CooldownsConfig.showHotbar
            + ",\n  \"cooldowns.hotbar.indicatorStyle\": \""
            + config.CooldownsConfig.hotbarIndicatorStyle
            + "\",\n  \"cooldowns.legacy.activeOnly\": "
            + config.CooldownsConfig.legacyActiveOnly
            + ",\n  \"cooldowns.track.inventory\": "
            + config.CooldownsConfig.trackInventory
            + ",\n  \"cooldowns.track.offhand\": "
            + config.CooldownsConfig.trackOffhand
            + ",\n  \"cooldowns.track.armor\": "
            + config.CooldownsConfig.trackArmor
            + ",\n  \"cooldowns.track.inventory.mode\": \""
            + config.CooldownsConfig.inventoryHudMode
            + "\",\n  \"cooldowns.position.x\": "
            + config.CooldownsConfig.positionX
            + ",\n  \"cooldowns.position.y\": "
            + config.CooldownsConfig.positionY
            + ",\n  \"cooldowns.scale\": "
            + config.CooldownsConfig.scale
            + ",\n  \"cooldowns.backgroundOpacity\": "
            + config.CooldownsConfig.backgroundOpacity
            + ",\n  \"cooldowns.mode\": \""
            + config.CooldownsConfig.mode
            + "\",\n  \"cooldowns.notify.sound\": "
            + config.CooldownsConfig.completeSound
            + ",\n  \"cooldowns.notify.sound.volume\": "
            + config.CooldownsConfig.completeSoundVolume
            + ",\n  \"cooldowns.notify.message\": "
            + config.CooldownsConfig.completeMessage
            + ",\n  \"cooldowns.notify.message.location\": \""
            + config.CooldownsConfig.completeMessageLocation
            + "\",\n  \"cooldowns.notify.threshold\": "
            + config.CooldownsConfig.completeThresholdInSecs
            + ",\n  \"dungeon.enabled\": "
            + config.DungeonConfig.enabled
            + ",\n  \"dungeon.showHud\": "
            + config.DungeonConfig.showHud
            + ",\n  \"dungeon.showHeader\": "
            + config.DungeonConfig.showHeader
            + ",\n  \"dungeon.hudServerMode\": \""
            + config.DungeonConfig.hudServerMode
            + "\",\n  \"dungeon.reportRunsInChat\": "
            + config.DungeonConfig.reportRunsInChat
            + ",\n  \"dungeon.reportRunsHttp\": "
            + config.DungeonConfig.reportRunsHttp
            + ",\n  \"dungeon.cooldownHours\": "
            + config.DungeonConfig.cooldownHours
            + ",\n  \"dungeon.position.x\": "
            + config.DungeonConfig.positionX
            + ",\n  \"dungeon.position.y\": "
            + config.DungeonConfig.positionY
            + ",\n  \"dungeon.scale\": "
            + config.DungeonConfig.scale
            + ",\n  \"dungeon.backgroundOpacity\": "
            + config.DungeonConfig.backgroundOpacity
            + ",\n  \"dungeon.cooldownsByServer\": ["
            + encodeDungeonCooldownsJson(config.DungeonConfig)
            + "],\n  \"chat.show.trackedChannelHud\": "
            + config.ChatConfig.showTrackedChannelHud
            + ",\n  \"chat.show.advertisementHud\": "
            + config.ChatConfig.showAdvertisementHud
            + ",\n  \"chat.track.staffChat\": "
            + config.ChatConfig.trackStaffChat
            + ",\n  \"chat.show.staffHud\": "
            + config.ChatConfig.showStaffHud
            + ",\n  \"chat.show.cleanedUpCrateMessages\": "
            + (config.ChatConfig.crateMessageMode == ChatConfig.CrateMessageMode.UPDATED)
            + ",\n  \"chat.crateMessages.mode\": \""
            + config.ChatConfig.crateMessageMode
            + "\",\n  \"chat.hide.inventoryFullVoucherMessages\": "
            + config.ChatConfig.hideInventoryFullVoucherMessages
            + ",\n  \"chat.hide.cooldownMessages\": "
            + config.ChatConfig.hideCooldownMessages
            + ",\n  \"chat.hide.inventoryCompactMessages\": "
            + config.ChatConfig.hideInventoryCompactMessages
            + ",\n  \"chat.hide.pinataBalanceMessages\": "
            + config.ChatConfig.hidePinataBalanceMessages
            + ",\n  \"chat.hide.pinataBalanceBelowDollars\": "
            + Math.max(0, config.ChatConfig.pinataBalanceHideBelowDollars)
            + ",\n  \"chat.trackedChannel\": \""
            + config.ChatConfig.trackedChannel
            + "\",\n  \"chat.advertisement.message\": \""
            + escapeJson(config.ChatConfig.advertisementMessage)
            + "\",\n  \"chat.welcome.message\": \""
            + escapeJson(config.ChatConfig.welcomeMessage)
            + "\",\n  \"chat.advertisement.readyAt\": "
            + config.ChatConfig.advertisementReadyAtEpochMs
            + ",\n  \"chat.advertisement.activeProfile\": "
            + Math.max(0, config.ChatConfig.advertiserActiveProfile)
            + ",\n  \"chat.advertisement.profiles\": ["
            + encodeAdvertiserProfilesJson(config.ChatConfig)
            + "],\n  \"chat.trackedChannel.compact\": "
            + config.ChatConfig.compact
            + ",\n  \"chat.position.x\": "
            + config.ChatConfig.positionX
            + ",\n  \"chat.position.y\": "
            + config.ChatConfig.positionY
            + ",\n  \"chat.scale\": "
            + config.ChatConfig.scale
            + ",\n  \"chat.backgroundOpacity\": "
            + config.ChatConfig.backgroundOpacity
            + ",\n"
            + saveChord("keybinds.openSettings", config.KeybindsConfig.openSettings)
            + saveChord("keybinds.toggleEditMode", config.KeybindsConfig.toggleEditMode)
            + saveChord("keybinds.pauseResume", config.KeybindsConfig.pauseResume)
            + saveChord("keybinds.resetSession", config.KeybindsConfig.resetSession)
            + saveChord("keybinds.resetSegment", config.KeybindsConfig.resetSegment)
            + saveChord("keybinds.toggleAutoSwapper", config.KeybindsConfig.toggleAutoSwapper)
            + saveChord("keybinds.toggleAutoDropper", config.KeybindsConfig.toggleAutoDropper)
            + saveChord("keybinds.toggleRentalsPause", config.KeybindsConfig.toggleRentalsPause)
            + saveChord("keybinds.clearExpiredRentals", config.KeybindsConfig.clearExpiredRentals)
            + saveChord("keybinds.runAutoDropper", config.KeybindsConfig.runAutoDropper)
            + saveChord("keybinds.runCondense", config.KeybindsConfig.runCondense)
            + saveChord("keybinds.sortInventory", config.KeybindsConfig.sortInventory)
            + saveChord("keybinds.sortContainer", config.KeybindsConfig.sortContainer)
            + saveChord("keybinds.sortAll", config.KeybindsConfig.sortAll)
            + saveChord("keybinds.depositAllToContainer", config.KeybindsConfig.depositAllToContainer)
            + saveChord("keybinds.depositMatchingToContainer", config.KeybindsConfig.depositMatchingToContainer)
            + saveChord("keybinds.withdrawAllFromContainer", config.KeybindsConfig.withdrawAllFromContainer)
            + saveChord("keybinds.withdrawMatchingFromContainer", config.KeybindsConfig.withdrawMatchingFromContainer)
            + saveChord("keybinds.toggleMarryChat", config.KeybindsConfig.toggleMarryChat)
            + saveChord("keybinds.toggleHolePuncherMode", config.KeybindsConfig.toggleHolePuncherMode)
            + saveChord("keybinds.toggleHolePuncherEnabled", config.KeybindsConfig.toggleHolePuncherEnabled)
            + saveChord("keybinds.toggleHolePuncherMarkers", config.KeybindsConfig.toggleHolePuncherMarkers)
            + saveChord("keybinds.setMiningTrack", config.KeybindsConfig.setMiningTrack)
            + saveChord("keybinds.clearMiningTrack", config.KeybindsConfig.clearMiningTrack)
            + saveChord("keybinds.toggleMiningTrackOverlay", config.KeybindsConfig.toggleMiningTrackIndicator)
            + saveChord("keybinds.toggleToolLock", config.KeybindsConfig.toggleToolLock)
            + saveChord("keybinds.toggleToolLockSlot", config.KeybindsConfig.toggleToolLockSlot)
            + saveChord("keybinds.useAdvertiser", config.KeybindsConfig.useAdvertiser)
            + saveChord("keybinds.cycleAdvertiserProfile", config.KeybindsConfig.cycleAdvertiserProfile)
            + saveChord("keybinds.sendWelcomeMessage", config.KeybindsConfig.sendWelcomeMessage)
            + saveChord("keybinds.toggleStaffChat", config.KeybindsConfig.toggleStaffChat)
            + "  \"vote.show.hud\": "
            + config.VoteConfig.showHud
            + ",\n  \"vote.send.data\": "
            + config.VoteConfig.sendData
            + ",\n  \"vote.display.current.server\": "
            + config.VoteConfig.displayCurrentServer
            + ",\n  \"vote.notify.when\": \""
            + config.VoteConfig.notifyWhen
            + "\",\n  \"vote.notify.countdownOnScreen\": "
            + includesCountdown(config.VoteConfig.notifyWhen)
            + ",\n  \"vote.position.x\": "
            + config.VoteConfig.positionX
            + ",\n  \"vote.position.y\": "
            + config.VoteConfig.positionY
            + ",\n  \"vote.scale\": "
            + config.VoteConfig.scale
            + ",\n  \"vote.backgroundOpacity\": "
            + config.VoteConfig.backgroundOpacity
            + ",\n  \"autoswapperHud.show.hud\": "
            + config.AutoSwapperHudConfig.showHud
            + ",\n  \"autoswapperHud.show.header\": "
            + config.AutoSwapperHudConfig.showHeader
            + ",\n  \"autoswapperHud.position.x\": "
            + config.AutoSwapperHudConfig.positionX
            + ",\n  \"autoswapperHud.position.y\": "
            + config.AutoSwapperHudConfig.positionY
            + ",\n  \"autoswapperHud.scale\": "
            + config.AutoSwapperHudConfig.scale
            + ",\n  \"autoswapperHud.backgroundOpacity\": "
            + config.AutoSwapperHudConfig.backgroundOpacity
            + ",\n  \"holePuncherHud.show.hud\": "
            + config.HolePuncherHudConfig.showHud
            + ",\n  \"holePuncherHud.show.header\": "
            + config.HolePuncherHudConfig.showHeader
            + ",\n  \"holePuncherHud.position.x\": "
            + config.HolePuncherHudConfig.positionX
            + ",\n  \"holePuncherHud.position.y\": "
            + config.HolePuncherHudConfig.positionY
            + ",\n  \"holePuncherHud.scale\": "
            + config.HolePuncherHudConfig.scale
            + ",\n  \"holePuncherHud.backgroundOpacity\": "
            + config.HolePuncherHudConfig.backgroundOpacity
            + ",\n  \"miningHud.show.hud\": "
            + config.MiningHudConfig.showHud
            + ",\n  \"miningHud.position.x\": "
            + config.MiningHudConfig.positionX
            + ",\n  \"miningHud.position.y\": "
            + config.MiningHudConfig.positionY
            + ",\n  \"miningHud.scale\": "
            + config.MiningHudConfig.scale
            + ",\n  \"miningHud.backgroundOpacity\": "
            + config.MiningHudConfig.backgroundOpacity
            + ",\n  \"alts.resources\": ["
            + encodeStringArrayJson(config.AltResourcesConfig.snapshots)
            + "],\n  \"coords.show.hud\": "
            + config.CoordsConfig.showHud
            + ",\n  \"coords.position.x\": "
            + config.CoordsConfig.positionX
            + ",\n  \"coords.position.y\": "
            + config.CoordsConfig.positionY
            + ",\n  \"coords.scale\": "
            + config.CoordsConfig.scale
            + ",\n  \"coords.backgroundOpacity\": "
            + config.CoordsConfig.backgroundOpacity
            + ",\n  \"coords.show.header\": "
            + config.CoordsConfig.showHeader
            + ",\n  \"biome.show.hud\": "
            + config.BiomeHudConfig.showHud
            + ",\n  \"biome.position.x\": "
            + config.BiomeHudConfig.positionX
            + ",\n  \"biome.position.y\": "
            + config.BiomeHudConfig.positionY
            + ",\n  \"biome.scale\": "
            + config.BiomeHudConfig.scale
            + ",\n  \"biome.backgroundOpacity\": "
            + config.BiomeHudConfig.backgroundOpacity
            + ",\n  \"biome.show.header\": "
            + config.BiomeHudConfig.showHeader
            + ",\n  \"rentals.show.hud\": "
            + config.RentalsConfig.showHud
            + ",\n  \"rentals.position.x\": "
            + config.RentalsConfig.positionX
            + ",\n  \"rentals.position.y\": "
            + config.RentalsConfig.positionY
            + ",\n  \"rentals.scale\": "
            + config.RentalsConfig.scale
            + ",\n  \"rentals.backgroundOpacity\": "
            + config.RentalsConfig.backgroundOpacity
            + ",\n  \"rentals.entries\": ["
            + encodeRentalEntriesJson(config.RentalsConfig)
            + "]\n}";
         Files.createDirectories(file.getParent());
         writeConfigAtomically(file, json);
         AutoDropperConfigIO.saveFrom(config.QolConfig);
         config.clearDirty();
      } catch (IOException e) {
         errorReporter.accept("[ConfigIO] Failed to save config to " + file() + ": " + e, e);
      }
   }

   public static void saveIfDirty() {
      if (SuiteConfig.INSTANCE.isDirty()) {
         if (saveBlockedByLoadFailure) {
            errorReporter.accept("[ConfigIO] Refusing to save defaults over an unreadable config. Fix or remove " + file(), null);
         } else {
            save();
         }
      }
   }

   private static boolean includesCountdown(VoteConfig.NotifyWhen mode) {
      return mode == VoteConfig.NotifyWhen.COUNTDOWN || mode == VoteConfig.NotifyWhen.BOTH;
   }

   private static String readValidConfigText(Path file) {
      try {
         String json = Files.readString(file, StandardCharsets.UTF_8);
         if (isValidConfigJson(json)) {
            return json;
         }

         errorReporter.accept("[ConfigIO] Config file is empty or invalid: " + file, null);
      } catch (IOException e) {
         errorReporter.accept("[ConfigIO] Failed to read config file " + file + ": " + e, e);
      }

      Path backup = backupFile(file);
      if (!Files.exists(backup)) {
         errorReporter.accept("[ConfigIO] No valid config backup found for " + file, null);
         return null;
      }

      try {
         String backupJson = Files.readString(backup, StandardCharsets.UTF_8);
         if (!isValidConfigJson(backupJson)) {
            errorReporter.accept("[ConfigIO] Config backup is also invalid: " + backup, null);
            return null;
         } else {
            Files.createDirectories(file.getParent());
            writeConfigAtomically(file, backupJson);
            errorReporter.accept("[ConfigIO] Restored config from backup: " + backup, null);
            return backupJson;
         }
      } catch (IOException e) {
         errorReporter.accept("[ConfigIO] Failed to restore config backup " + backup + ": " + e, e);
         return null;
      }
   }

   private static void writeConfigAtomically(Path file, String json) throws IOException {
      if (!isValidConfigJson(json)) {
         throw new IOException("Refusing to write invalid config JSON");
      }

      Path parent = file.getParent();
      Files.createDirectories(parent);
      Path tmp = parent.resolve(file.getFileName().toString() + ".tmp");
      Path backup = backupFile(file);
      Files.writeString(tmp, json, StandardCharsets.UTF_8);
      if (Files.exists(file) && isValidConfigFile(file)) {
         Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
      }

      try {
         Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
         Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
      }
   }

   private static Path backupFile(Path file) {
      return file.resolveSibling(file.getFileName().toString() + ".bak");
   }

   private static boolean isValidConfigFile(Path file) {
      try {
         return isValidConfigJson(Files.readString(file, StandardCharsets.UTF_8));
      } catch (IOException ignored) {
         return false;
      }
   }

   private static boolean isValidConfigJson(String json) {
      if (json != null && !json.isBlank()) {
         try {
            return JsonParser.parseString(json).isJsonObject();
         } catch (Exception ignored) {
            return false;
         }
      } else {
         return false;
      }
   }

   private static void loadChord(String json, String prefix, KeybindsConfig.Chord chord) {
      if (chord != null) {
         chord.key = extractString(json, prefix + ".key", "");
         chord.extraKey = extractString(json, prefix + ".extraKey", "");
         chord.mods = extractInt(json, prefix + ".mods", 0);
         chord.blockVanilla = extractBool(json, prefix + ".blockVanilla", false);
         if (chord.key == null) {
            chord.key = "";
         }

         if (chord.extraKey == null) {
            chord.extraKey = "";
         }

         chord.mods = Math.max(0, chord.mods) & 511;
         if (KeybindUtil.isModifierOnlyKey(chord.key)) {
            chord.key = "suitecore.modifier_only";
         }

         if ("suitecore.modifier_only".equals(chord.key) && chord.mods == 0) {
            chord.key = "";
         }

         if (chord.key.isBlank() || "suitecore.modifier_only".equals(chord.key)) {
            chord.extraKey = "";
         }
      }
   }

   private static String saveChord(String prefix, KeybindsConfig.Chord chord) {
      if (chord == null) {
         chord = new KeybindsConfig.Chord();
      }

      String key = chord.key == null ? "" : escapeJson(chord.key);
      String extraKey = chord.extraKey == null ? "" : escapeJson(chord.extraKey);
      int mods = Math.max(0, chord.mods) & 511;
      boolean blockVanilla = chord.blockVanilla;
      return "  \""
         + prefix
         + ".key\": \""
         + key
         + "\",\n  \""
         + prefix
         + ".extraKey\": \""
         + extraKey
         + "\",\n  \""
         + prefix
         + ".mods\": "
         + mods
         + ",\n  \""
         + prefix
         + ".blockVanilla\": "
         + blockVanilla
         + ",\n";
   }

   private static long extractLong(String json, String key, long def) {
      try {
         int i = json.indexOf("\"" + key + "\"");
         if (i < 0) {
            return def;
         }

         i = json.indexOf(":", i);
         if (i < 0) {
            return def;
         }

         int j = i + 1;

         while (j < json.length() && " \t".indexOf(json.charAt(j)) >= 0) {
            j++;
         }

         int end = j;

         while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
         }

         return Long.parseLong(json.substring(j, end));
      } catch (Exception e) {
         return def;
      }
   }

   private static double extractDouble(String json, String key, double def) {
      try {
         int i = json.indexOf("\"" + key + "\"");
         if (i < 0) {
            return def;
         }

         i = json.indexOf(":", i);
         if (i < 0) {
            return def;
         }

         int j = i + 1;

         while (j < json.length() && Character.isWhitespace(json.charAt(j))) {
            j++;
         }

         int end = j;

         while (end < json.length() && "0123456789.-eE+".indexOf(json.charAt(end)) >= 0) {
            end++;
         }

         return Double.parseDouble(json.substring(j, end));
      } catch (Exception e) {
         return def;
      }
   }

   private static int extractInt(String json, String key, int def) {
      try {
         int i = json.indexOf("\"" + key + "\"");
         if (i < 0) {
            return def;
         }

         i = json.indexOf(":", i);
         if (i < 0) {
            return def;
         }

         int j = i + 1;

         while (j < json.length() && " \t".indexOf(json.charAt(j)) >= 0) {
            j++;
         }

         int end = j;

         while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
         }

         return Integer.parseInt(json.substring(j, end));
      } catch (Exception e) {
         return def;
      }
   }

   private static float extractNormalizedFloat(String json, String key, float def) {
      float v = extractFloat(json, key, def);
      if (Float.isNaN(v) || Float.isInfinite(v)) {
         return def;
      } else {
         return !(v < 0.0F) && !(v > 1.0F) ? v : def;
      }
   }

   private static float extractFloat(String json, String key, float def) {
      try {
         int i = json.indexOf("\"" + key + "\"");
         if (i < 0) {
            return def;
         }

         i = json.indexOf(":", i);
         int j = i + 1;

         while (j < json.length() && " \t".indexOf(json.charAt(j)) >= 0) {
            j++;
         }

         int end = j;

         while (end < json.length() && "0123456789.-".indexOf(json.charAt(end)) >= 0) {
            end++;
         }

         return Float.parseFloat(json.substring(j, end));
      } catch (Exception e) {
         return def;
      }
   }

   private static boolean extractBool(String json, String key, boolean def) {
      String needle = "\"" + key + "\"";
      int i = json.indexOf(needle);
      if (i < 0) {
         return def;
      }

      i = json.indexOf(58, i + needle.length());
      if (i < 0) {
         return def;
      }

      i++;

      while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
         i++;
      }

      if (json.startsWith("true", i)) {
         return true;
      } else {
         return json.startsWith("false", i) ? false : def;
      }
   }

   private static String extractString(String json, String key, String def) {
      int i = json.indexOf("\"" + key + "\"");
      if (i < 0) {
         return def;
      }

      i = json.indexOf(":", i);
      int start = json.indexOf("\"", i + 1);
      if (start < 0) {
         return def;
      }

      int p = start + 1;
      StringBuilder sb = new StringBuilder();
      boolean escaping = false;

      while (p < json.length()) {
         char c = json.charAt(p++);
         if (escaping) {
            switch (c) {
               case '"':
                  sb.append('"');
                  break;
               case '\\':
                  sb.append('\\');
                  break;
               case 'n':
                  sb.append('\n');
                  break;
               case 'r':
                  sb.append('\r');
                  break;
               case 't':
                  sb.append('\t');
                  break;
               default:
                  sb.append(c);
            }

            escaping = false;
         } else if (c == '\\') {
            escaping = true;
         } else {
            if (c == '"') {
               return sb.toString();
            }

            sb.append(c);
         }
      }

      return def;
   }

   private static List<String> extractStringArray(String json, String key) {
      int i = json.indexOf("\"" + key + "\"");
      if (i < 0) {
         return List.of();
      }

      i = json.indexOf("[", i);
      if (i < 0) {
         return List.of();
      }

      int depth = 1;

      int j;
      for (j = i + 1; j < json.length() && depth > 0; j++) {
         char c = json.charAt(j);
         if (c == '[') {
            depth++;
         } else if (c == ']') {
            depth--;
         }
      }

      if (depth != 0) {
         return List.of();
      }

      String inside = json.substring(i + 1, j - 1).trim();
      if (inside.isEmpty()) {
         return List.of();
      }

      List<String> out = new ArrayList<>();
      int p = 0;

      while (p < inside.length()) {
         while (p < inside.length() && Character.isWhitespace(inside.charAt(p))) {
            p++;
         }

         if (p < inside.length() && inside.charAt(p) == ',') {
            p++;
         } else if (p < inside.length() && inside.charAt(p) == '"') {
            p++;
            StringBuilder sb = new StringBuilder();
            boolean escaping = false;

            while (true) {
               if (p < inside.length()) {
                  char c = inside.charAt(p++);
                  if (escaping) {
                     if (c != '\\' && c != '"') {
                        sb.append('\\').append(c);
                     } else {
                        sb.append(c);
                     }

                     escaping = false;
                     continue;
                  }

                  if (c == '\\') {
                     escaping = true;
                     continue;
                  }

                  if (c != '"') {
                     sb.append(c);
                     continue;
                  }
               }

               out.add(sb.toString());
               break;
            }
         } else {
            p++;
         }
      }

      return out;
   }

   public static <E extends Enum<E>> E parseEnum(String raw, Class<E> enumClass, E defaultValue) {
      if (raw != null && !raw.isBlank()) {
         try {
            return Enum.valueOf(enumClass, raw.trim().toUpperCase());
         } catch (IllegalArgumentException ex) {
            return defaultValue;
         }
      } else {
         return defaultValue;
      }
   }

   private static String encodeAutoSwapRule(QolConfig.AutoSwapRule r) {
      if (r.type == QolConfig.AutoSwapTargetType.GROUP) {
         return "GROUP|" + r.group.name() + "|" + r.slot;
      } else {
         return r.type == QolConfig.AutoSwapTargetType.CUSTOM
            ? "CUSTOM|" + (r.customGroupId == null ? "" : r.customGroupId) + "|" + r.slot
            : "BLOCK|" + r.blockId + "|" + r.slot;
      }
   }

   private static QolConfig.AutoSwapRule decodeAutoSwapRule(String s) {
      QolConfig.AutoSwapRule r = new QolConfig.AutoSwapRule();

      try {
         String[] parts = s.split("\\|");
         if (parts.length < 3) {
            return r;
         }

         String type = parts[0];
         String target = parts[1];
         int slot = Integer.parseInt(parts[2]);
         r.slot = clampSlot(slot);
         if ("GROUP".equalsIgnoreCase(type)) {
            r.type = QolConfig.AutoSwapTargetType.GROUP;
            r.group = QolConfig.AutoSwapGrouping.valueOf(target);
         } else if ("CUSTOM".equalsIgnoreCase(type)) {
            r.type = QolConfig.AutoSwapTargetType.CUSTOM;
            r.customGroupId = target;
         } else if ("BLOCK".equalsIgnoreCase(type)) {
            r.type = QolConfig.AutoSwapTargetType.BLOCK;
            r.blockId = target;
         }
      } catch (Exception var6) {
      }

      return r;
   }

   private static int clampSlot(int slot) {
      if (slot < 1) {
         return 1;
      } else {
         return slot > 9 ? 9 : slot;
      }
   }

   private static int clampColor(int value) {
      if (value < 0) {
         return 0;
      } else {
         return value > 255 ? 255 : value;
      }
   }

   public static String extractStringCompat(String json, String defaultValue, String... keys) {
      for (String key : keys) {
         String value = extractString(json, key, null);
         if (value != null) {
            return value;
         }
      }

      return defaultValue;
   }

   public static boolean extractBoolCompat(String json, boolean defaultValue, String... keys) {
      for (String key : keys) {
         if (hasKey(json, key)) {
            return extractBool(json, key, defaultValue);
         }
      }

      return defaultValue;
   }

   private static boolean hasKey(String json, String key) {
      String needle = "\"" + key + "\"";
      int i = json.indexOf(needle);
      if (i < 0) {
         return false;
      }

      int colon = json.indexOf(58, i + needle.length());
      return colon >= 0;
   }

   public static int extractIntCompat(String json, int defaultValue, String... keys) {
      for (String key : keys) {
         if (hasKey(json, key)) {
            return extractInt(json, key, defaultValue);
         }
      }

      return defaultValue;
   }

   public static float extractFloatCompat(String json, float defaultValue, String... keys) {
      for (String key : keys) {
         if (hasKey(json, key)) {
            return extractFloat(json, key, defaultValue);
         }
      }

      return defaultValue;
   }

   public static float extractNormalizedFloatCompat(String json, float defaultValue, String... keys) {
      float value = extractFloatCompat(json, defaultValue, keys);
      if (Float.isNaN(value) || Float.isInfinite(value)) {
         return defaultValue;
      } else if (value < 0.0F) {
         return 0.0F;
      } else {
         return value > 1.0F ? 1.0F : value;
      }
   }

   public static long extractLongCompat(String json, long defaultValue, String... keys) {
      for (String key : keys) {
         if (hasKey(json, key)) {
            return extractLong(json, key, defaultValue);
         }
      }

      return defaultValue;
   }

   public static double extractDoubleCompat(String json, double defaultValue, String... keys) {
      for (String key : keys) {
         if (hasKey(json, key)) {
            return extractDouble(json, key, defaultValue);
         }
      }

      return defaultValue;
   }

   public static List<String> extractStringArrayCompat(String json, String... keys) {
      for (String key : keys) {
         if (hasKey(json, key)) {
            List<String> result = extractStringArray(json, key);
            return result != null ? result : new ArrayList<>();
         }
      }

      return new ArrayList<>();
   }

   private static String encodeProfilesJson(QolConfig config) {
      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < config.autoSwapperProfiles.size(); i++) {
         if (i > 0) {
            sb.append(", ");
         }

         String enc = encodeAutoSwapperProfile(config.autoSwapperProfiles.get(i));
         sb.append("\"").append(escapeJson(enc)).append("\"");
      }

      return sb.toString();
   }

   private static String encodeToolLockProfilesJson(QolConfig config) {
      if (config == null) {
         return "";
      }

      config.ensureToolLockProfiles();
      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < config.toolLockProfiles.size(); i++) {
         if (i > 0) {
            sb.append(", ");
         }

         String enc = encodeToolLockProfile(config.toolLockProfiles.get(i));
         sb.append("\"").append(escapeJson(enc)).append("\"");
      }

      return sb.toString();
   }

   private static String encodeToolLockProfile(QolConfig.ToolLockProfile profile) {
      if (profile == null) {
         profile = new QolConfig.ToolLockProfile();
      }

      QolConfig.ToolLockReportMode reportMode = profile.reportMode == null ? QolConfig.ToolLockReportMode.NOTICE : profile.reportMode;
      return "name="
         + escapeProfileValue(profile.name)
         + ";blockOnBlock="
         + profile.blockOnInteractBlock
         + ";locked="
         + QolConfig.sanitizeToolLockMask(profile.lockedSlotsMask)
         + ";leftLocked="
         + QolConfig.sanitizeToolLockMask(profile.leftClickLockedSlotsMask)
         + ";report="
         + reportMode.name();
   }

   private static QolConfig.ToolLockProfile decodeToolLockProfile(String s) {
      if (s != null && !s.isBlank()) {
         Map<String, String> kv = decodeProfileKv(s);
         if (kv.isEmpty()) {
            return null;
         }

         QolConfig.ToolLockProfile profile = new QolConfig.ToolLockProfile();
         profile.name = kv.getOrDefault("name", "Profile");
         profile.blockOnInteractBlock = Boolean.parseBoolean(kv.getOrDefault("blockOnBlock", "false"));

         try {
            profile.lockedSlotsMask = QolConfig.sanitizeToolLockMask(Integer.parseInt(kv.getOrDefault("locked", "0")));
         } catch (Exception ignored) {
            profile.lockedSlotsMask = 0;
         }

         try {
            profile.leftClickLockedSlotsMask = QolConfig.sanitizeToolLockMask(Integer.parseInt(kv.getOrDefault("leftLocked", "0")));
         } catch (Exception ignored) {
            profile.leftClickLockedSlotsMask = 0;
         }

         profile.reportMode = parseEnum(
            kv.getOrDefault("report", QolConfig.ToolLockReportMode.NOTICE.name()), QolConfig.ToolLockReportMode.class, QolConfig.ToolLockReportMode.NOTICE
         );
         return profile;
      } else {
         return null;
      }
   }

   private static String encodeAutoSwapCustomGroupsJson(QolConfig config) {
      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < config.autoSwapperCustomGroups.size(); i++) {
         if (i > 0) {
            sb.append(", ");
         }

         String enc = encodeAutoSwapCustomGroup(config.autoSwapperCustomGroups.get(i));
         sb.append("\"").append(escapeJson(enc)).append("\"");
      }

      return sb.toString();
   }

   private static String encodeAutoSwapCustomGroup(QolConfig.AutoSwapCustomGroup group) {
      if (group == null) {
         group = new QolConfig.AutoSwapCustomGroup();
      }

      StringBuilder blocks = new StringBuilder();

      for (int i = 0; i < group.blockIds.size(); i++) {
         if (i > 0) {
            blocks.append("~~");
         }

         blocks.append(group.blockIds.get(i) == null ? "" : group.blockIds.get(i));
      }

      return "id=" + escapeProfileValue(group.id) + ";name=" + escapeProfileValue(group.name) + ";blocks=" + escapeProfileValue(blocks.toString());
   }

   private static QolConfig.AutoSwapCustomGroup decodeAutoSwapCustomGroup(String s) {
      if (s != null && !s.isBlank()) {
         Map<String, String> kv = decodeProfileKv(s);
         if (kv.isEmpty()) {
            return null;
         }

         QolConfig.AutoSwapCustomGroup group = new QolConfig.AutoSwapCustomGroup();
         group.id = kv.getOrDefault("id", "");
         group.name = kv.getOrDefault("name", "Custom Group");
         group.blockIds.clear();
         String blocks = kv.getOrDefault("blocks", "");
         if (!blocks.isBlank()) {
            for (String part : blocks.split("~~")) {
               String id = part == null ? "" : part.trim();
               if (!id.isBlank() && !group.blockIds.contains(id)) {
                  group.blockIds.add(id);
               }
            }
         }

         if (group.id == null || group.id.isBlank()) {
            group.id = "custom-" + System.currentTimeMillis();
         }

         return group;
      } else {
         return null;
      }
   }

   private static String encodeAutoDropRulesJson(QolConfig config) {
      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < config.autoDropperRules.size(); i++) {
         if (i > 0) {
            sb.append(", ");
         }

         sb.append("\"").append(escapeJson(encodeAutoDropRule(config.autoDropperRules.get(i)))).append("\"");
      }

      return sb.toString();
   }

   private static String encodeAutoDropRule(QolConfig.AutoDropRule rule) {
      if (rule == null) {
         rule = new QolConfig.AutoDropRule();
      }

      return "type="
         + rule.type
         + ";item="
         + escapeProfileValue(rule.itemId)
         + ";custom="
         + escapeProfileValue(rule.customGroupId)
         + ";minimumAmount="
         + Math.max(0, rule.minimumAmount)
         + ";keepItems="
         + Math.max(0, rule.keepItems)
         + ";keepStacks="
         + Math.max(0, rule.keepStacks)
         + ";enabled="
         + rule.enabled
         + ";components="
         + escapeProfileValue(rule.componentFilter);
   }

   private static QolConfig.AutoDropRule decodeAutoDropRule(String s) {
      if (s != null && !s.isBlank()) {
         Map<String, String> kv = decodeProfileKv(s);
         if (kv.isEmpty()) {
            return null;
         }

         QolConfig.AutoDropRule rule = new QolConfig.AutoDropRule();

         try {
            rule.type = parseEnum(kv.getOrDefault("type", "ITEM"), QolConfig.AutoDropTargetType.class, QolConfig.AutoDropTargetType.ITEM);
            rule.itemId = kv.getOrDefault("item", "minecraft:cobblestone");
            rule.customGroupId = kv.getOrDefault("custom", "");
            rule.minimumAmount = Math.max(0, Integer.parseInt(kv.getOrDefault("minimumAmount", "0")));
            rule.keepItems = Math.max(0, Integer.parseInt(kv.getOrDefault("keepItems", "0")));
            rule.keepStacks = Math.max(0, Integer.parseInt(kv.getOrDefault("keepStacks", "0")));
            rule.enabled = Boolean.parseBoolean(kv.getOrDefault("enabled", "true"));
            rule.componentFilter = kv.getOrDefault("components", "");
         } catch (Exception var4) {
         }

         return rule;
      } else {
         return null;
      }
   }

   private static String encodeAutoDropCustomGroupsJson(QolConfig config) {
      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < config.autoDropperCustomGroups.size(); i++) {
         if (i > 0) {
            sb.append(", ");
         }

         sb.append("\"").append(escapeJson(encodeAutoDropCustomGroup(config.autoDropperCustomGroups.get(i)))).append("\"");
      }

      return sb.toString();
   }

   private static String encodeAutoDropCustomGroup(QolConfig.AutoDropCustomGroup group) {
      if (group == null) {
         group = new QolConfig.AutoDropCustomGroup();
      }

      StringBuilder items = new StringBuilder();

      for (int i = 0; i < group.items.size(); i++) {
         if (i > 0) {
            items.append("~~");
         }

         items.append(encodeAutoDropGroupItem(group.items.get(i)));
      }

      return "id="
         + escapeProfileValue(group.id)
         + ";name="
         + escapeProfileValue(group.name)
         + ";enabled="
         + group.enabled
         + ";items="
         + escapeProfileValue(items.toString());
   }

   private static QolConfig.AutoDropCustomGroup decodeAutoDropCustomGroup(String s) {
      if (s != null && !s.isBlank()) {
         Map<String, String> kv = decodeProfileKv(s);
         if (kv.isEmpty()) {
            return null;
         }

         QolConfig.AutoDropCustomGroup group = new QolConfig.AutoDropCustomGroup();
         group.id = kv.getOrDefault("id", "");
         group.name = kv.getOrDefault("name", "Custom Group");
         group.enabled = Boolean.parseBoolean(kv.getOrDefault("enabled", "true"));
         String items = kv.getOrDefault("items", "");
         if (!items.isBlank()) {
            for (String part : items.split("~~")) {
               QolConfig.AutoDropGroupItem entry = decodeAutoDropGroupItem(part);
               if (entry != null && !entry.itemId.isBlank() && groupItemById(group, entry.itemId) == null) {
                  group.items.add(entry);
               }
            }
         }

         if (group.id == null || group.id.isBlank()) {
            group.id = "drop-" + System.currentTimeMillis();
         }

         return group;
      } else {
         return null;
      }
   }

   private static String encodeAutoDropGroupItem(QolConfig.AutoDropGroupItem item) {
      if (item == null) {
         item = new QolConfig.AutoDropGroupItem();
      }

      return b64(item.itemId)
         + "|"
         + Math.max(0, item.minimumAmount)
         + "|"
         + Math.max(0, item.keepItems)
         + "|"
         + Math.max(0, item.keepStacks)
         + "|"
         + b64(item.componentFilter);
   }

   private static QolConfig.AutoDropGroupItem decodeAutoDropGroupItem(String raw) {
      if (raw != null && !raw.isBlank()) {
         String[] parts = raw.split("\\|", -1);
         QolConfig.AutoDropGroupItem item = new QolConfig.AutoDropGroupItem();
         if (parts.length <= 1) {
            item.itemId = raw.trim();
            return item.itemId.isBlank() ? null : item;
         }

         item.itemId = unb64(parts[0]).trim();

         try {
            item.minimumAmount = Math.max(0, Integer.parseInt(parts[1]));
            item.keepItems = Math.max(0, Integer.parseInt(parts[2]));
            item.keepStacks = Math.max(0, Integer.parseInt(parts[3]));
            item.componentFilter = parts.length > 4 ? unb64(parts[4]) : "";
         } catch (Exception var4) {
         }

         return item.itemId.isBlank() ? null : item;
      } else {
         return null;
      }
   }

   private static QolConfig.AutoDropGroupItem groupItemById(QolConfig.AutoDropCustomGroup group, String itemId) {
      if (group != null && itemId != null) {
         for (QolConfig.AutoDropGroupItem item : group.items) {
            if (item != null && itemId.equals(item.itemId)) {
               return item;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static String b64(String value) {
      return Base64.getUrlEncoder().withoutPadding().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
   }

   private static String unb64(String value) {
      if (value != null && !value.isBlank()) {
         try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
         } catch (Exception ignored) {
            return "";
         }
      } else {
         return "";
      }
   }

   private static String encodeAutoSwapperProfile(QolConfig.AutoSwapperProfile profile) {
      StringBuilder rules = new StringBuilder();

      for (int i = 0; i < profile.rules.size(); i++) {
         if (i > 0) {
            rules.append("~~");
         }

         rules.append(encodeAutoSwapRule(profile.rules.get(i)));
      }

      return "name="
         + escapeProfileValue(profile.name)
         + ";useDefaultTool="
         + profile.useDefaultTool
         + ";defaultSlot="
         + clampSlot(profile.defaultSlot)
         + ";rules="
         + escapeProfileValue(rules.toString());
   }

   private static QolConfig.AutoSwapperProfile decodeAutoSwapperProfile(String s) {
      if (s != null && !s.isBlank()) {
         QolConfig.AutoSwapperProfile profile = new QolConfig.AutoSwapperProfile();

         try {
            String name = extractProfilePart(s, "name");
            if (name != null && !name.isBlank()) {
               profile.name = unescapeProfileValue(name);
            }

            String useDefaultTool = extractProfilePart(s, "useDefaultTool");
            if (useDefaultTool != null) {
               profile.useDefaultTool = Boolean.parseBoolean(useDefaultTool);
            }

            String defaultSlot = extractProfilePart(s, "defaultSlot");
            if (defaultSlot != null && !defaultSlot.isBlank()) {
               profile.defaultSlot = clampSlot(Integer.parseInt(defaultSlot));
            }

            String rulesRaw = extractProfilePart(s, "rules");
            profile.rules.clear();
            if (rulesRaw != null && !rulesRaw.isBlank()) {
               String decodedRules = unescapeProfileValue(rulesRaw);

               for (String part : decodedRules.split("~~")) {
                  if (!part.isBlank()) {
                     profile.rules.add(decodeAutoSwapRule(part));
                  }
               }
            }

            return profile;
         } catch (Exception ignored) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static String extractProfilePart(String s, String key) {
      String needle = key + "=";
      int start = s.indexOf(needle);
      if (start < 0) {
         return null;
      }

      start += needle.length();
      int end = s.length();
      String[] keys = new String[]{"name=", "useDefaultTool=", "defaultSlot=", "rules="};

      for (String k : keys) {
         if (!k.equals(needle)) {
            int idx = s.indexOf(";" + k, start);
            if (idx >= 0 && idx < end) {
               end = idx;
            }
         }
      }

      return s.substring(start, end);
   }

   private static String escapeJson(String s) {
      return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
   }

   private static String encodeStringArrayJson(List<String> list) {
      if (list != null && !list.isEmpty()) {
         StringBuilder sb = new StringBuilder();

         for (int i = 0; i < list.size(); i++) {
            String s = list.get(i);
            if (s == null) {
               s = "";
            }

            if (i > 0) {
               sb.append(", ");
            }

            sb.append("\"").append(escapeJson(s)).append("\"");
         }

         return sb.toString();
      } else {
         return "";
      }
   }

   private static String escapeProfileValue(String s) {
      return s == null ? "" : s.replace("\\", "\\\\").replace(";", "\\;").replace("=", "\\=");
   }

   private static String unescapeProfileValue(String s) {
      if (s != null && !s.isEmpty()) {
         StringBuilder out = new StringBuilder();
         boolean escaping = false;

         for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaping) {
               out.append(c);
               escaping = false;
            } else if (c == '\\') {
               escaping = true;
            } else {
               out.append(c);
            }
         }

         if (escaping) {
            out.append('\\');
         }

         return out.toString();
      } else {
         return "";
      }
   }

   private static String encodeAdvertiserProfilesJson(ChatConfig cfg) {
      if (cfg == null) {
         return "";
      }

      cfg.ensureAdvertiserProfilesInitialized();
      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < cfg.advertiserProfiles.size(); i++) {
         if (i > 0) {
            sb.append(", ");
         }

         String enc = encodeAdvertiserProfile(cfg.advertiserProfiles.get(i));
         sb.append("\"").append(escapeJson(enc)).append("\"");
      }

      return sb.toString();
   }

   private static String encodeAdvertiserProfile(ChatConfig.AdvertiserProfile p) {
      if (p == null) {
         p = new ChatConfig.AdvertiserProfile();
      }

      String name = p.name == null ? "" : p.name;
      String msg = p.message == null ? "" : p.message;
      return "name=" + escapeProfileValue(name) + ";message=" + escapeProfileValue(msg);
   }

   private static ChatConfig.AdvertiserProfile decodeAdvertiserProfile(String s) {
      if (s != null && !s.isBlank()) {
         Map<String, String> kv = decodeProfileKv(s);
         if (kv.isEmpty()) {
            return null;
         }

         ChatConfig.AdvertiserProfile p = new ChatConfig.AdvertiserProfile();
         p.name = kv.getOrDefault("name", "");
         p.message = kv.getOrDefault("message", "");
         return p;
      } else {
         return null;
      }
   }

   private static String encodeRentalEntriesJson(RentalsConfig cfg) {
      if (cfg != null && !cfg.entries.isEmpty()) {
         StringBuilder sb = new StringBuilder();

         for (int i = 0; i < cfg.entries.size(); i++) {
            if (i > 0) {
               sb.append(", ");
            }

            String enc = encodeRentalEntry(cfg.entries.get(i));
            sb.append("\"").append(escapeJson(enc)).append("\"");
         }

         return sb.toString();
      } else {
         return "";
      }
   }

   private static String encodeDungeonCooldownsJson(DungeonConfig cfg) {
      if (cfg != null && !cfg.cooldownEndsByServer.isEmpty()) {
         StringBuilder sb = new StringBuilder();
         int i = 0;

         for (Entry<String, Long> entry : cfg.cooldownEndsByServer.entrySet()) {
            if (entry != null && entry.getValue() != null && entry.getValue() > 0L) {
               if (i > 0) {
                  sb.append(", ");
               }

               sb.append("\"").append(escapeJson(DungeonConfig.serverKey(entry.getKey()) + "|" + entry.getValue())).append("\"");
               i++;
            }
         }

         return sb.toString();
      } else {
         return "";
      }
   }

   private static String encodeRentalEntry(RentalsConfig.RentalEntry entry) {
      if (entry == null) {
         entry = new RentalsConfig.RentalEntry();
      }

      return "item="
         + escapeProfileValue(entry.itemName)
         + ";location="
         + escapeProfileValue(entry.location)
         + ";minutes="
         + Math.max(1, entry.totalMinutes)
         + ";endAt="
         + Math.max(0L, entry.endAtEpochMs)
         + ";pausedRemaining="
         + Math.max(0L, entry.pausedRemainingMs)
         + ";paused="
         + entry.paused;
   }

   private static RentalsConfig.RentalEntry decodeRentalEntry(String s) {
      if (s != null && !s.isBlank()) {
         Map<String, String> kv = decodeProfileKv(s);
         if (kv.isEmpty()) {
            return null;
         }

         try {
            RentalsConfig.RentalEntry entry = new RentalsConfig.RentalEntry();
            entry.itemName = kv.getOrDefault("item", "Rental");
            entry.location = kv.getOrDefault("location", "Unknown");
            entry.totalMinutes = Math.max(1, Math.min(10080, Integer.parseInt(kv.getOrDefault("minutes", "60"))));
            entry.endAtEpochMs = Math.max(0L, Long.parseLong(kv.getOrDefault("endAt", "0")));
            entry.pausedRemainingMs = Math.max(0L, Long.parseLong(kv.getOrDefault("pausedRemaining", "0")));
            entry.paused = Boolean.parseBoolean(kv.getOrDefault("paused", "false"));
            if (entry.endAtEpochMs <= 0L && !entry.paused) {
               entry.endAtEpochMs = System.currentTimeMillis() + entry.totalMinutes * 60000L;
            }

            return entry;
         } catch (Exception ignored) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static String jobLifetimeJson(JobsConfig jobsConfig) {
      StringBuilder out = new StringBuilder();

      for (Entry<String, Double> entry : jobsConfig.lifetimeByServer().entrySet()) {
         out.append("  \"jobs.lifetime.").append(entry.getKey()).append("\": ").append(entry.getValue()).append(",\n");
      }

      return out.toString();
   }

   private static Map<String, String> decodeProfileKv(String s) {
      Map<String, String> out = new HashMap<>();
      if (s == null) {
         return out;
      }

      StringBuilder key = new StringBuilder();
      StringBuilder val = new StringBuilder();
      boolean inKey = true;
      boolean escaping = false;

      for (int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);
         if (escaping) {
            if (inKey) {
               key.append(c);
            } else {
               val.append(c);
            }

            escaping = false;
         } else if (c == '\\') {
            escaping = true;
         } else if (inKey && c == '=') {
            inKey = false;
         } else if (c == ';') {
            String k = key.toString().trim();
            if (!k.isEmpty()) {
               out.put(k, val.toString());
            }

            key.setLength(0);
            val.setLength(0);
            inKey = true;
         } else if (inKey) {
            key.append(c);
         } else {
            val.append(c);
         }
      }

      String k = key.toString().trim();
      if (!k.isEmpty()) {
         out.put(k, val.toString());
      }

      return out;
   }
}
