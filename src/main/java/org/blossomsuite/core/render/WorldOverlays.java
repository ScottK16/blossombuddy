package org.blossomsuite.core.render;

import org.blossomsuite.core.SuiteFeature;
import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.config.QolConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.qol.holepuncher.HolePuncher;
import java.awt.Color;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

public final class WorldOverlays {
   private static final int GUIDE_STEP = 5;
   private static final int MARKER_RANGE = 32;
   private static final int MINING_TRACK_RANGE_MIN = 12;
   private static final int MINING_TRACK_RANGE_MAX = 2048;
   private static WorldOverlays.TrackAxis currentMiningAxis = WorldOverlays.TrackAxis.X;


   private WorldOverlays() {
   }

   public static void init() {
      // Overlays are drawn as Minecraft "gizmos" (world-space boxes and lines). They go through the game's per-tick collector, the
      // way its own debug overlays do: what is added on a tick is drawn every frame until the next tick replaces it.
      ClientTickEvents.END_CLIENT_TICK.register(client -> {
         if (client != null && client.player != null && client.level != null) {
            SuiteConfig cfg = SuiteConfig.INSTANCE;
            if (cfg != null && cfg.QolConfig != null && cfg.isEnabledForCurrentWorld()) {
               try (Gizmos.TemporaryCollection ignored = client.collectPerTickGizmos()) {
                  drawHolePuncherOverlays(client, cfg);
                  drawMiningTrackOverlay(client, cfg);
               }
            }
         }
      });
   }

   private static void drawHolePuncherOverlays(Minecraft client, SuiteConfig cfg) {
      if (SuiteRuntime.isEnabled(SuiteFeature.HOLE_PUNCHER)) {
         if (cfg != null && cfg.QolConfig != null) {
            if (cfg.QolConfig.holePuncherEnabled) {
               if (cfg.QolConfig.holePuncherGuided) {
                  BlockPos anchor = HolePuncher.getGuideAnchor();
                  if (anchor != null) {
                     if (cfg.QolConfig.holePuncherVisualMode == 1 && cfg.QolConfig.holePuncherMarkersEnabled) {
                        drawGridMarkers(client, anchor);
                     }

                     if (cfg.QolConfig.holePuncherVisualMode == 0) {
                        HitResult hr = client.hitResult;
                        if (hr != null && hr.getType() == Type.BLOCK) {
                           BlockHitResult bhr = (BlockHitResult)hr;
                           BlockPos target = bhr.getBlockPos();
                           Direction face = bhr.getDirection();
                           BlockPos behind = target.relative(face.getOpposite());
                           boolean onGrid = isOnGuideGrid(anchor, target);
                           float r = onGrid ? 0.2F : 1.0F;
                           float g = onGrid ? 0.95F : 0.78F;
                           float b = onGrid ? 0.2F : 0.1F;
                           float a = 0.26F;
                           drawBlockOverlay(target, r, g, b, a);
                           drawBlockOverlay(behind, r, g, b, a);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void drawMiningTrackOverlay(Minecraft client, SuiteConfig cfg) {
      if (cfg != null && cfg.QolConfig != null) {
         QolConfig q = cfg.QolConfig;
         if (q.miningTrackIndicator) {
            if (client != null && client.level != null) {
               Level world = client.level;
               String dirStr = q.miningTrackDir == null ? "" : q.miningTrackDir.trim().toLowerCase();
               if (!dirStr.isBlank()) {
                  Direction d = switch (dirStr) {
                     case "north" -> Direction.NORTH;
                     case "south" -> Direction.SOUTH;
                     case "east" -> Direction.EAST;
                     case "west" -> Direction.WEST;
                     default -> null;
                  };
                  if (d != null) {
                     BlockPos p = client.player.blockPosition();
                     int y = p.getY();
                     boolean alongZ = d == Direction.NORTH || d == Direction.SOUTH;
                     int drift = alongZ ? p.getX() - q.miningTrackCoord : p.getZ() - q.miningTrackCoord;
                     boolean onTrack = drift == 0;
                     float a = 0.55F;
                     int thickness = q.miningTrackLineThickness;
                     if (thickness < 1) {
                        thickness = 1;
                     }

                     if (thickness > 4) {
                        thickness = 4;
                     }

                     int stepX = alongZ ? 0 : 1;
                     int stepZ = alongZ ? 1 : 0;
                     int range = q.miningTrackRangeBlocks;
                     if (range <= 0) {
                        range = autoMiningTrackRange(client);
                     }

                     if (range < 12) {
                        range = 12;
                     }

                     if (range > 2048) {
                        range = 2048;
                     }

                     WorldOverlays.TrackAxis prevAxis = currentMiningAxis;
                     currentMiningAxis = alongZ ? WorldOverlays.TrackAxis.Z : WorldOverlays.TrackAxis.X;
                     long now = System.currentTimeMillis();

                     try {
                        for (int i = -range; i <= range; i++) {
                           if (i != 0) {
                              int x;
                              int z;
                              if (alongZ) {
                                 x = q.miningTrackCoord;
                                 z = p.getZ() + stepZ * i;
                              } else {
                                 x = p.getX() + stepX * i;
                                 z = q.miningTrackCoord;
                              }

                              WorldOverlays.ColorRgb color = miningTrackColor(q, onTrack, i, range, now);

                              for (int dy = -1; dy <= 2; dy++) {
                                 BlockPos bp = new BlockPos(x, y + dy, z);
                                 if (shouldOutlineBlock(world, bp)) {
                                    drawBlockOutline(bp, color.r, color.g, color.b, a, thickness);
                                 }
                              }
                           }
                        }
                     } finally {
                        currentMiningAxis = prevAxis;
                     }
                  }
               }
            }
         }
      }
   }

   private static WorldOverlays.ColorRgb miningTrackColor(QolConfig q, boolean onTrack, int offset, int range, long nowMs) {
      QolConfig.MiningTrackColorMode mode = onTrack ? q.miningTrackOnTrackColorMode : q.miningTrackOffTrackColorMode;
      if (mode == null) {
         mode = QolConfig.MiningTrackColorMode.SOLID;
      }

      if (mode == QolConfig.MiningTrackColorMode.RAINBOW) {
         float hue = (float)(nowMs % 3500L) / 3500.0F + offset * 0.015F;
         hue -= (float)Math.floor(hue);
         return WorldOverlays.ColorRgb.fromPacked(Color.HSBtoRGB(hue, 0.85F, 1.0F));
      } else {
         int r1 = onTrack ? q.miningTrackOnTrackR : q.miningTrackOffTrackR;
         int g1 = onTrack ? q.miningTrackOnTrackG : q.miningTrackOffTrackG;
         int b1 = onTrack ? q.miningTrackOnTrackB : q.miningTrackOffTrackB;
         return new WorldOverlays.ColorRgb(clampColorFloat(r1), clampColorFloat(g1), clampColorFloat(b1));
      }
   }

   private static float clampColorFloat(int value) {
      return Math.max(0, Math.min(255, value)) / 255.0F;
   }

   private static boolean shouldOutlineBlock(Level world, BlockPos pos) {
      if (world != null && pos != null) {
         try {
            return !world.getBlockState(pos).isAir();
         } catch (Throwable ignored) {
            return false;
         }
      } else {
         return false;
      }
   }

   private static int autoMiningTrackRange(Minecraft client) {
      if (client != null && client.options != null) {
         try {
            int chunks = client.options.renderDistance().get();
            return Math.max(64, Math.min(2048, chunks * 16));
         } catch (Throwable ignored) {
            return 128;
         }
      } else {
         return 128;
      }
   }

   private static void drawBlockOverlay(BlockPos pos, float r, float g, float b, float a) {
      if (pos != null) {
         AABB box = new AABB(pos).inflate(0.002);
         Gizmos.cuboid(box, GizmoStyle.fill(argb(r, g, b, a)));
      }
   }

   private static void drawBlockOutline(BlockPos pos, float r, float g, float b, float a, int thickness) {
      if (pos != null) {
         if (thickness < 1) {
            thickness = 1;
         }

         if (thickness > 4) {
            thickness = 4;
         }

         drawLineBoxParallel(new AABB(pos).inflate(0.002), argb(r, g, b, a), thickness, currentMiningAxis);
      }
   }

   /** The box's edges along the track and its vertical edges (not the ones across the track, which would clutter the line of blocks). */
   private static void drawLineBoxParallel(AABB box, int color, float width, WorldOverlays.TrackAxis axis) {
      if (axis == null) {
         axis = WorldOverlays.TrackAxis.X;
      }

      double x1 = box.minX;
      double y1 = box.minY;
      double z1 = box.minZ;
      double x2 = box.maxX;
      double y2 = box.maxY;
      double z2 = box.maxZ;
      for (double y : new double[] {y1, y2}) {
         for (double c : new double[] {axis == WorldOverlays.TrackAxis.X ? z1 : x1, axis == WorldOverlays.TrackAxis.X ? z2 : x2}) {
            if (axis == WorldOverlays.TrackAxis.X) {
               Gizmos.line(new Vec3(x1, y, c), new Vec3(x2, y, c), color, width);
            } else {
               Gizmos.line(new Vec3(c, y, z1), new Vec3(c, y, z2), color, width);
            }
         }
      }

      for (double x : new double[] {x1, x2}) {
         for (double z : new double[] {z1, z2}) {
            Gizmos.line(new Vec3(x, y1, z), new Vec3(x, y2, z), color, width);
         }
      }
   }

   private static int argb(float r, float g, float b, float a) {
      int ir = Math.max(0, Math.min(255, (int)(r * 255.0F)));
      int ig = Math.max(0, Math.min(255, (int)(g * 255.0F)));
      int ib = Math.max(0, Math.min(255, (int)(b * 255.0F)));
      int ia = Math.max(0, Math.min(255, (int)(a * 255.0F)));
      return ia << 24 | ir << 16 | ig << 8 | ib;
   }

   private static boolean isOnGuideGrid(BlockPos anchor, BlockPos target) {
      if (anchor != null && target != null) {
         int dx = target.getX() - anchor.getX();
         int dz = target.getZ() - anchor.getZ();
         return Math.floorMod(dx, 5) == 0 && Math.floorMod(dz, 5) == 0;
      } else {
         return false;
      }
   }

   private static void drawGridMarkers(Minecraft client, BlockPos anchor) {
      if (client != null && client.player != null) {
         if (anchor != null) {
            BlockPos p = client.player.blockPosition();
            int y = anchor.getY();
            float r = 0.15F;
            float g = 1.0F;
            float b = 0.2F;
            float a = 0.22F;
            int baseX = p.getX();
            int baseZ = p.getZ();

            for (int dx = -32; dx <= 32; dx++) {
               int x = baseX + dx;
               int offX = x - anchor.getX();
               if (Math.floorMod(offX, 5) == 0) {
                  for (int dz = -32; dz <= 32; dz++) {
                     int z = baseZ + dz;
                     int offZ = z - anchor.getZ();
                     if (Math.floorMod(offZ, 5) == 0) {
                        double x1 = x + 0.1;
                        double z1 = z + 0.1;
                        double x2 = x + 0.9;
                        double z2 = z + 0.9;
                        double y1 = y + 1.002;
                        double y2 = y + 1.1;
                        AABB cap = new AABB(x1, y1, z1, x2, y2, z2).inflate(0.002);
                        Gizmos.cuboid(cap, GizmoStyle.fill(argb(r, g, b, a)));
                     }
                  }
               }
            }
         }
      }
   }

   private record ColorRgb(float r, float g, float b) {
      private static WorldOverlays.ColorRgb fromPacked(int rgb) {
         int r = rgb >> 16 & 0xFF;
         int g = rgb >> 8 & 0xFF;
         int b = rgb & 0xFF;
         return new WorldOverlays.ColorRgb(r / 255.0F, g / 255.0F, b / 255.0F);
      }
   }

   private enum TrackAxis {
      X,
      Z;
   }
}
