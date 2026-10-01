package com.example.riskdicesimulator;

import org.junit.Test;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

public class RiskEngineTest {
    private static final double EPS = 1e-12;

    @Test public void diceCountsFollowArmiesAndCapital() {
        assertEquals(0, RiskEngine.attackerDiceCount(1));
        assertEquals(1, RiskEngine.attackerDiceCount(2));
        assertEquals(3, RiskEngine.attackerDiceCount(10));
        assertEquals(0, RiskEngine.defenderDiceCount(0, false));
        assertEquals(2, RiskEngine.defenderDiceCount(5, false));
        assertEquals(3, RiskEngine.defenderDiceCount(5, true));
        assertTrue(RiskEngine.canAttack(2, 1));
        assertFalse(RiskEngine.canAttack(1, 5));
        assertFalse(RiskEngine.canAttack(5, 0));
    }

    @Test public void diceAreSortedAndTiesGoToDefender() {
        RiskEngine.RollResult result = RiskEngine.resolve(new int[]{2, 6, 4}, new int[]{4, 6});
        assertArrayEquals(new int[]{6, 4, 2}, result.attackerDice);
        assertArrayEquals(new int[]{6, 4}, result.defenderDice);
        assertEquals(2, result.attackerLost); // 6 = 6 and 4 = 4: both ties.
        assertEquals(0, result.defenderLost);
        RiskEngine.RollResult split = RiskEngine.resolve(new int[]{6, 4, 2}, new int[]{5, 4});
        assertEquals(1, split.attackerLost);
        assertEquals(1, split.defenderLost);
    }

    @Test public void singlePairIsWonByAttackerIn15Of36() {
        int wins = 0;
        for (int a = 1; a <= 6; a++) for (int d = 1; d <= 6; d++)
            if (RiskEngine.resolve(new int[]{a}, new int[]{d}).defenderLost == 1) wins++;
        assertEquals(15, wins);
        List<RiskEngine.Outcome> outcomes = RiskEngine.rollOutcomes(2, 1, false);
        assertEquals(15 / 36.0, outcomes.get(0).probability, EPS);
    }

    @Test public void threeVersusTwoOutcomesMatchExactCounts() {
        List<RiskEngine.Outcome> outcomes = RiskEngine.rollOutcomes(10, 5, false);
        assertEquals(3, outcomes.size());
        assertEquals(2, outcomes.get(0).defenderLost);
        assertEquals(2890 / 7776.0, outcomes.get(0).probability, EPS);
        assertEquals(2611 / 7776.0, outcomes.get(1).probability, EPS);
        assertEquals(2, outcomes.get(2).attackerLost);
        assertEquals(2275 / 7776.0, outcomes.get(2).probability, EPS);
    }

    @Test public void capitalOutcomesSumToOne() {
        for (int a = 2; a <= 4; a++) for (int d = 1; d <= 3; d++) {
            double total = 0;
            for (RiskEngine.Outcome outcome : RiskEngine.rollOutcomes(a, d, true)) total += outcome.probability;
            assertEquals(1, total, EPS);
        }
        assertTrue(RiskEngine.rollOutcomes(1, 3, false).isEmpty());
    }

    @Test public void battleOddsMatchDefaultScenario() {
        RiskEngine.BattleOdds odds = RiskEngine.battleOdds(10, 5, false);
        assertEquals("87.3%", MainActivity.percent(odds.attackerWin));
        assertEquals(6.1, odds.expectedAttackers, .05);
        assertEquals(0.3, odds.expectedDefenders, .05);
    }

    @Test public void battleOddsTerminalStatesAndSimpleCase() {
        assertEquals(1, RiskEngine.battleOdds(5, 0, false).attackerWin, EPS);
        assertEquals(0, RiskEngine.battleOdds(1, 5, false).attackerWin, EPS);
        assertEquals(15 / 36.0, RiskEngine.battleOdds(2, 1, false).attackerWin, EPS);
        assertTrue(RiskEngine.battleOdds(10, 8, true).attackerWin < RiskEngine.battleOdds(10, 8, false).attackerWin);
    }

    @Test public void largestBattleIsComputableAndCancellable() {
        RiskEngine.BattleOdds odds = RiskEngine.battleOdds(RiskEngine.MAX_ARMIES, RiskEngine.MAX_ARMIES, true);
        assertTrue(odds.attackerWin >= 0 && odds.attackerWin <= 1);
        assertNull(RiskEngine.battleOdds(RiskEngine.MAX_ARMIES, RiskEngine.MAX_ARMIES, false, () -> true));
    }

    @Test public void rollOnceUsesAvailableDice() {
        Random random = new Random(42);
        for (int i = 0; i < 1000; i++) {
            RiskEngine.RollResult result = RiskEngine.rollOnce(3, 7, i % 2 == 0, random);
            assertEquals(2, result.attackerDice.length);
            assertEquals(i % 2 == 0 ? 3 : 2, result.defenderDice.length);
            assertTrue(result.attackerDice[0] >= result.attackerDice[1]);
            assertEquals(2, result.attackerLost + result.defenderLost);
        }
    }

    @Test public void percentFormatsLikeTheWeb() {
        assertEquals("<0.1%", MainActivity.percent(.0004));
        assertEquals(">99.9%", MainActivity.percent(.9996));
        assertEquals("0.0%", MainActivity.percent(0));
        assertEquals("100.0%", MainActivity.percent(1));
    }
}
