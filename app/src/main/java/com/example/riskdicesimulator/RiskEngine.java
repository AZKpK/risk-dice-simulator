package com.example.riskdicesimulator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class RiskEngine {

    private final Random random = new Random();

    public static class BattleResult {
        public int attackerArmiesLeft;
        public int defenderArmiesLeft;
        public int attackerWins;
        public int defenderWins;

        public BattleResult(int attackerArmiesLeft, int defenderArmiesLeft, int attackerWins, int defenderWins) {
            this.attackerArmiesLeft = attackerArmiesLeft;
            this.defenderArmiesLeft = defenderArmiesLeft;
            this.attackerWins = attackerWins;
            this.defenderWins = defenderWins;
        }
    }

    // 1. ROLL SINGLE ROUND
    public BattleResult rollOnce(int attackerArmies, int defenderArmies, boolean isCapital) {
        if (attackerArmies < 2 || defenderArmies < 1) {
            return new BattleResult(attackerArmies, defenderArmies, 0, 0);
        }

        int attackerDiceCount = Math.min(3, attackerArmies - 1);

        // Capital allows up to 3 dice; Standard allows up to 2
        int maxDefenderDice = isCapital ? 3 : 2;
        int defenderDiceCount = Math.min(maxDefenderDice, defenderArmies);

        List<Integer> attackerRolls = rollDice(attackerDiceCount);
        List<Integer> defenderRolls = rollDice(defenderDiceCount);

        // How many pairs of dice are compared (the smaller of the two dice counts)
        int comparisons = Math.min(attackerDiceCount, defenderDiceCount);

        for (int i = 0; i < comparisons; i++) {
            if (attackerRolls.get(i) > defenderRolls.get(i)) {
                defenderArmies--;
            } else {
                attackerArmies--; // Defender wins ties
            }
        }

        return new BattleResult(attackerArmies, defenderArmies, 0, 0);
    }

    // 2. FIGHT UNTIL DEATH
    public BattleResult fightToTheDeath(int attackerArmies, int defenderArmies, boolean isCapital) {
        while (attackerArmies > 1 && defenderArmies > 0) {
            BattleResult round = rollOnce(attackerArmies, defenderArmies, isCapital);
            attackerArmies = round.attackerArmiesLeft;
            defenderArmies = round.defenderArmiesLeft;
        }
        return new BattleResult(attackerArmies, defenderArmies, 0, 0);
    }

    // 3. SIMULATE 1,000 BATTLES
    public BattleResult runBatchSimulation(int attackerArmies, int defenderArmies, int totalSimulations, boolean isCapital) {
        int attackerWins = 0;
        int defenderWins = 0;
        int totalAttackerRemaining = 0;
        int totalDefenderRemaining = 0;

        for (int i = 0; i < totalSimulations; i++) {
            BattleResult outcome = fightToTheDeath(attackerArmies, defenderArmies, isCapital);

            if (outcome.attackerArmiesLeft > 1) {
                attackerWins++;
            } else {
                defenderWins++;
            }

            totalAttackerRemaining += outcome.attackerArmiesLeft;
            totalDefenderRemaining += outcome.defenderArmiesLeft;
        }

        return new BattleResult(
                totalAttackerRemaining / totalSimulations,
                totalDefenderRemaining / totalSimulations,
                attackerWins,
                defenderWins
        );
    }

    private List<Integer> rollDice(int count) {
        List<Integer> dice = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            dice.add(random.nextInt(6) + 1);
        }
        dice.sort(Collections.reverseOrder());
        return dice;
    }
}