package com.mugloar.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mugloar.domain.Reputation;
import com.mugloar.domain.ShopItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class GameMemoriesTest {

    private static final List<ShopItem> SHOP = List.of(new ShopItem("hpot", "Healing potion", 50));

    @Test
    void startsEmptyForAnyGameAndNeverReturnsNull() {
        GameMemories memories = new GameMemories();

        assertThat(memories.of("unknown")).isEqualTo(GameMemory.EMPTY);
        assertThat(memories.of("unknown").knowsShop()).isFalse();
        assertThat(memories.reputationOf("unknown")).isEmpty();
    }

    @Test
    void updatesAreAppliedToWhatIsAlreadyThere() {
        GameMemories memories = new GameMemories();

        memories.update("g1", m -> m.withShop(SHOP));
        memories.update("g1", m -> m.afterIdling(Reputation.NEUTRAL));
        memories.update("g1", m -> m.afterIdling(new Reputation(1, 0, 0)));

        GameMemory memory = memories.of("g1");
        assertThat(memory.shop()).isEqualTo(SHOP);
        assertThat(memory.idlesUsed()).isEqualTo(2);
        assertThat(memory.reputation()).isEqualTo(new Reputation(1, 0, 0));
    }

    @Test
    void forgettingDropsTheEntryEntirely() {
        GameMemories memories = new GameMemories();
        memories.update("g1", m -> m.withShop(SHOP));

        memories.forget("g1");

        assertThat(memories.of("g1")).isEqualTo(GameMemory.EMPTY);
        assertThat(memories.size()).isZero();
    }

    @Test
    void holdsOneEntryPerGameAndNoMore() {
        // There is no size cap on purpose; the bound is that every writer is also a remover. This
        // just pins down that repeated updates to one game do not accumulate entries.
        GameMemories memories = new GameMemories();

        for (int i = 0; i < 50; i++) {
            memories.update("g1", m -> m.afterIdling(Reputation.NEUTRAL));
        }

        assertThat(memories.size()).isEqualTo(1);
        assertThat(memories.of("g1").idlesUsed()).isEqualTo(50);
    }
}
