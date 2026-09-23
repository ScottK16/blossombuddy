package org.blossomsuite.core.hud;

public interface DraggableHud {
   String id();

   int x();

   int y();

   int w();

   int h();

   float posX();

   float posY();

   void setPos(float var1, float var2);

   boolean enabled();

   default boolean resizable() {
      return false;
   }

   default float scale() {
      return 1.0F;
   }

   default void setScale(float s) {
   }

   default float minScale() {
      return 0.1F;
   }

   default float maxScale() {
      return 2.0F;
   }

   default int baseW() {
      return this.w();
   }

   default int baseH() {
      return this.h();
   }

   default int minPixelWidth() {
      return 12;
   }

   default int minPixelHeight() {
      return 8;
   }

   default boolean supportsBackgroundOpacity() {
      return true;
   }

   default float backgroundOpacity() {
      return 0.33F;
   }

   default void setBackgroundOpacity(float opacity) {
   }

   default float minBackgroundOpacity() {
      return 0.0F;
   }

   default float maxBackgroundOpacity() {
      return 1.0F;
   }
}
