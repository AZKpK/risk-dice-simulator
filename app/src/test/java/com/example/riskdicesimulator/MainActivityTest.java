package com.example.riskdicesimulator;

import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public class MainActivityTest {
    private static BattleViewModel settle(MainActivity activity) throws Exception {
        BattleViewModel model = new ViewModelProvider(activity).get(BattleViewModel.class);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (!model.oddsStale()) return model;
            Thread.sleep(5);
        }
        throw new AssertionError("Odds were not computed");
    }

    private static String text(MainActivity activity, int id) {
        return ((TextView) activity.findViewById(id)).getText().toString();
    }

    @Test public void initialScreenMatchesPreview() throws Exception {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            settle(activity);
            assertEquals("10", text(activity, R.id.attackerValue));
            assertEquals("5", text(activity, R.id.defenderValue));
            assertEquals("Ready: 3 vs 2 dice", text(activity, R.id.caption));
            assertEquals("87.3%", text(activity, R.id.attackerWin));
            assertEquals("12.7%", text(activity, R.id.defenderHold));
            assertEquals("Next roll · 3 vs 2 dice", text(activity, R.id.nextRollLabel));
            assertEquals("0", text(activity, R.id.logCount));
            assertEquals(3, ((android.view.ViewGroup) activity.findViewById(R.id.attackerDice)).getChildCount());
            assertEquals(2, ((android.view.ViewGroup) activity.findViewById(R.id.defenderDice)).getChildCount());
            assertEquals(View.GONE, activity.findViewById(R.id.logList).getVisibility());
        }
    }

    @Test public void rollOnceUpdatesArmiesTotalsAndLog() throws Exception {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            activity.findViewById(R.id.rollOnce).performClick();
            BattleViewModel model = settle(activity);
            assertEquals(1, model.totalRolls());
            assertEquals(10, model.attackers() + model.totalAttackerLost());
            assertEquals(5, model.defenders() + model.totalDefenderLost());
            assertEquals(2, model.totalAttackerLost() + model.totalDefenderLost());
            assertEquals(String.valueOf(model.attackers()), text(activity, R.id.attackerValue));
            assertEquals("1", text(activity, R.id.logCount));
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
            assertEquals(1f, activity.findViewById(R.id.oddsSection).getAlpha(), 0); // Not left dimmed.
            activity.findViewById(R.id.logToggle).performClick();
            settle(activity);
            RecyclerView list = activity.findViewById(R.id.logList);
            assertEquals(View.VISIBLE, list.getVisibility());
            assertEquals(1, list.getAdapter().getItemCount());
        }
    }

    @Test public void rollAllFinishesBattleAndExpandsRounds() throws Exception {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            activity.findViewById(R.id.rollAll).performClick();
            BattleViewModel model = settle(activity);
            assertFalse(model.active());
            assertFalse(activity.findViewById(R.id.rollOnce).isEnabled());
            assertEquals(.5f, activity.findViewById(R.id.rollAll).getAlpha(), 0);
            assertTrue(model.defenders() == 0 || model.attackers() == 1);
            assertEquals(View.GONE, activity.findViewById(R.id.nextRoll).getVisibility());
            BattleViewModel.LogEntry entry = model.log().get(0);
            assertEquals(model.totalRolls(), entry.rounds.size());
            activity.findViewById(R.id.logToggle).performClick();
            model.toggleExpanded(entry.id);
            settle(activity);
            RecyclerView list = activity.findViewById(R.id.logList);
            assertEquals(1 + entry.rounds.size(), list.getAdapter().getItemCount());
        }
    }

    @Test public void typedArmiesAreClampedOnCommit() throws Exception {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            EditText attacker = activity.findViewById(R.id.attackerValue);
            attacker.setText("2147483648999");
            attacker.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
            BattleViewModel model = settle(activity);
            assertEquals(RiskEngine.MAX_ARMIES, model.attackers());
            assertEquals("2000", attacker.getText().toString());
            EditText defender = activity.findViewById(R.id.defenderValue);
            defender.setText("");
            activity.findViewById(R.id.logToggle).performClick(); // Buttons commit; an empty draft keeps the value.
            assertEquals("5", defender.getText().toString());
        }
    }

    @Test public void steppersAndCapitalUpdateOdds() throws Exception {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            View defenderCard = activity.findViewById(R.id.defenderCard);
            defenderCard.findViewById(R.id.plusTen).performClick();
            defenderCard.findViewById(R.id.minusOne).performClick();
            activity.findViewById(R.id.capitalRow).performClick();
            BattleViewModel model = settle(activity);
            assertEquals(14, model.defenders());
            assertTrue(model.capital());
            assertEquals("Defends with up to 3", ((TextView) defenderCard.findViewById(R.id.hint)).getText().toString());
            assertEquals(MainActivity.percent(RiskEngine.battleOdds(10, 14, true).attackerWin), text(activity, R.id.attackerWin));
            assertEquals(3, ((android.view.ViewGroup) activity.findViewById(R.id.defenderDice)).getChildCount());
        }
    }

    @Test public void resetRestoresStartAndLogsNewBattle() throws Exception {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            activity.findViewById(R.id.rollOnce).performClick();
            activity.findViewById(R.id.reset).performClick();
            BattleViewModel model = settle(activity);
            assertEquals(10, model.attackers());
            assertEquals(5, model.defenders());
            assertNull(model.lastRoll());
            assertEquals(0, model.totalRolls());
            assertEquals(BattleViewModel.Kind.SETUP, model.log().get(0).kind);
            assertEquals("2", text(activity, R.id.logCount));
        }
    }

    @Test public void rotationKeepsBattle() throws Exception {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            activity.findViewById(R.id.rollOnce).performClick();
            BattleViewModel before = settle(activity);
            int attackers = before.attackers();
            controller.recreate();
            MainActivity recreated = controller.get();
            BattleViewModel after = settle(recreated);
            assertSame(before, after);
            assertEquals(String.valueOf(attackers), text(recreated, R.id.attackerValue));
            assertEquals("1", text(recreated, R.id.totalRolls));
        }
    }
}
