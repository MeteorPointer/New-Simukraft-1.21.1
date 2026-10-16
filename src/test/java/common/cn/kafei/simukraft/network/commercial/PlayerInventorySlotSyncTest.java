package common.cn.kafei.simukraft.network.commercial;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class PlayerInventorySlotSyncTest {
    @Test
    void hotbarMainInventoryAndOffhandUseThePlayerInventoryContainer() {
        assertArrayEquals(new int[] {-2, 0}, CommercialTradePacket.inventorySlotSync(0));
        assertArrayEquals(new int[] {-2, 8}, CommercialTradePacket.inventorySlotSync(8));
        assertArrayEquals(new int[] {-2, 9}, CommercialTradePacket.inventorySlotSync(9));
        assertArrayEquals(new int[] {-2, 35}, CommercialTradePacket.inventorySlotSync(35));
        assertArrayEquals(new int[] {-2, 40}, CommercialTradePacket.inventorySlotSync(40));
    }
}
