package com.example.riskdicesimulator;

import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

public final class MainActivity extends AppCompatActivity {
    private BattleViewModel model;
    private ArmyInput attackerInput, defenderInput;
    private RiskSwitch capitalSwitch;
    private LinearLayout attackerDice, defenderDice, outcomeList;
    private BattleLogAdapter logAdapter;
    private int renderedRollKey;
    private boolean renderedLogOpen, renderedNextRollOpen, renderedStale, firstRender = true;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        View root = findViewById(R.id.root);
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightStatusBars(false);
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightNavigationBars(false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return windowInsets;
        });
        for (int id : new int[]{R.id.title, R.id.logToggle}) ViewCompat.setAccessibilityHeading(findViewById(id), true);

        model = new ViewModelProvider(this).get(BattleViewModel.class);
        renderedRollKey = model.rollKey();

        attackerInput = new ArmyInput(findViewById(R.id.attackerCard), true, 1,
                value -> model.setArmies(value, model.defenders()));
        defenderInput = new ArmyInput(findViewById(R.id.defenderCard), false, 0,
                value -> model.setArmies(model.attackers(), value));

        capitalSwitch = findViewById(R.id.capitalSwitch);
        capitalSwitch.setOnCheckedChangeListener((button, checked) -> model.setCapital(checked));
        findViewById(R.id.capitalRow).setOnClickListener(v -> capitalSwitch.toggle());

        findViewById(R.id.arena).setBackground(new ArenaBackground(getResources().getDisplayMetrics().density, getColor(R.color.border)));
        attackerDice = findViewById(R.id.attackerDice);
        defenderDice = findViewById(R.id.defenderDice);

        click(R.id.reset, model::reset);
        click(R.id.rollOnce, model::rollOnce);
        click(R.id.rollAll, model::rollAll);
        click(R.id.logToggle, model::toggleLog);
        click(R.id.clearLog, model::clearLog);
        click(R.id.nextRollToggle, model::toggleNextRoll);

        RecyclerView logList = findViewById(R.id.logList);
        logList.setLayoutManager(new LinearLayoutManager(this));
        logList.setItemAnimator(null);
        logAdapter = new BattleLogAdapter(model::toggleExpanded);
        logList.setAdapter(logAdapter);

        BarView meter = findViewById(R.id.meter);
        meter.setColors(getColor(R.color.defender), getColor(R.color.attacker), false);
        outcomeList = findViewById(R.id.outcomeList);

        model.changes().observe(this, ignored -> render());
    }

    /** Buttons commit a pending army draft first, as clicking blurs the input on the web. */
    private void click(int id, Runnable action) {
        findViewById(id).setOnClickListener(v -> {
            commitDrafts();
            action.run();
        });
    }

    private void commitDrafts() {
        attackerInput.commit();
        defenderInput.commit();
    }

    private void render() {
        int attackers = model.attackers(), defenders = model.defenders();
        boolean capital = model.capital(), active = model.active();
        int attackerSlots = RiskEngine.attackerDiceCount(attackers);
        int defenderSlots = RiskEngine.defenderDiceCount(defenders, capital);

        attackerInput.render(attackers, getString(R.string.attacker_hint));
        defenderInput.render(defenders, getString(capital ? R.string.defender_hint_capital : R.string.defender_hint_standard));
        if (capitalSwitch.isChecked() != capital) capitalSwitch.setChecked(capital);

        // Dice arena
        RiskEngine.RollResult roll = model.lastRoll();
        boolean animate = model.rollKey() != renderedRollKey;
        renderedRollKey = model.rollKey();
        int[] attack = roll == null ? new int[0] : roll.attackerDice, defense = roll == null ? new int[0] : roll.defenderDice;
        renderDice(attackerDice, true, attack, defense, attackerSlots, animate);
        renderDice(defenderDice, false, defense, attack, defenderSlots, animate);
        text(R.id.caption).setText(caption(attackers, defenders, attackerSlots, defenderSlots, roll));

        findViewById(R.id.rollOnce).setEnabled(active);
        findViewById(R.id.rollAll).setEnabled(active);

        // Battle log
        List<BattleViewModel.LogEntry> log = model.log();
        boolean logOpen = model.logOpen();
        text(R.id.logCount).setText(String.valueOf(log.size()));
        rotateChevron(R.id.logChevron, logOpen, renderedLogOpen);
        renderedLogOpen = logOpen;
        findViewById(R.id.logToggle).setContentDescription(getString(R.string.battle_log) + ", " + log.size());
        show(R.id.clearLog, logOpen);
        findViewById(R.id.clearLog).setEnabled(!log.isEmpty());
        show(R.id.logEmpty, logOpen && log.isEmpty());
        show(R.id.logList, logOpen && !log.isEmpty());
        if (logOpen) logAdapter.submit(model);

        renderOdds(attackers, defenders, capital, attackerSlots, defenderSlots);
        firstRender = false;
    }

    private void renderDice(LinearLayout row, boolean attacker, int[] own, int[] other, int slots, boolean animate) {
        int count = Math.max(1, Math.max(own.length, slots));
        int size = getResources().getDimensionPixelSize(R.dimen.die_size);
        while (row.getChildCount() < count) row.addView(new DieView(this), new LinearLayout.LayoutParams(size, size));
        while (row.getChildCount() > count) row.removeViewAt(row.getChildCount() - 1);
        for (int i = 0; i < count; i++) {
            DieView die = (DieView) row.getChildAt(i);
            int value = i < own.length ? own[i] : 0;
            die.bind(attacker, value, own.length == 0 ? DieView.State.IDLE : dieState(i, own, other, attacker));
            if (animate && value > 0) die.tumble(i * 60L);
        }
    }

    private static DieView.State dieState(int index, int[] own, int[] other, boolean attacker) {
        if (index >= other.length) return DieView.State.UNUSED;
        boolean attackerWins = attacker ? own[index] > other[index] : other[index] > own[index];
        return attacker == attackerWins ? DieView.State.WON : DieView.State.LOST;
    }

    private String caption(int attackers, int defenders, int attackerSlots, int defenderSlots, RiskEngine.RollResult roll) {
        if (defenders == 0) return getString(R.string.caption_captured);
        if (attackers < 2) return getString(R.string.caption_too_few);
        if (roll == null) return getString(R.string.caption_ready, attackerSlots, defenderSlots);
        if (roll.attackerLost == 0) return getString(R.string.defender_loses, roll.defenderLost);
        if (roll.defenderLost == 0) return getString(R.string.attacker_loses, roll.attackerLost);
        return getString(R.string.both_lose, roll.attackerLost, roll.defenderLost);
    }

    private void renderOdds(int attackers, int defenders, boolean capital, int attackerSlots, int defenderSlots) {
        RiskEngine.BattleOdds odds = model.odds();
        View section = findViewById(R.id.oddsSection);
        boolean stale = model.oddsStale();
        float alpha = stale ? .6f : 1f;
        if (firstRender) section.setAlpha(alpha);
        else if (stale != renderedStale) section.animate().alpha(alpha).setDuration(150).start();
        renderedStale = stale;

        text(R.id.attackerWin).setText(percent(odds.attackerWin));
        text(R.id.defenderHold).setText(percent(1 - odds.attackerWin));
        BarView meter = findViewById(R.id.meter);
        meter.setFraction((float) odds.attackerWin, firstRender ? 0 : 500);
        ViewCompat.setStateDescription(meter, Math.round(odds.attackerWin * 100) + "%");
        text(R.id.expectedAttackers).setText(String.format(Locale.US, "%.1f", odds.expectedAttackers));
        text(R.id.expectedDefenders).setText(String.format(Locale.US, "%.1f", odds.expectedDefenders));

        List<RiskEngine.Outcome> outcomes = RiskEngine.rollOutcomes(attackers, defenders, capital);
        show(R.id.nextRoll, !outcomes.isEmpty());
        boolean open = model.nextRollOpen();
        text(R.id.nextRollLabel).setText(getString(defenderSlots == 1 ? R.string.next_roll_one : R.string.next_roll_many,
                attackerSlots, defenderSlots));
        rotateChevron(R.id.nextRollChevron, open, renderedNextRollOpen);
        renderedNextRollOpen = open;
        show(R.id.outcomeList, open);
        if (open) renderOutcomes(outcomes);

        text(R.id.totalRolls).setText(String.valueOf(model.totalRolls()));
        text(R.id.totalAttackerLost).setText(String.valueOf(model.totalAttackerLost()));
        text(R.id.totalDefenderLost).setText(String.valueOf(model.totalDefenderLost()));
    }

    private void renderOutcomes(List<RiskEngine.Outcome> outcomes) {
        LayoutInflater inflater = LayoutInflater.from(this);
        while (outcomeList.getChildCount() < outcomes.size()) inflater.inflate(R.layout.item_outcome, outcomeList, true);
        while (outcomeList.getChildCount() > outcomes.size()) outcomeList.removeViewAt(outcomeList.getChildCount() - 1);
        for (int i = 0; i < outcomes.size(); i++) {
            RiskEngine.Outcome outcome = outcomes.get(i);
            View item = outcomeList.getChildAt(i);
            ((TextView) item.findViewById(R.id.outcomeText)).setText(outcomeText(outcome));
            ((TextView) item.findViewById(R.id.outcomePercent)).setText(percent(outcome.probability));
            BarView bar = item.findViewById(R.id.outcomeBar);
            int fill = outcome.attackerLost == 0 ? R.color.attacker : outcome.defenderLost == 0 ? R.color.defender : R.color.muted_foreground;
            bar.setColors(getColor(R.color.secondary), getColor(fill), true);
            bar.setFraction((float) outcome.probability, 0);
        }
    }

    private String outcomeText(RiskEngine.Outcome outcome) {
        if (outcome.attackerLost == 0) return getString(R.string.defender_loses, outcome.defenderLost);
        if (outcome.defenderLost == 0) return getString(R.string.attacker_loses, outcome.attackerLost);
        if (outcome.attackerLost == outcome.defenderLost) return getString(R.string.each_loses, outcome.attackerLost);
        return getString(R.string.mixed_losses, outcome.attackerLost, outcome.defenderLost);
    }

    static String percent(double value) {
        double pct = value * 100;
        if (pct > 0 && pct < .1) return "<0.1%";
        if (pct < 100 && pct > 99.9) return ">99.9%";
        return String.format(Locale.US, "%.1f%%", pct);
    }

    /** transition-transform: 150ms cubic-bezier(.4, 0, .2, 1). */
    private void rotateChevron(int id, boolean open, boolean wasOpen) {
        View chevron = findViewById(id);
        float target = open ? 180 : 0;
        if (firstRender || open == wasOpen) {
            chevron.animate().cancel();
            chevron.setRotation(target);
        } else {
            chevron.animate().rotation(target).setDuration(150)
                    .setInterpolator(new PathInterpolator(.4f, 0, .2f, 1)).start();
        }
    }

    private TextView text(int id) { return findViewById(id); }
    private void show(int id, boolean visible) { findViewById(id).setVisibility(visible ? View.VISIBLE : View.GONE); }

    /** components/risk/army-input.tsx: a free-text draft committed on blur or Enter, plus steppers. */
    private final class ArmyInput {
        private final EditText field;
        private final TextView hint;
        private final int min;
        private final IntConsumer onChange;
        private int value = -1;

        ArmyInput(View card, boolean attacker, int min, IntConsumer onChange) {
            this.min = min;
            this.onChange = onChange;
            card.setBackgroundResource(attacker ? R.drawable.bg_army_attacker : R.drawable.bg_army_defender);
            card.findViewById(R.id.dot).setBackgroundResource(attacker ? R.drawable.dot_attacker : R.drawable.dot_defender);
            String label = getString(attacker ? R.string.attacker : R.string.defender);
            ((TextView) card.findViewById(R.id.label)).setText(label);
            field = card.findViewById(R.id.value);
            // Both cards come from one layout; give each field a unique id. The model owns the value.
            field.setId(attacker ? R.id.attackerValue : R.id.defenderValue);
            field.setSaveEnabled(false);
            field.setTextColor(getColor(attacker ? R.color.attacker : R.color.defender));
            field.setContentDescription(label);
            hint = card.findViewById(R.id.hint);
            field.setOnFocusChangeListener((v, focused) -> { if (!focused) commit(); });
            field.setOnEditorActionListener((v, action, event) -> {
                if (action == EditorInfo.IME_ACTION_DONE
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    commit();
                    field.clearFocus();
                    ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(field.getWindowToken(), 0);
                    return true;
                }
                return false;
            });
            step(card, R.id.minusTen, -10, R.string.remove_ten, label);
            step(card, R.id.minusOne, -1, R.string.remove_one, label);
            step(card, R.id.plusOne, 1, R.string.add_one, label);
            step(card, R.id.plusTen, 10, R.string.add_ten, label);
        }

        private void step(View card, int id, int delta, int description, String label) {
            View button = card.findViewById(id);
            button.setContentDescription(getString(description, label));
            button.setOnClickListener(v -> {
                commitDrafts();
                onChange.accept(clamp((long) value + delta));
            });
        }

        private int clamp(long next) { return (int) Math.min(RiskEngine.MAX_ARMIES, Math.max(min, next)); }

        void commit() {
            if (value < 0) return;
            String raw = field.getText() == null ? "" : field.getText().toString().replaceAll("\\D", "");
            int next;
            if (raw.isEmpty()) next = value;
            else {
                // Very long digit strings saturate rather than overflow.
                long parsed = raw.length() > 12 ? Long.MAX_VALUE : Long.parseLong(raw);
                next = clamp(parsed);
            }
            if (!field.getText().toString().equals(String.valueOf(next))) field.setText(String.valueOf(next));
            if (next != value) onChange.accept(next);
        }

        void render(int newValue, String hintText) {
            if (newValue != value) {
                value = newValue;
                field.setText(String.valueOf(newValue));
                if (field.hasFocus()) field.selectAll();
            }
            hint.setText(hintText);
        }
    }
}
