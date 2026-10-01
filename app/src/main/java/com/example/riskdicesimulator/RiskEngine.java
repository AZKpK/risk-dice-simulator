package com.example.riskdicesimulator;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;

/** Risk battle rules: sorted dice, ties to the defender, and exact odds. */
public final class RiskEngine {
    public static final int MAX_ARMIES = 2000;
    private static final Random RANDOM = new SecureRandom();

    private RiskEngine() {}

    public static final class RollResult {
        public final int[] attackerDice, defenderDice;
        public final int attackerLost, defenderLost;

        RollResult(int[] attackerDice, int[] defenderDice) {
            this.attackerDice = attackerDice;
            this.defenderDice = defenderDice;
            int lost = 0, pairs = Math.min(attackerDice.length, defenderDice.length);
            for (int i = 0; i < pairs; i++) if (attackerDice[i] <= defenderDice[i]) lost++;
            attackerLost = lost;
            defenderLost = pairs - lost;
        }
    }

    public static final class Outcome {
        public final int attackerLost, defenderLost;
        public final double probability;

        Outcome(int attackerLost, int defenderLost, double probability) {
            this.attackerLost = attackerLost;
            this.defenderLost = defenderLost;
            this.probability = probability;
        }
    }

    public static final class BattleOdds {
        public final double attackerWin, expectedAttackers, expectedDefenders;

        BattleOdds(double attackerWin, double expectedAttackers, double expectedDefenders) {
            this.attackerWin = attackerWin;
            this.expectedAttackers = expectedAttackers;
            this.expectedDefenders = expectedDefenders;
        }
    }

    public static int attackerDiceCount(int attackers) {
        return Math.max(0, Math.min(3, attackers - 1));
    }

    public static int defenderDiceCount(int defenders, boolean capital) {
        return Math.max(0, Math.min(capital ? 3 : 2, defenders));
    }

    public static boolean canAttack(int attackers, int defenders) {
        return attackers >= 2 && defenders >= 1;
    }

    public static RollResult rollOnce(int attackers, int defenders, boolean capital) {
        return rollOnce(attackers, defenders, capital, RANDOM);
    }

    static RollResult rollOnce(int attackers, int defenders, boolean capital, Random random) {
        return resolve(roll(attackerDiceCount(attackers), random), roll(defenderDiceCount(defenders, capital), random));
    }

    /** Sorts both sides high to low and compares pairs. */
    static RollResult resolve(int[] attackerDice, int[] defenderDice) {
        return new RollResult(sortDesc(attackerDice), sortDesc(defenderDice));
    }

    private static int[] roll(int count, Random random) {
        int[] dice = new int[count];
        for (int i = 0; i < count; i++) dice[i] = random.nextInt(6) + 1;
        return dice;
    }

    private static int[] sortDesc(int[] values) {
        int[] sorted = values.clone();
        Arrays.sort(sorted);
        for (int i = 0, j = sorted.length - 1; i < j; i++, j--) {
            int swap = sorted[i];
            sorted[i] = sorted[j];
            sorted[j] = swap;
        }
        return sorted;
    }

    private static List<Outcome> enumerateOutcomes(int attackerCount, int defenderCount) {
        int dice = attackerCount + defenderCount, total = (int) Math.pow(6, dice);
        int[][] tally = new int[4][4];
        for (int index = 0; index < total; index++) {
            int rest = index;
            int[] attack = new int[attackerCount], defense = new int[defenderCount];
            for (int i = 0; i < dice; i++) {
                if (i < attackerCount) attack[i] = rest % 6 + 1;
                else defense[i - attackerCount] = rest % 6 + 1;
                rest /= 6;
            }
            RollResult result = resolve(attack, defense);
            tally[result.attackerLost][result.defenderLost]++;
        }
        List<Outcome> outcomes = new ArrayList<>();
        for (int defenderLost = 3; defenderLost >= 0; defenderLost--)
            for (int attackerLost = 0; attackerLost <= 3; attackerLost++)
                if (tally[attackerLost][defenderLost] > 0)
                    outcomes.add(new Outcome(attackerLost, defenderLost, (double) tally[attackerLost][defenderLost] / total));
        return Collections.unmodifiableList(outcomes);
    }

    private static final List<List<Outcome>> OUTCOME_TABLE = new ArrayList<>();
    static {
        for (int a = 1; a <= 3; a++) for (int d = 1; d <= 3; d++) OUTCOME_TABLE.add(enumerateOutcomes(a, d));
    }

    private static List<Outcome> table(int attackerDice, int defenderDice) {
        return OUTCOME_TABLE.get((attackerDice - 1) * 3 + defenderDice - 1);
    }

    /** Probabilities of each loss split for the next roll, defender losses first. */
    public static List<Outcome> rollOutcomes(int attackers, int defenders, boolean capital) {
        if (!canAttack(attackers, defenders)) return Collections.emptyList();
        return table(attackerDiceCount(attackers), defenderDiceCount(defenders, capital));
    }

    public static BattleOdds battleOdds(int attackers, int defenders, boolean capital) {
        return battleOdds(attackers, defenders, capital, () -> false);
    }

    /**
     * Exact odds via dynamic programming over (attackers, defenders) states.
     * A single roll can cost the attacker up to 3 armies (capital defense), so a
     * ring of four attacker rows is enough and memory stays O(defenders).
     * Returns null when cancelled.
     */
    public static BattleOdds battleOdds(int attackers, int defenders, boolean capital, BooleanSupplier cancelled) {
        if (defenders <= 0) return new BattleOdds(1, attackers, 0);
        if (attackers <= 1) return new BattleOdds(0, attackers, defenders);

        int size = defenders + 1;
        double[][] win = new double[4][size], atk = new double[4][size], def = new double[4][size];
        for (int a = 1; a <= attackers; a++) {
            if (cancelled.getAsBoolean()) return null;
            int row = a % 4;
            for (int d = 0; d <= defenders; d++) {
                if (d == 0) {
                    win[row][d] = 1;
                    atk[row][d] = a;
                    def[row][d] = 0;
                    continue;
                }
                if (a == 1) {
                    win[row][d] = 0;
                    atk[row][d] = 1;
                    def[row][d] = d;
                    continue;
                }
                double w = 0, x = 0, y = 0;
                for (Outcome outcome : table(attackerDiceCount(a), defenderDiceCount(d, capital))) {
                    int source = (a - outcome.attackerLost) % 4, nextD = d - outcome.defenderLost;
                    w += outcome.probability * win[source][nextD];
                    x += outcome.probability * atk[source][nextD];
                    y += outcome.probability * def[source][nextD];
                }
                win[row][d] = w;
                atk[row][d] = x;
                def[row][d] = y;
            }
        }
        int last = attackers % 4;
        return new BattleOdds(win[last][defenders], atk[last][defenders], def[last][defenders]);
    }
}
