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
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
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
      LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal(commandName).then(ClientCommands.literal("reload").executes(ctx -> {
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
      return (LiteralArgumentBuilder<FabricClientCommandSource>)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal(
                                          commandName
                                       )
                                       .executes(
                                          ctx -> {
                                             ((FabricClientCommandSource)ctx.getSource())
                                                .sendFeedback(Component.literal(SuiteRuntime.profile().displayName() + " loaded. Try /" + commandName + " help"));
                                             return 1;
                                          }
                                       ))
                                    .then(ClientCommands.literal("options").executes(ctx -> {
                                       requestSettingsOpen.run();
                                       return 1;
                                    })))
                                 .then(ClientCommands.literal("trade").executes(ctx -> {
                                    if (!requireActiveWorld()) {
                                       return 0;
                                    }

                                    reportTrade();
                                    return 1;
                                 })))
                              .then(ClientCommands.literal("sort").executes(ctx -> {
                                 if (!requireActiveWorld()) {
                                    return 0;
                                 }

                                 InventorySorter.sortPlayerInventory(Minecraft.getInstance());
                                 return 1;
                              })))
                           .then(ClientCommands.literal("debug").then(ClientCommands.literal("notifyhand").executes(ctx -> notifyHeldItem()))))
                        .then(
                           ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal(
                                             "rent"
                                          )
                                          .then(ClientCommands.literal("pause").executes(ctx -> pauseRentals())))
                                       .then(ClientCommands.literal("resume").executes(ctx -> resumeRentals())))
                                    .then(ClientCommands.literal("toggle").executes(ctx -> toggleRentalsPaused())))
                                 .then(ClientCommands.literal("clear").executes(ctx -> clearExpiredRentals())))
                              .then(((RequiredArgumentBuilder)ClientCommands.argument("minutes", StringArgumentType.word()).executes(ctx -> {
                                 String minutes = StringArgumentType.getString(ctx, "minutes");
                                 return addRentalFromHand(minutes, "");
                              })).then(ClientCommands.argument("place", StringArgumentType.greedyString()).executes(ctx -> {
                                 String minutes = StringArgumentType.getString(ctx, "minutes");
                                 String place = StringArgumentType.getString(ctx, "place");
                                 return addRentalFromHand(minutes, place);
                              })))
                        ))
                  .then(
                     ((LiteralArgumentBuilder)ClientCommands.literal("help")
                           .executes(
                              ctx -> {
                                 ChatOutput.info(Component.literal("-------- " + SuiteRuntime.profile().displayName() + " --------").withStyle(ChatFormatting.DARK_GRAY));
                                 ChatOutput.info(
                                    Component.literal(commandText("help "))
                                       .withStyle(ChatFormatting.GRAY)
                                       .append(Component.literal("- Shows this menu").withStyle(ChatFormatting.DARK_GRAY))
                                 );
                                 ChatOutput.info(
                                    Component.literal(commandText("options "))
                                       .withStyle(ChatFormatting.GRAY)
                                       .append(Component.literal("- Open settings UI").withStyle(ChatFormatting.DARK_GRAY))
                                 );
                                 ChatOutput.info(
                                    Component.literal(commandText("reload "))
                                       .withStyle(ChatFormatting.GRAY)
                                       .append(Component.literal("- Reload cooldown rules from disk").withStyle(ChatFormatting.DARK_GRAY))
                                 );
                                 ChatOutput.info(
                                    Component.literal(commandText("editmode "))
                                       .withStyle(ChatFormatting.GRAY)
                                       .append(Component.literal("- Move HUD elements").withStyle(ChatFormatting.DARK_GRAY))
                                 );
                                 ChatOutput.info(
                                    Component.literal(commandText("totals "))
                                       .withStyle(ChatFormatting.GRAY)
                                       .append(Component.literal("- View lifetime job earnings").withStyle(ChatFormatting.DARK_GRAY))
                                 );
                                 ChatOutput.info(
                                    Component.literal(commandText("trade "))
                                       .withStyle(ChatFormatting.GRAY)
                                       .append(
                                          Component.literal("- View tracked balance, claim blocks, and " + SuiteRuntime.profile().primaryResourceDisplayName())
                                             .withStyle(ChatFormatting.DARK_GRAY)
                                       )
                                 );
                                 ChatOutput.info(
                                    Component.literal(commandText("sort "))
                                       .withStyle(ChatFormatting.GRAY)
                                       .append(Component.literal("- Sort unlocked inventory slots").withStyle(ChatFormatting.DARK_GRAY))
                                 );
                                 ChatOutput.info(
                                    Component.literal(commandText("rent <minutes> [place] "))
                                       .withStyle(ChatFormatting.GRAY)
                                       .append(Component.literal("- Track the held item as a rental").withStyle(ChatFormatting.DARK_GRAY))
                                 );
                                 ChatOutput.info(
                                    Component.literal(commandText("rent clear "))
                                       .withStyle(ChatFormatting.GRAY)
                                       .append(Component.literal("- Clear expired rental counters").withStyle(ChatFormatting.DARK_GRAY))
                                 );
                                 ChatOutput.info(
                                    Component.literal(commandText("jobs segment report "))
                                       .withStyle(ChatFormatting.GRAY)
                                       .append(Component.literal("- View job segment breakdown").withStyle(ChatFormatting.DARK_GRAY))
                                 );
                                 ChatOutput.info(
                                    Component.literal(commandText("jobs setlifetime <amount> "))
                                       .withStyle(ChatFormatting.GRAY)
                                       .append(Component.literal("- Set lifetime jobs total for your current recognized server").withStyle(ChatFormatting.DARK_GRAY))
                                 );
                                 ChatOutput.info(
                                    Component.literal(commandText("help debug hand "))
                                       .withStyle(ChatFormatting.GRAY)
                                       .append(Component.literal("- Debug held item").withStyle(ChatFormatting.DARK_GRAY))
                                 );
                                 ChatOutput.info(
                                    Component.literal(commandText("help debug nbt "))
                                       .withStyle(ChatFormatting.GRAY)
                                       .append(Component.literal("- Log held item NBT").withStyle(ChatFormatting.DARK_GRAY))
                                 );
                                 ChatOutput.info(
                                    Component.literal(commandText("debug notifyhand "))
                                       .withStyle(ChatFormatting.GRAY)
                                       .append(Component.literal("- Show held item in the screen notice spot").withStyle(ChatFormatting.DARK_GRAY))
                                 );
                                 ChatOutput.info(
                                    Component.literal(
                                          "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500"
                                       )
                                       .withStyle(ChatFormatting.DARK_GRAY)
                                 );
                                 return 1;
                              }
                           ))
                        .then(
                           ((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("debug")
                                    .then(ClientCommands.literal("hand").executes(ctx -> {
                                       if (!requireActiveWorld()) {
                                          return 0;
                                       } else {
                                          Minecraft client = Minecraft.getInstance();
                                          if (client.player == null) {
                                             return 0;
                                          } else {
                                             ItemStack held = client.player.getMainHandItem();
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
                                 .then(ClientCommands.literal("notifyhand").executes(ctx -> notifyHeldItem())))
                              .then(ClientCommands.literal("nbt").executes(ctx -> {
                                 if (!requireActiveWorld()) {
                                    return 0;
                                 }

                                 Minecraft client = Minecraft.getInstance();
                                 if (client.player != null && client.level != null) {
                                    ItemStack held = client.player.getMainHandItem();
                                    if (held.isEmpty()) {
                                       ChatOutput.info("No item in hand");
                                       return 1;
                                    }

                                    try {
                                       Provider lookup = client.level.registryAccess();
                                       Tag element = (Tag)ItemStack.CODEC.encodeStart(lookup.createSerializationContext(NbtOps.INSTANCE), held).getOrThrow();
                                       if (element instanceof CompoundTag compound) {
                                          String snbt = compound.toString();
                                          infoLogger.accept("=== " + SuiteRuntime.profile().displayName() + " HELD ITEM NBT ===", null);
                                          infoLogger.accept(snbt, null);
                                          infoLogger.accept("=== /" + SuiteRuntime.profile().displayName() + " HELD ITEM NBT ===", null);
                                          if (client.keyboardHandler != null) {
                                             client.keyboardHandler.setClipboard(snbt);
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
                  ((LiteralArgumentBuilder)ClientCommands.literal("jobs")
                        .then(ClientCommands.literal("segment").then(((LiteralArgumentBuilder)ClientCommands.literal("report").executes(ctx -> {
                           if (!requireActiveWorld()) {
                              return 0;
                           }

                           report();
                           return 1;
                        })).then(ClientCommands.literal("segments").executes(ctx -> {
                           if (!requireActiveWorld()) {
                              return 0;
                           }

                           report();
                           return 1;
                        })))))
                     .then(
                        ClientCommands.literal("setlifetime")
                           .then(
                              ClientCommands.argument("amount", StringArgumentType.greedyString())
                                 .executes(ctx -> setCurrentServerLifetime(StringArgumentType.getString(ctx, "amount")))
                           )
                     )
               ))
            .then(ClientCommands.literal("editmode").executes(ctx -> {
               if (!requireActiveWorld()) {
                  return 0;
               }

               requestHudEditOpen.run();
               return 1;
            })))
         .then(
            ClientCommands.literal("totals")
               .executes(
                  ctx -> {
                     if (!requireActiveWorld()) {
                        return 0;
                     }

                     ChatOutput.info(
                        Component.literal("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500").withStyle(ChatFormatting.DARK_GRAY)
                     );
                     ChatFormatting setFormatting = realmFormatting(recognizedRealm(JobsChattextSetup.realmName));
                     ChatOutput.info(
                        Component.literal(SuiteRuntime.profile().displayName().toUpperCase(Locale.ROOT))
                           .withStyle(setFormatting, ChatFormatting.BOLD)
                           .append(Component.literal(" \u2022 ").withStyle(ChatFormatting.DARK_GRAY))
                           .append(Component.literal("Job Lifetime").withStyle(ChatFormatting.GRAY))
                     );
                     ChatOutput.info(
                        Component.literal("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500").withStyle(ChatFormatting.DARK_GRAY)
                     );

                     for (SuiteServer server : SuiteRuntime.profile().servers()) {
                        sendLifetimeLine(server.displayName(), realmFormatting(server.key()), SuiteConfig.INSTANCE.JobsConfig.lifetimeForServer(server.key()));
                     }

                     return 1;
                  }
               )
         );
   }

   private static void sendLifetimeLine(String name, ChatFormatting nameColor, double amount) {
      ChatOutput.info(
         Component.literal(name)
            .withStyle(nameColor)
            .append(Component.literal(": $").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(TextUtil.fmtMoney(amount)).withStyle(ChatFormatting.GREEN))
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

      Minecraft client = Minecraft.getInstance();
      if (client != null && client.player != null) {
         ItemStack held = client.player.getMainHandItem();
         if (held != null && !held.isEmpty()) {
            String itemName = TextUtil.stripLegacySectionCodes(held.getHoverName()).getString();
            if (itemName == null || itemName.isBlank()) {
               itemName = held.getItemName().getString();
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
            Component.literal("Set ")
               .withStyle(ChatFormatting.GRAY)
               .append(Component.literal(SuiteRuntime.profile().serverDisplayName(realm)).withStyle(realmFormatting(realm)))
               .append(Component.literal(" jobs lifetime to $").withStyle(ChatFormatting.GRAY))
               .append(Component.literal(TextUtil.fmtMoney(amount)).withStyle(ChatFormatting.GREEN))
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

   private static ChatFormatting realmFormatting(String realm) {
      return switch (realm) {
         case "cherry" -> ChatFormatting.LIGHT_PURPLE;
         case "spirit" -> ChatFormatting.BLUE;
         case "lotus" -> ChatFormatting.GREEN;
         case "tulip" -> ChatFormatting.YELLOW;
         case "cosmic" -> ChatFormatting.LIGHT_PURPLE;
         case "arcane" -> ChatFormatting.AQUA;
         case "elysium" -> ChatFormatting.GREEN;
         default -> ChatFormatting.WHITE;
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
      Minecraft client = Minecraft.getInstance();
      long now = System.currentTimeMillis();
      if (client != null && client.player != null && WorldGate.isActive()) {
         AltResourceState.scanInventoryNow(client, WorldGate.Server, now);
         AltResourceState.scanOpenContainerNow(client, WorldGate.Server, now);
      }

      List<AltResourceState.Snapshot> snapshots = AltResourceState.snapshots();
      ChatOutput.info(Component.literal("---- " + SuiteRuntime.profile().displayName().toUpperCase(Locale.ROOT) + " SERVER DATA ----").withStyle(ChatFormatting.GOLD));
      if (snapshots.isEmpty()) {
         ChatOutput.info(Component.literal("No server data tracked yet.").withStyle(ChatFormatting.GRAY));
      } else {
         for (AltResourceState.Snapshot snapshot : snapshots) {
            ChatOutput.info(
               Component.literal(snapshot.displayName())
                  .withStyle(ChatFormatting.AQUA)
                  .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                  .append(Component.literal(AltResourceState.formatBalance(snapshot)).withStyle(ChatFormatting.GREEN))
                  .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                  .append(Component.literal(AltResourceState.formatClaimBlocks(snapshot)).withStyle(ChatFormatting.YELLOW))
                  .append(Component.literal(" Claim Blocks").withStyle(ChatFormatting.GRAY))
                  .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                  .append(Component.literal(AltResourceState.formatTrackedResource(snapshot)).withStyle(ChatFormatting.LIGHT_PURPLE))
                  .append(Component.literal(" " + SuiteRuntime.profile().primaryResourceDisplayName()).withStyle(ChatFormatting.GRAY))
                  .append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY))
                  .append(Component.literal(AltResourceState.formatTrackedResourceBreakdown(snapshot)).withStyle(ChatFormatting.GRAY))
                  .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY))
                  .append(Component.literal(" | Updated ").withStyle(ChatFormatting.DARK_GRAY))
                  .append(Component.literal(AltResourceState.formatLastUpdated(snapshot, now)).withStyle(ChatFormatting.GRAY))
            );
         }
      }
   }

   private static int addRentalFromHand(String rawMinutes, String rawPlace) {
      if (!requireActiveWorld()) {
         return 0;
      }

      Minecraft client = Minecraft.getInstance();
      if (client != null && client.player != null) {
         int minutes;
         try {
            minutes = Integer.parseInt(rawMinutes == null ? "" : rawMinutes.trim());
         } catch (Exception ignored) {
            ChatOutput.info("Usage: " + commandText("rent <minutes> [place]"));
            return 0;
         }

         if (minutes >= 1 && minutes <= 10080) {
            ItemStack held = client.player.getMainHandItem();
            if (held != null && !held.isEmpty()) {
               String itemName = held.getHoverName().getString();
               if (itemName == null || itemName.isBlank()) {
                  itemName = held.getItemName().getString();
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
                  Component.literal("Tracking rental: ")
                     .withStyle(ChatFormatting.GRAY)
                     .append(Component.literal(itemName).withStyle(ChatFormatting.AQUA))
                     .append(Component.literal(" for " + minutes + " min at ").withStyle(ChatFormatting.GRAY))
                     .append(Component.literal(place).withStyle(ChatFormatting.YELLOW))
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
      Minecraft client = Minecraft.getInstance();
      List<JobsTracker.SegmentSnapshot> segments = SegmentStore.getRecent12();
      if (client != null && client.player != null) {
         client.player
            .sendSystemMessage(
               Component.literal("---- " + SuiteRuntime.profile().displayName().toUpperCase(Locale.ROOT) + " LAST 12 SEGMENTS ----").withStyle(ChatFormatting.GOLD));

         for (JobsTracker.SegmentSnapshot s : segments) {
            String timeStr = TextUtil.fmtStopwatch(s.activeMs());
            String moneyStr = "$" + TextUtil.fmtMoney(s.totalMoney());
            String rateStr = TextUtil.fmtRate(s.moneyPerHr());
            int rateRgb = RateColors.rateColor(true, false, s.moneyPerHr()) & 16777215;
            Component line = Component.literal("[" + timeStr + "] ")
               .withStyle(ChatFormatting.DARK_GRAY)
               .append(Component.literal(moneyStr + "  ").withStyle(ChatFormatting.WHITE))
               .append(Component.literal(rateStr).setStyle(Style.EMPTY.withColor(rateRgb)))
               .append(Component.literal(" | "))
               .append(Component.literal(TextUtil.fmtExp(s.totalExp())))
               .append(Component.literal(" "))
               .append(Component.literal(TextUtil.fmtRate(s.expPerHr())));
            client.player.sendSystemMessage(line);
         }
      }
   }
}
