package com.mugloar.application;

import static com.mugloar.TestFixtures.ad;
import static com.mugloar.TestFixtures.state;
import static org.assertj.core.api.Assertions.assertThat;

import com.mugloar.domain.Ad;
import com.mugloar.domain.Reputation;
import com.mugloar.domain.RiskLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The rule in isolation. It is only ever asked after the strategy has refused the whole board and
 * the shop has declined to help, so these tests do not repeat those decisions.
 */
class WaitingPolicyTest {

    private static Board boardOf(Ad... ads) {
        return new Board(List.of(ads), List.of(), List.of(), new ShopDecision.Skip("no"));
    }

    private static GameMemory afterWaiting(int turns) {
        GameMemory memory = GameMemory.EMPTY;
        for (int i = 0; i < turns; i++) {
            memory = memory.afterIdling(Reputation.NEUTRAL);
        }
        return memory;
    }

    @Test
    void waitsWhenTheBudgetCoversTheSoonestExpiry() {
        WaitingPolicy policy = new WaitingPolicy(10);

        assertThat(policy.reasonToWait(state(1, 0, 0), boardOf(ad("a", 100, 3, RiskLevel.RISKY)),
                GameMemory.EMPTY)).isPresent();
    }

    @Test
    void refusesWhenTheBoardOutlastsTheBudget() {
        // Waiting drops nothing from the board, so it only pays off once something expires. If
        // that is further away than the turns left to spend, every turn spent is wasted.
        WaitingPolicy policy = new WaitingPolicy(3);

        assertThat(policy.reasonToWait(state(1, 0, 0), boardOf(ad("a", 100, 6, RiskLevel.RISKY)),
                GameMemory.EMPTY)).isEmpty();
    }

    @Test
    void theSoonestExpiryIsWhatCountsNotTheAverage() {
        WaitingPolicy policy = new WaitingPolicy(3);
        Board mixed = boardOf(
                ad("late", 100, 7, RiskLevel.RISKY),
                ad("soon", 100, 2, RiskLevel.RISKY));

        assertThat(policy.reasonToWait(state(1, 0, 0), mixed, GameMemory.EMPTY)).isPresent();
    }

    @Test
    void theBudgetIsCumulativeAcrossTheWholeGame() {
        WaitingPolicy policy = new WaitingPolicy(3);
        Board board = boardOf(ad("a", 100, 2, RiskLevel.RISKY));

        assertThat(policy.reasonToWait(state(1, 0, 0), board, afterWaiting(1))).isPresent();
        assertThat(policy.reasonToWait(state(1, 0, 0), board, afterWaiting(2))).isEmpty();
    }

    @Test
    void neverWaitsWhenSwitchedOff() {
        WaitingPolicy policy = new WaitingPolicy(0);

        assertThat(policy.reasonToWait(state(1, 0, 0), boardOf(ad("a", 100, 1, RiskLevel.RISKY)),
                GameMemory.EMPTY)).isEmpty();
    }

    @Test
    void anEmptyBoardIsWaitableIfThereIsAnyBudgetAtAll() {
        WaitingPolicy policy = new WaitingPolicy(1);

        assertThat(policy.reasonToWait(state(1, 0, 0), boardOf(), GameMemory.EMPTY)).isPresent();
    }

    @Test
    void explainsItselfInTheBotsVoiceWithTheRightPlural() {
        WaitingPolicy policy = new WaitingPolicy(10);
        Board board = boardOf(ad("a", 100, 1, RiskLevel.RISKY));

        assertThat(policy.reasonToWait(state(1, 0, 0), board, GameMemory.EMPTY))
                .contains("Nothing worth attempting at 1 life");
        assertThat(policy.reasonToWait(state(2, 0, 0), board, GameMemory.EMPTY))
                .contains("Nothing worth attempting at 2 lives");
    }
}
