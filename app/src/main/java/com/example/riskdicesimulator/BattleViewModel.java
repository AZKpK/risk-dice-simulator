package com.example.riskdicesimulator;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** Battle state for one screen; survives rotation. Odds are computed off the main thread. */
public final class BattleViewModel extends ViewModel {
    public enum Kind { ROLL, BLITZ, SETUP }

    public static final class Round {
        public final RiskEngine.RollResult roll;
        public final int attackersAfter, defendersAfter;

        Round(RiskEngine.RollResult roll, int attackersAfter, int defendersAfter) {
            this.roll = roll;
            this.attackersAfter = attackersAfter;
            this.defendersAfter = defendersAfter;
        }
    }

    public static final class LogEntry {
        public final long id;
        public final Kind kind;
        public final int[] attackerDice, defenderDice;
        public final int attackerLost, defenderLost, attackersAfter, defendersAfter;
        public final List<Round> rounds;
        public final boolean capital;

        LogEntry(long id, Kind kind, RiskEngine.RollResult last, int attackerLost, int defenderLost,
                 int attackersAfter, int defendersAfter, List<Round> rounds, boolean capital) {
            this.id = id;
            this.kind = kind;
            this.attackerDice = last == null ? new int[0] : last.attackerDice;
            this.defenderDice = last == null ? new int[0] : last.defenderDice;
            this.attackerLost = attackerLost;
            this.defenderLost = defenderLost;
            this.attackersAfter = attackersAfter;
            this.defendersAfter = defendersAfter;
            this.rounds = rounds;
            this.capital = capital;
        }
    }

    private static final int MAX_LOG = 500;

    private final MutableLiveData<Long> changes = new MutableLiveData<>(0L);
    private final ExecutorService oddsWorker = Executors.newSingleThreadExecutor();
    private final AtomicLong oddsGeneration = new AtomicLong();
    private final Handler main = new Handler(Looper.getMainLooper());

    private int attackers = 10, defenders = 5, startAttackers = 10, startDefenders = 5;
    private boolean capital;
    private RiskEngine.RollResult lastRoll;
    private int rollKey;
    private List<LogEntry> log = Collections.emptyList();
    private long nextId;
    private int totalRolls, totalAttackerLost, totalDefenderLost;
    private RiskEngine.BattleOdds odds;
    private boolean oddsStale;
    private boolean logOpen, nextRollOpen;
    private final Set<Long> expanded = new HashSet<>();

    public BattleViewModel() {
        // The first frame shows final odds, like the web preview's synchronous first render.
        odds = RiskEngine.battleOdds(attackers, defenders, capital);
    }

    public LiveData<Long> changes() { return changes; }
    public int attackers() { return attackers; }
    public int defenders() { return defenders; }
    public boolean capital() { return capital; }
    public RiskEngine.RollResult lastRoll() { return lastRoll; }
    public int rollKey() { return rollKey; }
    public List<LogEntry> log() { return log; }
    public int totalRolls() { return totalRolls; }
    public int totalAttackerLost() { return totalAttackerLost; }
    public int totalDefenderLost() { return totalDefenderLost; }
    public RiskEngine.BattleOdds odds() { return odds; }
    public boolean oddsStale() { return oddsStale; }
    public boolean active() { return RiskEngine.canAttack(attackers, defenders); }
    public boolean logOpen() { return logOpen; }
    public boolean nextRollOpen() { return nextRollOpen; }
    public boolean isExpanded(long id) { return expanded.contains(id); }

    private void changed() { changes.setValue(changes.getValue() + 1); }

    private void requestOdds() {
        long generation = oddsGeneration.incrementAndGet();
        int a = attackers, d = defenders;
        boolean c = capital;
        oddsStale = true;
        oddsWorker.execute(() -> {
            RiskEngine.BattleOdds result = RiskEngine.battleOdds(a, d, c, () -> oddsGeneration.get() != generation);
            if (result == null) return;
            main.post(() -> {
                if (oddsGeneration.get() != generation) return;
                odds = result;
                oddsStale = false;
                changed();
            });
        });
    }

    public void setCapital(boolean value) {
        if (capital == value) return;
        capital = value;
        closeNextRollIfGone();
        requestOdds();
        changed();
    }

    public void setArmies(int nextAttackers, int nextDefenders) {
        attackers = nextAttackers;
        defenders = nextDefenders;
        startAttackers = nextAttackers;
        startDefenders = nextDefenders;
        lastRoll = null;
        totalRolls = totalAttackerLost = totalDefenderLost = 0;
        closeNextRollIfGone();
        requestOdds();
        changed();
    }

    public void rollOnce() {
        if (!active()) return;
        RiskEngine.RollResult result = RiskEngine.rollOnce(attackers, defenders, capital);
        attackers -= result.attackerLost;
        defenders -= result.defenderLost;
        lastRoll = result;
        rollKey++;
        totalRolls++;
        totalAttackerLost += result.attackerLost;
        totalDefenderLost += result.defenderLost;
        addLog(Kind.ROLL, result, result.attackerLost, result.defenderLost, null);
        closeNextRollIfGone();
        requestOdds();
        changed();
    }

    public void rollAll() {
        if (!active()) return;
        int a = attackers, d = defenders;
        List<Round> rounds = new ArrayList<>();
        while (RiskEngine.canAttack(a, d)) {
            RiskEngine.RollResult result = RiskEngine.rollOnce(a, d, capital);
            a -= result.attackerLost;
            d -= result.defenderLost;
            rounds.add(new Round(result, a, d));
        }
        RiskEngine.RollResult last = rounds.get(rounds.size() - 1).roll;
        int attackerLost = attackers - a, defenderLost = defenders - d;
        attackers = a;
        defenders = d;
        lastRoll = last;
        rollKey++;
        totalRolls += rounds.size();
        totalAttackerLost += attackerLost;
        totalDefenderLost += defenderLost;
        addLog(Kind.BLITZ, last, attackerLost, defenderLost, Collections.unmodifiableList(rounds));
        closeNextRollIfGone();
        requestOdds();
        changed();
    }

    public void reset() {
        nextId++;
        prepend(new LogEntry(nextId, Kind.SETUP, null, 0, 0, startAttackers, startDefenders, null, false));
        setArmies(startAttackers, startDefenders);
    }

    public void clearLog() {
        log = Collections.emptyList();
        expanded.clear();
        changed();
    }

    public void toggleLog() { logOpen = !logOpen; changed(); }
    public void toggleNextRoll() { nextRollOpen = !nextRollOpen; changed(); }

    public void toggleExpanded(long id) {
        if (!expanded.remove(id)) expanded.add(id);
        changed();
    }

    private void addLog(Kind kind, RiskEngine.RollResult last, int attackerLost, int defenderLost, List<Round> rounds) {
        nextId++;
        prepend(new LogEntry(nextId, kind, last, attackerLost, defenderLost, attackers, defenders, rounds, capital));
    }

    private void prepend(LogEntry entry) {
        List<LogEntry> next = new ArrayList<>(Math.min(log.size() + 1, MAX_LOG));
        next.add(entry);
        for (int i = 0; i < log.size() && next.size() < MAX_LOG; i++) next.add(log.get(i));
        for (int i = MAX_LOG - 1; i < log.size(); i++) expanded.remove(log.get(i).id);
        log = Collections.unmodifiableList(next);
    }

    // The web <details> element unmounts when there is no next roll, so it reopens closed.
    private void closeNextRollIfGone() {
        if (RiskEngine.rollOutcomes(attackers, defenders, capital).isEmpty()) nextRollOpen = false;
    }

    @Override protected void onCleared() {
        oddsGeneration.incrementAndGet();
        oddsWorker.shutdownNow();
    }
}
