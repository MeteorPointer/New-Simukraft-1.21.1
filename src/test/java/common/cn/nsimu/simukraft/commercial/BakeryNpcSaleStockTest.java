package common.cn.nsimu.simukraft.commercial;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

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

    private static boolean clerkShouldConsume(CommercialOffer offer) {
        return !(offer.stock() != null && offer.stock().materialBacked() && offer.itemLeavesStock());
    }
}
