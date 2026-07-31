package com.example.riskdicesimulator;

import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText etAttackerArmies, etDefenderArmies;
    private CheckBox cbCapitalDefense;
    private Button btnRollOnce, btnFightToDeath, btnSimulate1000;
    private TextView tvResults;
    private RiskEngine riskEngine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        riskEngine = new RiskEngine();

        etAttackerArmies = findViewById(R.id.etAttackerArmies);
        etDefenderArmies = findViewById(R.id.etDefenderArmies);
        cbCapitalDefense = findViewById(R.id.cbCapitalDefense);
        btnRollOnce = findViewById(R.id.btnRollOnce);
        btnFightToDeath = findViewById(R.id.btnFightToDeath);
        btnSimulate1000 = findViewById(R.id.btnSimulate1000);
        tvResults = findViewById(R.id.tvResults);

        // --- BUTTON 1: Single Round ---
        btnRollOnce.setOnClickListener(v -> {
            int[] armies = getArmyCounts();
            if (armies == null) return;

            boolean isCapital = cbCapitalDefense.isChecked();
            RiskEngine.BattleResult result = riskEngine.rollOnce(armies[0], armies[1], isCapital);

            etAttackerArmies.setText(String.valueOf(result.attackerArmiesLeft));
            etDefenderArmies.setText(String.valueOf(result.defenderArmiesLeft));

            tvResults.setText(String.format("Single Round Outcome:\nAttacker: %d | Defender: %d",
                    result.attackerArmiesLeft, result.defenderArmiesLeft));
        });

        // --- BUTTON 2: Fight To The Death ---
        btnFightToDeath.setOnClickListener(v -> {
            int[] armies = getArmyCounts();
            if (armies == null) return;

            boolean isCapital = cbCapitalDefense.isChecked();
            RiskEngine.BattleResult result = riskEngine.fightToTheDeath(armies[0], armies[1], isCapital);

            String winner = (result.attackerArmiesLeft > 1) ? "Attacker" : "Defender";
            tvResults.setText(String.format("Battle Over!\nWinner: %s\nAttacker Remaining: %d\nDefender Remaining: %d",
                    winner, result.attackerArmiesLeft, result.defenderArmiesLeft));
        });

        // --- BUTTON 3: Simulate 1,000 Battles ---
        btnSimulate1000.setOnClickListener(v -> {
            int[] armies = getArmyCounts();
            if (armies == null) return;

            boolean isCapital = cbCapitalDefense.isChecked();
            int totalSims = 1000;
            RiskEngine.BattleResult stats = riskEngine.runBatchSimulation(armies[0], armies[1], totalSims, isCapital);

            double attackerWinPct = ((double) stats.attackerWins / totalSims) * 100;
            double defenderWinPct = ((double) stats.defenderWins / totalSims) * 100;

            String modeText = isCapital ? "Capital Defense (3 Dice)" : "Standard Defense (2 Dice)";
            String output = String.format("Mode: %s\nResults from %,d Battles:\n\n" +
                            "Attacker Wins: %d (%.1f%%)\n" +
                            "Defender Wins: %d (%.1f%%)\n\n" +
                            "Avg. Attacker Armies Left: %d\n" +
                            "Avg. Defender Armies Left: %d",
                    modeText, totalSims, stats.attackerWins, attackerWinPct, stats.defenderWins, defenderWinPct,
                    stats.attackerArmiesLeft, stats.defenderArmiesLeft);

            tvResults.setText(output);
        });
    }

    private int[] getArmyCounts() {
        String attackerText = etAttackerArmies.getText().toString().trim();
        String defenderText = etDefenderArmies.getText().toString().trim();

        if (attackerText.isEmpty() || defenderText.isEmpty()) {
            Toast.makeText(this, "Please enter numbers for both armies!", Toast.LENGTH_SHORT).show();
            return null;
        }

        int attacker = Integer.parseInt(attackerText);
        int defender = Integer.parseInt(defenderText);

        if (attacker < 2) {
            Toast.makeText(this, "Attacker must start with at least 2 armies!", Toast.LENGTH_SHORT).show();
            return null;
        }

        if (defender < 1) {
            Toast.makeText(this, "Defender must start with at least 1 army!", Toast.LENGTH_SHORT).show();
            return null;
        }

        return new int[]{attacker, defender};
    }
}