package org.blossomsuite.core.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.SuiteServer;
import org.blossomsuite.core.alts.AltResourceState;
import org.blossomsuite.core.chat.ChatOutput;
import org.blossomsuite.core.cooldowns.CooldownRules;
import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.RentalsConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.hud.ScreenNoticeOverlay;
import org.blossomsuite.core.jobs.JobsChattextSetup;
import org.blossomsuite.core.jobs.JobsTracker;
import org.blossomsuite.core.qol.inventorysort.InventorySorter;
import org.blossomsuite.core.storage.SegmentStore;
import org.blossomsuite.core.util.RateColors;
import org.blossomsuite.core.util.SuiteItemIdUtil;
import org.blossomsuite.core.util.TextUtil;
import org.blossomsuite.core.util.WorldGate;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper.WrapperLookup;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class SuiteCommands {
   private static Runnable requestSettingsOpen = () -> {};
   private static Runnable requestHudEditOpen = () -> {};
   private static BiConsumer<String, Throwable> infoLogger = (message, throwable) -> {};
   private static BiConsumer<String, Throwable> warnLogger = (message, throwable) -> {};

   private SuiteCommands() {
   }

   public static void configure(
      Runnable settingsOpenRequester, Runnable hudEditOpenRequester, BiConsumer<String, Throwable> infoLog, BiConsumer<String, Throwable> warnLog
   ) {
      requestSettingsOpen = settingsOpenRequester == null ? () -> {} : settingsOpenRequester;
      requestHudEditOpen = hudEditOpenRequester == null ? () -> {} : hudEditOpenRequester;
      infoLogger = infoLog == null ? (message, throwable) -> {} : infoLog;
      warnLogger = warnLog == null ? (message, throwable) -> {} : warnLog;
   }

   private static LiteralArgumentBuilder<FabricClientCommandSource> extras(String commandName) {
      LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal(commandName).then(ClientCommandManager.literal("reload").executes(ctx -> {
         int count = CooldownRules.loadLocalFile();
         ChatOutput.info(count < 0 ? "No cooldowns.json found in the config folder." : "Loaded " + count + " cooldown rules.");
         return 1;
      }));
      for (LiteralArgumentBuilder<FabricClientCommandSource> sub : BuddyCommands.subcommands()) {
         root.then(sub);
      }

      return root;
   }

   public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
      dispatcher.register(root(SuiteRuntime.profile().commandName()));
      dispatcher.register(extras(SuiteRuntime.profile().commandName()));

      for (String alias : SuiteRuntime.profile().commandAliases()) {
         if (alias != null && !alias.isBlank()) {
            dispatcher.register(root(alias));
            dispatcher.register(extras(alias));
         }
      }
   }

   private static LiteralArgumentBuilder<FabricClientCommandSource> root(String commandName) {
      return (LiteralArgumentBuilder<FabricClientCommandSource>)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal(
                                          commandName
                                       )
                                       .executes(
                                          ctx -> {
                                             ((FabricClientCommandSource)ctx.getSource())
                                                .sendFeedback(Text.literal(SuiteRuntime.profile().displayName() + " loaded. Try /" + commandName + " help"));
                                             return 1;
                                          }
                                       ))
                                    .then(ClientCommandManager.literal("options").executes(ctx -> {
                                       requestSettingsOpen.run();
                                       return 1;
                                    })))
                                 .then(ClientCommandManager.literal("trade").executes(ctx -> {
                                    if (!requireActiveWorld()) {
                                       return 0;
                                    }

                                    reportTrade();
                                    return 1;
                                 })))
                              .then(ClientCommandManager.literal("sort").executes(ctx -> {
                                 if (!requireActiveWorld()) {
                                    return 0;
                                 }

                                 InventorySorter.sortPlayerInventory(MinecraftClient.getInstance());
                                 return 1;
                              })))
                           .then(ClientCommandManager.literal("debug").then(ClientCommandManager.literal("notifyhand").executes(ctx -> notifyHeldItem()))))
                        .then(
                           ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal(
                                             "rent"
                                          )
                                          .then(ClientCommandManager.literal("pause").executes(ctx -> pauseRentals())))
                                       .then(ClientCommandManager.literal("resume").executes(ctx -> resumeRentals())))
                                    .then(ClientCommandManager.literal("toggle").executes(ctx -> toggleRentalsPaused())))
                                 .then(ClientCommandManager.literal("clear").executes(ctx -> clearExpiredRentals())))
                              .then(((RequiredArgumentBuilder)ClientCommandManager.argument("minutes", StringArgumentType.word()).executes(ctx -> {
                                 String minutes = StringArgumentType.getString(ctx, "minutes");
                                 return addRentalFromHand(minutes, "");
                              })).then(ClientCommandManager.argument("place", StringArgumentType.greedyString()).executes(ctx -> {
                                 String minutes = StringArgumentType.getString(ctx, "minutes");
                                 String place = StringArgumentType.getString(ctx, "place");
                                 return addRentalFromHand(minutes, place);
                              })))
                        ))
                  .then(
                     ((LiteralArgumentBuilder)ClientCommandManager.literal("help")
                           .executes(
                              ctx -> {
                                 ChatOutput.info(Text.literal("-------- " + SuiteRuntime.profile().displayName() + " --------").formatted(Formatting.DARK_GRAY));
                                 ChatOutput.info(
                                    Text.literal(commandText("help "))
                                       .formatted(Formatting.GRAY)
                                       .append(Text.literal("- Shows this menu").formatted(Formatting.DARK_GRAY))
                                 );
                                 ChatOutput.info(
                                    Text.literal(commandText("options "))
                                       .formatted(Formatting.GRAY)
                                       .append(Text.literal("- Open settings UI").formatted(Formatting.DARK_GRAY))
                                 );
                                 ChatOutput.info(
                                    Text.literal(commandText("reload "))
                                       .formatted(Formatting.GRAY)
                                       .append(Text.literal("- Reload cooldown rules from disk").formatted(Formatting.DARK_GRAY))
                                 );
                                 ChatOutput.info(
                                    Text.literal(commandText("editmode "))
                                       .formatted(Formatting.GRAY)
                                       .append(Text.literal("- Move HUD elements").formatted(Formatting.DARK_GRAY))
                                 );
                                 ChatOutput.info(
                                    Text.literal(commandText("totals "))
                                       .formatted(Formatting.GRAY)
                                       .append(Text.literal("- View lifetime job earnings").formatted(Formatting.DARK_GRAY))
                                 );
                                 ChatOutput.info(
                                    Text.literal(commandText("trade "))
                                       .formatted(Formatting.GRAY)
                                       .append(
                                          Text.literal("- View tracked balance, claim blocks, and " + SuiteRuntime.profile().primaryResourceDisplayName())
                                             .formatted(Formatting.DARK_GRAY)
                                       )
                                 );
                                 ChatOutput.info(
                                    Text.literal(commandText("sort "))
                                       .formatted(Formatting.GRAY)
                                       .append(Text.literal("- Sort unlocked inventory slots").formatted(Formatting.DARK_GRAY))
                                 );
                                 ChatOutput.info(
                                    Text.literal(commandText("rent <minutes> [place] "))
                                       .formatted(Formatting.GRAY)
                                       .append(Text.literal("- Track the held item as a rental").formatted(Formatting.DARK_GRAY))
                                 );
                                 ChatOutput.info(
                                    Text.literal(commandText("rent clear "))
                                       .formatted(Formatting.GRAY)
                                       .append(Text.literal("- Clear expired rental counters").formatted(Formatting.DARK_GRAY))
                                 );
                                 ChatOutput.info(
                                    Text.literal(commandText("jobs segment report "))
                                       .formatted(Formatting.GRAY)
                                       .append(Text.literal("- View job segment breakdown").formatted(Formatting.DARK_GRAY))
                                 );
                                 ChatOutput.info(
                                    Text.literal(commandText("jobs setlifetime <amount> "))
                                       .formatted(Formatting.GRAY)
                                       .append(Text.literal("- Set lifetime jobs total for your current recognized server").formatted(Formatting.DARK_GRAY))
                                 );
                                 ChatOutput.info(
                                    Text.literal(commandText("help debug hand "))
                                       .formatted(Formatting.GRAY)
                                       .append(Text.literal("- Debug held item").formatted(Formatting.DARK_GRAY))
                                 );
                                 ChatOutput.info(
                                    Text.literal(commandText("help debug nbt "))
                                       .formatted(Formatting.GRAY)
                                       .append(Text.literal("- Log held item NBT").formatted(Formatting.DARK_GRAY))
                                 );
                                 ChatOutput.info(
                                    Text.literal(commandText("debug notifyhand "))
                                       .formatted(Formatting.GRAY)
                                       .append(Text.literal("- Show held item in the screen notice spot").formatted(Formatting.DARK_GRAY))
                                 );
                                 ChatOutput.info(
                                    Text.literal(
                                          "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500"
                                       )
                                       .formatted(Formatting.DARK_GRAY)
                                 );
                                 return 1;
                              }
                           ))
                        .then(
                           ((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal("debug")
                                    .then(ClientCommandManager.literal("hand").executes(ctx -> {
                                       if (!requireActiveWorld()) {
                                          return 0;
                                       } else {
                                          MinecraftClient client = MinecraftClient.getInstance();
                                          if (client.player == null) {
                                             return 0;
                                          } else {
                                             ItemStack held = client.player.getMainHandStack();
                                             if (held.isEmpty()) {
                                                ChatOutput.info("No item in hand");
                                                return 1;
                                             } else {
                                                String key = SuiteItemIdUtil.getBestId(held);
                                                ChatOutput.info("=== Suite Item Key ===");
                                                ChatOutput.info(key);
                                                ChatOutput.info("========================");
                                                return 1;
                                             }
                                          }
                                       }
                                    })))
                                 .then(ClientCommandManager.literal("notifyhand").executes(ctx -> notifyHeldItem())))
                              .then(ClientCommandManager.literal("nbt").executes(ctx -> {
                                 if (!requireActiveWorld()) {
                                    return 0;
                                 }

                                 MinecraftClient client = MinecraftClient.getInstance();
                                 if (client.player != null && client.world != null) {
                                    ItemStack held = client.player.getMainHandStack();
                                    if (held.isEmpty()) {
                                       ChatOutput.info("No item in hand");
                                       return 1;
                                    }

                                    try {
                                       WrapperLookup lookup = client.world.getRegistryManager();
                                       NbtElement element = (NbtElement)ItemStack.CODEC.encodeStart(lookup.getOps(NbtOps.INSTANCE), held).getOrThrow();
                                       if (element instanceof NbtCompound compound) {
                                          String snbt = compound.toString();
                                          infoLogger.accept("=== " + SuiteRuntime.profile().displayName() + " HELD ITEM NBT ===", null);
                                          infoLogger.accept(snbt, null);
                                          infoLogger.accept("=== /" + SuiteRuntime.profile().displayName() + " HELD ITEM NBT ===", null);
                                          if (client.keyboard != null) {
                                             client.keyboard.setClipboard(snbt);
                                          }

                                          ChatOutput.info("Copied + logged held item NBT (" + snbt.length() + " chars)");
                                          return 1;
                                       } else {
                                          ChatOutput.info("Failed to encode ItemStack to NBT");
                                          return 0;
                                       }
                                    } catch (Exception e) {
                                       ChatOutput.info("Failed to encode/log NBT (see log for details)");
                                       warnLogger.accept("[" + SuiteRuntime.profile().commandName() + "] help debug nbt failed", e);
                                       return 0;
                                    }
                                 } else {
                                    return 0;
                                 }
                              }))
                        )
                  ))
               .then(
                  ((LiteralArgumentBuilder)ClientCommandManager.literal("jobs")
                        .then(ClientCommandManager.literal("segment").then(((LiteralArgumentBuilder)ClientCommandManager.literal("report").executes(ctx -> {
                           if (!requireActiveWorld()) {
                              return 0;
                           }

                           report();
                           return 1;
                        })).then(ClientCommandManager.literal("segments").executes(ctx -> {
                           if (!requireActiveWorld()) {
                              return 0;
                           }

                           report();
                           return 1;
                        })))))
                     .then(
                        ClientCommandManager.literal("setlifetime")
                           .then(
                              ClientCommandManager.argument("amount", StringArgumentType.greedyString())
                                 .executes(ctx -> setCurrentServerLifetime(StringArgumentType.getString(ctx, "amount")))
                           )
                     )
               ))
            .then(ClientCommandManager.literal("editmode").executes(ctx -> {
               if (!requireActiveWorld()) {
                  return 0;
               }

               requestHudEditOpen.run();
               return 1;
            })))
         .then(
            ClientCommandManager.literal("totals")
               .executes(
                  ctx -> {
                     if (!requireActiveWorld()) {
                        return 0;
                     }

                     ChatOutput.info(
                        Text.literal("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500").formatted(Formatting.DARK_GRAY)
                     );
                     Formatting setFormatting = realmFormatting(recognizedRealm(JobsChattextSetup.realmName));
                     ChatOutput.info(
                        Text.literal(SuiteRuntime.profile().displayName().toUpperCase(Locale.ROOT))
                           .formatted(setFormatting, Formatting.BOLD)
                           .append(Text.literal(" \u2022 ").formatted(Formatting.DARK_GRAY))
                           .append(Text.literal("Job Lifetime").formatted(Formatting.GRAY))
                     );
                     ChatOutput.info(
                        Text.literal("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500").formatted(Formatting.DARK_GRAY)
                     );

                     for (SuiteServer server : SuiteRuntime.profile().servers()) {
                        sendLifetimeLine(server.displayName(), realmFormatting(server.key()), SuiteConfig.INSTANCE.JobsConfig.lifetimeForServer(server.key()));
                     }

                     return 1;
                  }
               )
         );
   }

   private static void sendLifetimeLine(String name, Formatting nameColor, double amount) {
      ChatOutput.info(
         Text.literal(name)
            .formatted(nameColor)
            .append(Text.literal(": $").formatted(Formatting.GRAY))
            .append(Text.literal(TextUtil.fmtMoney(amount)).formatted(Formatting.GREEN))
      );
   }

   private static String commandText(String suffix) {
      return "/" + SuiteRuntime.profile().commandName() + " " + (suffix == null ? "" : suffix);
   }

   private static boolean requireActiveWorld() {
      if (SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         return true;
      }

      ChatOutput.info(SuiteRuntime.profile().displayName() + " is inactive in this world. Use " + commandText("options").trim() + " to change Activation.");
      return false;
   }

   private static int notifyHeldItem() {
      if (!requireActiveWorld()) {
         return 0;
      }

      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.player != null) {
         ItemStack held = client.player.getMainHandStack();
         if (held != null && !held.isEmpty()) {
            String itemName = TextUtil.stripLegacySectionCodes(held.getName()).getString();
            if (itemName == null || itemName.isBlank()) {
               itemName = held.getItem().getName().getString();
            }

            if (itemName == null || itemName.isBlank()) {
               itemName = "Held item";
            }

            ScreenNoticeOverlay.show(itemName + " is ready", held, 5635925, 1800L);
            ChatOutput.info("Showing held item notification preview.");
            return 1;
         } else {
            ChatOutput.info("No item in hand");
            return 1;
         }
      } else {
         return 0;
      }
   }

   private static int setCurrentServerLifetime(String rawAmount) {
      if (!requireActiveWorld()) {
         return 0;
      }

      Double amount = parseMoneyAmount(rawAmount);
      if (amount == null) {
         ChatOutput.info("Usage: " + commandText("jobs setlifetime <amount>"));
         ChatOutput.info("Examples: " + commandText("jobs setlifetime 1250000") + " or 1.25m");
         return 0;
      }

      String realm = recognizedRealm(JobsChattextSetup.realmName);
      if (realm == null) {
         realm = recognizedRealm(WorldGate.Server);
      }

      if (realm == null) {
         ChatOutput.info("Could not set lifetime: current server is not recognized for " + SuiteRuntime.profile().displayName() + ".");
         return 0;
      } else {
         SuiteConfig.INSTANCE.JobsConfig.setLifetimeForServer(realm, amount);
         ConfigIO.saveIfDirty();
         ChatOutput.info(
            Text.literal("Set ")
               .formatted(Formatting.GRAY)
               .append(Text.literal(SuiteRuntime.profile().serverDisplayName(realm)).formatted(realmFormatting(realm)))
               .append(Text.literal(" jobs lifetime to $").formatted(Formatting.GRAY))
               .append(Text.literal(TextUtil.fmtMoney(amount)).formatted(Formatting.GREEN))
         );
         return 1;
      }
   }

   private static String recognizedRealm(String raw) {
      if (raw == null) {
         return null;
      }

      String key = SuiteServer.normalizeKey(raw);
      return SuiteRuntime.profile().isTrackedServerKey(key) ? key : null;
   }

   private static Formatting realmFormatting(String realm) {
      return switch (realm) {
         case "cherry" -> Formatting.LIGHT_PURPLE;
         case "spirit" -> Formatting.BLUE;
         case "lotus" -> Formatting.GREEN;
         case "tulip" -> Formatting.YELLOW;
         case "cosmic" -> Formatting.LIGHT_PURPLE;
         case "arcane" -> Formatting.AQUA;
         case "elysium" -> Formatting.GREEN;
         default -> Formatting.WHITE;
      };
   }

   private static Double parseMoneyAmount(String raw) {
      if (raw == null) {
         return null;
      }

      String s = raw.trim().toLowerCase(Locale.ROOT);
      if (s.isEmpty()) {
         return null;
      }

      s = s.replace("$", "");
      s = s.replace(",", "");
      s = s.replace("_", "");
      s = s.replace(" ", "");
      double multiplier = 1.0;
      if (s.endsWith("k")) {
         multiplier = 1000.0;
         s = s.substring(0, s.length() - 1);
      } else if (s.endsWith("m")) {
         multiplier = 1000000.0;
         s = s.substring(0, s.length() - 1);
      } else if (s.endsWith("b")) {
         multiplier = 1.0E9;
         s = s.substring(0, s.length() - 1);
      } else if (s.endsWith("t")) {
         multiplier = 1.0E12;
         s = s.substring(0, s.length() - 1);
      }

      if (s.isBlank()) {
         return null;
      }

      try {
         double parsed = Double.parseDouble(s) * multiplier;
         return Double.isFinite(parsed) && !(parsed < 0.0) ? parsed : null;
      } catch (Exception ignored) {
         return null;
      }
   }

   private static void reportTrade() {
      MinecraftClient client = MinecraftClient.getInstance();
      long now = System.currentTimeMillis();
      if (client != null && client.player != null && WorldGate.isActive()) {
         AltResourceState.scanInventoryNow(client, WorldGate.Server, now);
         AltResourceState.scanOpenContainerNow(client, WorldGate.Server, now);
      }

      List<AltResourceState.Snapshot> snapshots = AltResourceState.snapshots();
      ChatOutput.info(Text.literal("---- " + SuiteRuntime.profile().displayName().toUpperCase(Locale.ROOT) + " SERVER DATA ----").formatted(Formatting.GOLD));
      if (snapshots.isEmpty()) {
         ChatOutput.info(Text.literal("No server data tracked yet.").formatted(Formatting.GRAY));
      } else {
         for (AltResourceState.Snapshot snapshot : snapshots) {
            ChatOutput.info(
               Text.literal(snapshot.displayName())
                  .formatted(Formatting.AQUA)
                  .append(Text.literal(": ").formatted(Formatting.DARK_GRAY))
                  .append(Text.literal(AltResourceState.formatBalance(snapshot)).formatted(Formatting.GREEN))
                  .append(Text.literal(" | ").formatted(Formatting.DARK_GRAY))
                  .append(Text.literal(AltResourceState.formatClaimBlocks(snapshot)).formatted(Formatting.YELLOW))
                  .append(Text.literal(" Claim Blocks").formatted(Formatting.GRAY))
                  .append(Text.literal(" | ").formatted(Formatting.DARK_GRAY))
                  .append(Text.literal(AltResourceState.formatTrackedResource(snapshot)).formatted(Formatting.LIGHT_PURPLE))
                  .append(Text.literal(" " + SuiteRuntime.profile().primaryResourceDisplayName()).formatted(Formatting.GRAY))
                  .append(Text.literal(" (").formatted(Formatting.DARK_GRAY))
                  .append(Text.literal(AltResourceState.formatTrackedResourceBreakdown(snapshot)).formatted(Formatting.GRAY))
                  .append(Text.literal(")").formatted(Formatting.DARK_GRAY))
                  .append(Text.literal(" | Updated ").formatted(Formatting.DARK_GRAY))
                  .append(Text.literal(AltResourceState.formatLastUpdated(snapshot, now)).formatted(Formatting.GRAY))
            );
         }
      }
   }

   private static int addRentalFromHand(String rawMinutes, String rawPlace) {
      if (!requireActiveWorld()) {
         return 0;
      }

      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.player != null) {
         int minutes;
         try {
            minutes = Integer.parseInt(rawMinutes == null ? "" : rawMinutes.trim());
         } catch (Exception ignored) {
            ChatOutput.info("Usage: " + commandText("rent <minutes> [place]"));
            return 0;
         }

         if (minutes >= 1 && minutes <= 10080) {
            ItemStack held = client.player.getMainHandStack();
            if (held != null && !held.isEmpty()) {
               String itemName = held.getName().getString();
               if (itemName == null || itemName.isBlank()) {
                  itemName = held.getItem().getName().getString();
               }

               if (itemName == null || itemName.isBlank()) {
                  itemName = "Rental";
               }

               String place = rawPlace == null ? "" : rawPlace.trim();
               if (place.isEmpty()) {
                  place = WorldGate.Server != null && !WorldGate.Server.isBlank() ? WorldGate.Server : "Unknown";
               }

               SuiteConfig.INSTANCE.RentalsConfig.addRental(itemName, place, minutes);
               ConfigIO.saveIfDirty();
               ChatOutput.info(
                  Text.literal("Tracking rental: ")
                     .formatted(Formatting.GRAY)
                     .append(Text.literal(itemName).formatted(Formatting.AQUA))
                     .append(Text.literal(" for " + minutes + " min at ").formatted(Formatting.GRAY))
                     .append(Text.literal(place).formatted(Formatting.YELLOW))
               );
               return 1;
            } else {
               ChatOutput.info("Hold the rented item in your main hand first.");
               return 0;
            }
         } else {
            ChatOutput.info("Rental time must be between 1 and 10080 minutes.");
            return 0;
         }
      } else {
         return 0;
      }
   }

   public static int pauseRentals() {
      if (!requireActiveWorld()) {
         return 0;
      } else {
         RentalsConfig rentals = SuiteConfig.INSTANCE.RentalsConfig;
         if (!rentals.hasActiveRentals()) {
            ChatOutput.info("No active rentals to pause.");
            return 0;
         } else if (!rentals.hasRunningRentals()) {
            ChatOutput.info("Rentals are already paused.");
            return 0;
         } else {
            rentals.pauseAll();
            ConfigIO.saveIfDirty();
            ChatOutput.info("Rental timers paused.");
            return 1;
         }
      }
   }

   public static int resumeRentals() {
      if (!requireActiveWorld()) {
         return 0;
      } else {
         RentalsConfig rentals = SuiteConfig.INSTANCE.RentalsConfig;
         if (!rentals.hasPausedRentals()) {
            ChatOutput.info("No paused rentals to resume.");
            return 0;
         } else {
            rentals.resumeAll();
            ConfigIO.saveIfDirty();
            ChatOutput.info("Rental timers resumed.");
            return 1;
         }
      }
   }

   public static int toggleRentalsPaused() {
      if (!requireActiveWorld()) {
         return 0;
      } else {
         RentalsConfig rentals = SuiteConfig.INSTANCE.RentalsConfig;
         if (!rentals.hasActiveRentals()) {
            ChatOutput.info("No active rentals to pause.");
            return 0;
         } else if (rentals.hasRunningRentals()) {
            rentals.pauseAll();
            ConfigIO.saveIfDirty();
            ChatOutput.info("Rental timers paused.");
            return 1;
         } else {
            rentals.resumeAll();
            ConfigIO.saveIfDirty();
            ChatOutput.info("Rental timers resumed.");
            return 1;
         }
      }
   }

   public static int clearExpiredRentals() {
      if (!requireActiveWorld()) {
         return 0;
      } else {
         int cleared = SuiteConfig.INSTANCE.RentalsConfig.clearFinished();
         ConfigIO.saveIfDirty();
         if (cleared <= 0) {
            ChatOutput.info("No expired rental counters to clear.");
            return 0;
         } else {
            ChatOutput.info("Cleared " + cleared + " expired rental counter" + (cleared == 1 ? "." : "s."));
            return 1;
         }
      }
   }

   private static void report() {
      MinecraftClient client = MinecraftClient.getInstance();
      List<JobsTracker.SegmentSnapshot> segments = SegmentStore.getRecent12();
      if (client != null && client.player != null) {
         client.player
            .sendMessage(
               Text.literal("---- " + SuiteRuntime.profile().displayName().toUpperCase(Locale.ROOT) + " LAST 12 SEGMENTS ----").formatted(Formatting.GOLD),
               false
            );

         for (JobsTracker.SegmentSnapshot s : segments) {
            String timeStr = TextUtil.fmtStopwatch(s.activeMs());
            String moneyStr = "$" + TextUtil.fmtMoney(s.totalMoney());
            String rateStr = TextUtil.fmtRate(s.moneyPerHr());
            int rateRgb = RateColors.rateColor(true, false, s.moneyPerHr()) & 16777215;
            Text line = Text.literal("[" + timeStr + "] ")
               .formatted(Formatting.DARK_GRAY)
               .append(Text.literal(moneyStr + "  ").formatted(Formatting.WHITE))
               .append(Text.literal(rateStr).setStyle(Style.EMPTY.withColor(rateRgb)))
               .append(Text.literal(" | "))
               .append(Text.literal(TextUtil.fmtExp(s.totalExp())))
               .append(Text.literal(" "))
               .append(Text.literal(TextUtil.fmtRate(s.expPerHr())));
            client.player.sendMessage(line, false);
         }
      }
   }
}
