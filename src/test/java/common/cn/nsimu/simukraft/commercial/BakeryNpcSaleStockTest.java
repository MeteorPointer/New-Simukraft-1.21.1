package common.cn.nsimu.simukraft.commercial;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bakery sells bread made from wheat in the chest. The clerk must not run that sale as a
 * phantom customer, or the SQLite bread counter is drained while the wheat stays in the chest.
 */
class BakeryNpcSaleStockTest {
    @Test
    void materialBackedBreadSaleIsNotAClerkSale() {
        CommercialOffer bread = new CommercialOffer(
                "shop_sells_bread",
                CommercialVisibility.MIXED,
                List.of(CommercialResource.money(0.25D)),
                List.of(CommercialResource.item("minecraft:bread", 1)),
                new CommercialOffer.StockRule("minecraft:bread", 64, 0, 0, 0L,
                        List.of(new CommercialOffer.MaterialRequirement("minecraft:wheat", 3))));

        assertTrue(bread.visibleToNpc());
        assertTrue(bread.itemLeavesStock());
        assertTrue(bread.stock().materialBacked());
        assertFalse(clerkShouldConsume(bread));
    }

    @Test
    void sqliteOnlySaleStillBelongsToTheClerk() {
        CommercialOffer timed = new CommercialOffer(
                "timed_goods",
                CommercialVisibility.NPC,
                List.of(CommercialResource.money(1.0D)),
                List.of(CommercialResource.item("minecraft:apple", 1)),
                new CommercialOffer.StockRule("minecraft:apple", 16, 0, 1, 200L, List.of()));

        assertFalse(timed.stock().materialBacked());
        assertTrue(clerkShouldConsume(timed));
    }

    @Test
    void buyOfferStockIsReleasedAndSharedSellStockIsNot() {
        CommercialOffer sells = new CommercialOffer(
                "buy_oak_planks",
                CommercialVisibility.MIXED,
                List.of(CommercialResource.money(2.24D)),
                List.of(CommercialResource.item("minecraft:oak_planks", 64)),
                new CommercialOffer.StockRule("minecraft:oak_planks", 512, 256, 256, 12000L, List.of()));
        CommercialOffer buys = new CommercialOffer(
                "sell_oak_planks",
                CommercialVisibility.PLAYER,
                List.of(CommercialResource.item("minecraft:oak_planks", 64)),
                List.of(CommercialResource.money(1.79D)),
                new CommercialOffer.StockRule("minecraft:oak_planks", 512, 0, 0, 0L, List.of()));
        CommercialOffer wheat = new CommercialOffer(
                "shop_buys_wheat",
                CommercialVisibility.PLAYER,
                List.of(CommercialResource.item("minecraft:wheat", 16)),
                List.of(CommercialResource.money(3.2D)),
                new CommercialOffer.StockRule("minecraft:wheat", 256, 0, 0, 0L, List.of()));
        CommercialDefinition definition = new CommercialDefinition(
                "shop", "shop", null, null, Map.of(), List.of(sells, buys, wheat), null);

        assertEquals(Set.of("minecraft:oak_planks", "minecraft:wheat"), CommercialStockService.acquiredItemIds(definition));
    }

    private static boolean clerkShouldConsume(CommercialOffer offer) {
        return !(offer.stock() != null && offer.stock().materialBacked() && offer.itemLeavesStock());
    }
}
