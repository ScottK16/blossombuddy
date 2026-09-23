package org.blossomsuite.core.render;

import org.blossomsuite.core.SuiteFeature;
import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.config.QolConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.qol.holepuncher.HolePuncher;
import java.awt.Color;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class WorldOverlays {
   private static final int GUIDE_STEP = 5;
   private static final int MARKER_RANGE = 32;
   private static final int MINING_TRACK_RANGE_MIN = 12;
   private static final int MINING_TRACK_RANGE_MAX = 2048;
   private static WorldOverlays.TrackAxis currentMiningAxis = WorldOverlays.TrackAxis.X;

   private WorldOverlays() {
   }

   public static void init() {
      WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.player != null && client.world != null) {
            SuiteConfig cfg = SuiteConfig.INSTANCE;
            if (cfg != null && cfg.QolConfig != null) {
               if (cfg.isEnabledForCurrentWorld()) {
                  if (ctx.consumers() != null) {
                     if (ctx.matrixStack() != null) {
                        MatrixStack matrices = ctx.matrixStack();
                        Camera cam = ctx.camera();
                        Vec3d camPos = cam.getPos();
                        matrices.push();
                        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

                        try {
                           drawHolePuncherOverlays(ctx, client, cfg);
                           drawMiningTrackOverlay(ctx, client, cfg);
                        } finally {
                           matrices.pop();
                        }
                     }
                  }
               }
            }
         }
      });
   }

   private static void drawHolePuncherOverlays(WorldRenderContext ctx, MinecraftClient client, SuiteConfig cfg) {
      if (SuiteRuntime.isEnabled(SuiteFeature.HOLE_PUNCHER)) {
         if (cfg != null && cfg.QolConfig != null) {
            if (cfg.QolConfig.holePuncherEnabled) {
               if (cfg.QolConfig.holePuncherGuided) {
                  BlockPos anchor = HolePuncher.getGuideAnchor();
                  if (anchor != null) {
                     if (cfg.QolConfig.holePuncherVisualMode == 1 && cfg.QolConfig.holePuncherMarkersEnabled) {
                        drawGridMarkers(ctx, client, anchor);
                     }

                     if (cfg.QolConfig.holePuncherVisualMode == 0) {
                        HitResult hr = client.crosshairTarget;
                        if (hr != null && hr.getType() == Type.BLOCK) {
                           BlockHitResult bhr = (BlockHitResult)hr;
                           BlockPos target = bhr.getBlockPos();
                           Direction face = bhr.getSide();
                           BlockPos behind = target.offset(face.getOpposite());
                           boolean onGrid = isOnGuideGrid(anchor, target);
                           float r = onGrid ? 0.2F : 1.0F;
                           float g = onGrid ? 0.95F : 0.78F;
                           float b = onGrid ? 0.2F : 0.1F;
                           float a = 0.26F;
                           drawBlockOverlay(ctx, target, r, g, b, a);
                           drawBlockOverlay(ctx, behind, r, g, b, a);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void drawMiningTrackOverlay(WorldRenderContext ctx, MinecraftClient client, SuiteConfig cfg) {
      if (cfg != null && cfg.QolConfig != null) {
         QolConfig q = cfg.QolConfig;
         if (q.miningTrackIndicator) {
            if (client != null && client.world != null) {
               World world = client.world;
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
                     BlockPos p = client.player.getBlockPos();
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
                                    drawBlockOutline(ctx, bp, color.r, color.g, color.b, a, thickness);
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

   private static boolean shouldOutlineBlock(World world, BlockPos pos) {
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

   private static int autoMiningTrackRange(MinecraftClient client) {
      if (client != null && client.options != null) {
         try {
            int chunks = client.options.getViewDistance().getValue();
            return Math.max(64, Math.min(2048, chunks * 16));
         } catch (Throwable ignored) {
            return 128;
         }
      } else {
         return 128;
      }
   }

   private static void drawBlockOverlay(WorldRenderContext ctx, BlockPos pos, float r, float g, float b, float a) {
      if (pos != null) {
         Box box = new Box(pos).expand(0.002);
         DebugRenderer.drawBox(ctx.matrixStack(), ctx.consumers(), box, r, g, b, a);
      }
   }

   private static void drawBlockOutline(WorldRenderContext ctx, BlockPos pos, float r, float g, float b, float a, int thickness) {
      if (pos != null) {
         if (ctx != null && ctx.matrixStack() != null && ctx.consumers() != null) {
            if (thickness < 1) {
               thickness = 1;
            }

            if (thickness > 4) {
               thickness = 4;
            }

            VertexConsumer lines = ctx.consumers().getBuffer(RenderLayer.getLines());

            for (int pass = 0; pass < thickness; pass++) {
               double expand = 0.002 + pass * 0.004;
               Box box = new Box(pos).expand(expand);
               drawLineBoxParallel(ctx.matrixStack(), lines, box, r, g, b, a, currentMiningAxis);
            }
         }
      }
   }

   private static void drawLineBoxParallel(MatrixStack matrices, VertexConsumer vc, Box box, float r, float g, float b, float a, WorldOverlays.TrackAxis axis) {
      if (axis == null) {
         axis = WorldOverlays.TrackAxis.X;
      }

      Matrix4f m = matrices.peek().getPositionMatrix();
      float x1 = (float)box.minX;
      float y1 = (float)box.minY;
      float z1 = (float)box.minZ;
      float x2 = (float)box.maxX;
      float y2 = (float)box.maxY;
      float z2 = (float)box.maxZ;
      int ir = Math.max(0, Math.min(255, (int)(r * 255.0F)));
      int ig = Math.max(0, Math.min(255, (int)(g * 255.0F)));
      int ib = Math.max(0, Math.min(255, (int)(b * 255.0F)));
      int ia = Math.max(0, Math.min(255, (int)(a * 255.0F)));
      if (axis == WorldOverlays.TrackAxis.X) {
         v(vc, m, x1, y1, z1, ir, ig, ib, ia);
         v(vc, m, x2, y1, z1, ir, ig, ib, ia);
         v(vc, m, x1, y1, z2, ir, ig, ib, ia);
         v(vc, m, x2, y1, z2, ir, ig, ib, ia);
         v(vc, m, x1, y2, z1, ir, ig, ib, ia);
         v(vc, m, x2, y2, z1, ir, ig, ib, ia);
         v(vc, m, x1, y2, z2, ir, ig, ib, ia);
         v(vc, m, x2, y2, z2, ir, ig, ib, ia);
      } else {
         v(vc, m, x1, y1, z1, ir, ig, ib, ia);
         v(vc, m, x1, y1, z2, ir, ig, ib, ia);
         v(vc, m, x2, y1, z1, ir, ig, ib, ia);
         v(vc, m, x2, y1, z2, ir, ig, ib, ia);
         v(vc, m, x1, y2, z1, ir, ig, ib, ia);
         v(vc, m, x1, y2, z2, ir, ig, ib, ia);
         v(vc, m, x2, y2, z1, ir, ig, ib, ia);
         v(vc, m, x2, y2, z2, ir, ig, ib, ia);
      }

      v(vc, m, x1, y1, z1, ir, ig, ib, ia);
      v(vc, m, x1, y2, z1, ir, ig, ib, ia);
      v(vc, m, x2, y1, z1, ir, ig, ib, ia);
      v(vc, m, x2, y2, z1, ir, ig, ib, ia);
      v(vc, m, x2, y1, z2, ir, ig, ib, ia);
      v(vc, m, x2, y2, z2, ir, ig, ib, ia);
      v(vc, m, x1, y1, z2, ir, ig, ib, ia);
      v(vc, m, x1, y2, z2, ir, ig, ib, ia);
   }

   private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, int r, int g, int b, int a) {
      int argb = (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | b & 0xFF;
      Vector4f v4 = new Vector4f(x, y, z, 1.0F).mul(m);
      vc.vertex(v4.x, v4.y, v4.z, argb, 0.0F, 0.0F, 0, 0, 0.0F, 1.0F, 0.0F);
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

   private static void drawGridMarkers(WorldRenderContext ctx, MinecraftClient client, BlockPos anchor) {
      if (client != null && client.player != null) {
         if (anchor != null) {
            BlockPos p = client.player.getBlockPos();
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
                        Box cap = new Box(x1, y1, z1, x2, y2, z2).expand(0.002);
                        DebugRenderer.drawBox(ctx.matrixStack(), ctx.consumers(), cap, r, g, b, a);
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
