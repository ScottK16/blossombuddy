package org.blossomsuite.core.finder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContainerIndexTest {
   private ContainerIndex index;

   private static ContainerIndex.Container chest(String realm, int x, long updated, ContainerIndex.Stack... items) {
      ContainerIndex.Container c = new ContainerIndex.Container();
      c.realm = realm;
      c.dimension = "minecraft:overworld";
      c.x = x;
      c.y = 64;
      c.z = 0;
      c.label = "Chest";
      c.key = ContainerIndex.keyFor(realm, c.dimension, c.x, c.y, c.z, false);
      c.updatedMs = updated;
      c.items = List.of(items).stream().collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
      return c;
   }

   @BeforeEach
   void setUp() {
      this.index = new ContainerIndex();
   }

   @Test
   void searchFindsByNameOrIdAndSortsByMostFirst() {
      this.index.record(chest("cherry", 1, 10, new ContainerIndex.Stack("minecraft:diamond", "Diamond", 5)));
      this.index.record(chest("cherry", 2, 20, new ContainerIndex.Stack("minecraft:diamond", "Diamond", 64), new ContainerIndex.Stack("minecraft:dirt", "Dirt", 900)));
      this.index.record(chest("cherry", 3, 30, new ContainerIndex.Stack("minecraft:dirt", "Dirt", 12)));

      List<ContainerIndex.Hit> hits = this.index.search("diamond", null);
      assertEquals(2, hits.size());
      assertEquals(64, hits.get(0).count());
      assertEquals(2, hits.get(0).container().x);
      assertEquals(5, hits.get(1).count());
   }

   @Test
   void everyWordMustMatchAndCaseDoesNotMatter() {
      this.index.record(chest("cherry", 1, 10, new ContainerIndex.Stack("minecraft:diamond_block", "Block of Diamond", 9)));
      this.index.record(chest("cherry", 2, 10, new ContainerIndex.Stack("minecraft:diamond", "Diamond", 9)));

      assertEquals(2, this.index.search("DIAMOND", null).size());
      assertEquals(1, this.index.search("block diamond", null).size());
      assertTrue(this.index.search("netherite", null).isEmpty());
      assertTrue(this.index.search("   ", null).isEmpty());
   }

   @Test
   void customNamedItemsAreFoundByTheirName() {
      this.index.record(chest("cherry", 1, 10, new ContainerIndex.Stack("minecraft:paper", "Spirit Voucher", 3)));
      assertEquals(1, this.index.search("voucher", null).size());
      assertEquals("Spirit Voucher", this.index.search("voucher", null).get(0).itemName());
   }

   @Test
   void realmCanBeFilteredAndAReopenedChestReplacesWhatWasThere() {
      this.index.record(chest("cherry", 1, 10, new ContainerIndex.Stack("minecraft:diamond", "Diamond", 5)));
      this.index.record(chest("spirit", 1, 10, new ContainerIndex.Stack("minecraft:diamond", "Diamond", 7)));
      assertEquals(1, this.index.search("diamond", "spirit").size());

      this.index.record(chest("cherry", 1, 99)); // looked again: now empty
      assertTrue(this.index.search("diamond", "cherry").isEmpty());
      assertEquals(2, this.index.size());
   }

   @Test
   void theOldestContainersAreDroppedAtTheLimit() {
      for (int i = 0; i < ContainerIndex.MAX_CONTAINERS + 5; i++) {
         this.index.record(chest("cherry", i, i, new ContainerIndex.Stack("minecraft:stone", "Stone", 1)));
      }

      assertEquals(ContainerIndex.MAX_CONTAINERS, this.index.size());
      assertEquals(ContainerIndex.MAX_CONTAINERS, this.index.search("stone", null).size());
      assertTrue(this.index.search("stone", null).stream().noneMatch(h -> h.container().x < 5), "the first five went");
   }

   @Test
   void anEnderChestHasOneEntryPerRealmWhereverItIsOpened() {
      assertEquals(ContainerIndex.keyFor("cherry", "a", 1, 2, 3, true), ContainerIndex.keyFor("cherry", "b", 9, 9, 9, true));
      assertTrue(!ContainerIndex.keyFor("cherry", "a", 1, 2, 3, true).equals(ContainerIndex.keyFor("spirit", "a", 1, 2, 3, true)));
   }

   @Test
   void theIndexSurvivesAReloadAndADamagedFileStartsAgain(@TempDir Path dir) throws Exception {
      Path file = dir.resolve("containers.json");
      this.index.record(chest("cherry", 1, 10, new ContainerIndex.Stack("minecraft:diamond", "Diamond", 5)));
      this.index.save(file);

      ContainerIndex again = new ContainerIndex();
      again.load(file);
      assertEquals(1, again.search("diamond", null).size());
      assertEquals(5, again.search("diamond", null).get(0).count());

      java.nio.file.Files.writeString(file, "{{ not json");
      again.load(file);
      assertEquals(0, again.size());
   }
}
