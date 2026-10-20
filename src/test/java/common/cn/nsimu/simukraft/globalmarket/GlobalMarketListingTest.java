package common.cn.nsimu.simukraft.globalmarket;

import common.cn.nsimu.simukraft.commercial.CommercialTradeOfferTab;
import common.cn.nsimu.simukraft.commercial.CommercialTradeView;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GlobalMarketListingTest {
    @Test
    void sellAndBuyNeedItemAndPrice() {
        CommercialTradeView.ResourceEntry item = new CommercialTradeView.ResourceEntry("item", "minecraft:oak_planks", 64, 0.0D);
        assertNotNull(GlobalMarketListing.validateDraft(CommercialTradeOfferTab.SELL, null, null, item, 0.0D));
        assertNotNull(GlobalMarketListing.validateDraft(CommercialTradeOfferTab.SELL, null, null, item, 0.004D));
        assertNull(GlobalMarketListing.validateDraft(CommercialTradeOfferTab.SELL, null, null, item, 0.01D));
        assertNull(GlobalMarketListing.validateDraft(CommercialTradeOfferTab.SELL, null, null, item, 2.54D));
        assertNull(GlobalMarketListing.validateDraft(CommercialTradeOfferTab.BUY, item, null, null, 1.76D));
        GlobalMarketListing sell = GlobalMarketListing.fromDraft("sell", UUID.randomUUID(), "city", CommercialTradeOfferTab.SELL, null, null, item, 2.54D);
        assertEquals("sell", sell.mode());
        assertEquals(64, sell.stock());
        assertEquals(64, sell.maxStock());
        assertEquals(1, sell.result().getFirst().count());
        GlobalMarketListing buy = GlobalMarketListing.fromDraft("buy", UUID.randomUUID(), "city", CommercialTradeOfferTab.BUY, item, null, null, 1.76D);
        assertEquals("buy", buy.mode());
        assertEquals(0, buy.stock());
        assertEquals(64, buy.maxStock());
        assertEquals(1, buy.cost().getFirst().count());
        assertNull(GlobalMarketListing.validateDraft(CommercialTradeOfferTab.BUY, item, null, null, 0.01D));
    }

    @Test
    void barterAllowsAtMostTwoItemsForOne() {
        CommercialTradeView.ResourceEntry first = new CommercialTradeView.ResourceEntry("item", "minecraft:cobblestone", 64, 0.0D);
        CommercialTradeView.ResourceEntry second = new CommercialTradeView.ResourceEntry("item", "minecraft:sand", 32, 0.0D);
        CommercialTradeView.ResourceEntry result = new CommercialTradeView.ResourceEntry("item", "minecraft:glass", 16, 0.0D);
        assertNull(GlobalMarketListing.validateDraft(CommercialTradeOfferTab.BARTER, first, null, result, 0.0D));
        assertNull(GlobalMarketListing.validateDraft(CommercialTradeOfferTab.BARTER, first, second, result, 0.0D));
        assertNotNull(GlobalMarketListing.validateDraft(CommercialTradeOfferTab.BARTER, first, null, null, 0.0D));
        GlobalMarketListing listing = GlobalMarketListing.fromDraft("id", UUID.randomUUID(), "city", CommercialTradeOfferTab.BARTER, first, second, result, 0.0D);
        assertEquals(2, listing.cost().size());
        assertEquals(1, listing.result().size());
        assertEquals(16, listing.stock());
        assertEquals(16, listing.result().getFirst().count());
        GlobalMarketListing capped = GlobalMarketListing.fromDraft(
                "cap", UUID.randomUUID(), "city", CommercialTradeOfferTab.BARTER, first, second, result, 0.0D, 128);
        assertEquals(128, capped.stock());
        assertEquals(128, capped.maxStock());
        GlobalMarketListing buyCap = GlobalMarketListing.fromDraft(
                "buyCap", UUID.randomUUID(), "city", CommercialTradeOfferTab.BUY, first, null, null, 1.00D, 128);
        assertEquals(0, buyCap.stock());
        assertEquals(128, buyCap.maxStock());
        CommercialTradeView.ResourceEntry overstack = new CommercialTradeView.ResourceEntry("item", "minecraft:dirt", 128, 0.0D);
        GlobalMarketListing bulk = GlobalMarketListing.fromDraft("bulk", UUID.randomUUID(), "city", CommercialTradeOfferTab.SELL, null, null, overstack, 0.05D);
        assertEquals(128, bulk.stock());
        assertEquals(1, bulk.result().getFirst().count());
        GlobalMarketListing sellQty = GlobalMarketListing.fromDraft(
                "sellQty", UUID.randomUUID(), "city", CommercialTradeOfferTab.SELL, null, null, overstack, 0.05D, 256);
        assertEquals(256, sellQty.stock());
        assertEquals(256, sellQty.maxStock());
        assertEquals(1, sellQty.result().getFirst().count());
    }

    @Test
    void sellKeepsItemStackNbt() {
        CommercialTradeView.ResourceEntry enchanted = new CommercialTradeView.ResourceEntry(
                "item", "minecraft:diamond_sword", 1, 0.0D, "{id:\"minecraft:diamond_sword\",count:1}");
        GlobalMarketListing listing = GlobalMarketListing.fromDraft(
                "sword", UUID.randomUUID(), "city", CommercialTradeOfferTab.SELL, null, null, enchanted, 1.00D);
        assertEquals(enchanted.stackNbt(), listing.result().getFirst().stackNbt());
        assertEquals("minecraft:diamond_sword", listing.result().getFirst().itemId());
    }
}
