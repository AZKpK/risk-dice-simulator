package com.example.riskdicesimulator;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * Battle log rows, newest first. An expanded "Roll all" entry is followed by one row per
 * round; those rows continue the entry's background so it reads as a single list item.
 */
final class BattleLogAdapter extends RecyclerView.Adapter<BattleLogAdapter.Holder> {
    private static final int ENTRY = 0, ROUND = 1;

    private static final class Row {
        final BattleViewModel.LogEntry entry;
        final int entryIndex, roundIndex;
        final boolean expanded;

        Row(BattleViewModel.LogEntry entry, int entryIndex, int roundIndex, boolean expanded) {
            this.entry = entry;
            this.entryIndex = entryIndex;
            this.roundIndex = roundIndex;
            this.expanded = expanded;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final LongConsumer toggle;
    private int entryCount;

    BattleLogAdapter(LongConsumer toggle) { this.toggle = toggle; }

    void submit(BattleViewModel model) {
        rows.clear();
        List<BattleViewModel.LogEntry> log = model.log();
        entryCount = log.size();
        for (int i = 0; i < log.size(); i++) {
            BattleViewModel.LogEntry entry = log.get(i);
            boolean expanded = entry.kind == BattleViewModel.Kind.BLITZ && model.isExpanded(entry.id) && entry.rounds != null;
            rows.add(new Row(entry, i, -1, expanded));
            if (expanded) for (int r = 0; r < entry.rounds.size(); r++) rows.add(new Row(entry, i, r, true));
        }
        notifyDataSetChanged();
    }

    @Override public int getItemCount() { return rows.size(); }

    @Override public int getItemViewType(int position) { return rows.get(position).roundIndex < 0 ? ENTRY : ROUND; }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(type == ENTRY ? R.layout.item_log_entry : R.layout.item_log_round, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        Row row = rows.get(position);
        if (row.roundIndex < 0) bindEntry(holder, row, position);
        else bindRound(holder, row);
    }

    private void bindEntry(Holder holder, Row row, int position) {
        Context context = holder.itemView.getContext();
        BattleViewModel.LogEntry entry = row.entry;
        float dp = context.getResources().getDisplayMetrics().density;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
        params.topMargin = position == 0 ? 0 : Math.round(4 * dp);
        holder.itemView.setLayoutParams(params);
        holder.itemView.setPadding(holder.itemView.getPaddingLeft(), Math.round(8 * dp),
                holder.itemView.getPaddingRight(), row.expanded ? 0 : Math.round(8 * dp));
        holder.itemView.setBackground(stripe(context, row.entryIndex, row.expanded ? Corners.TOP : Corners.ALL));

        holder.text(R.id.number).setText(context.getString(R.string.log_number, entryCount - row.entryIndex));
        boolean setup = entry.kind == BattleViewModel.Kind.SETUP, blitz = entry.kind == BattleViewModel.Kind.BLITZ;
        holder.show(R.id.setupText, setup);
        holder.show(R.id.dice, entry.kind == BattleViewModel.Kind.ROLL);
        holder.show(R.id.blitzToggle, blitz);
        holder.show(R.id.losses, !setup);
        if (setup) {
            holder.text(R.id.setupText).setText(context.getString(R.string.log_new_battle, entry.attackersAfter, entry.defendersAfter));
            holder.show(R.id.resultLine, false);
            return;
        }
        if (blitz) {
            int rolls = entry.rounds == null ? 0 : entry.rounds.size();
            holder.text(R.id.blitzLabel).setText(context.getString(
                    rolls == 1 ? R.string.log_roll_all_one : R.string.log_roll_all_many, rolls));
            holder.itemView.findViewById(R.id.blitzChevron).setRotation(row.expanded ? 180 : 0);
            View button = holder.itemView.findViewById(R.id.blitzToggle);
            button.setContentDescription(holder.text(R.id.blitzLabel).getText() + ", "
                    + context.getString(row.expanded ? R.string.log_hide_rolls : R.string.log_show_rolls));
            button.setOnClickListener(v -> toggle.accept(entry.id));
        } else {
            ((DiceStackView) holder.itemView.findViewById(R.id.dice)).bind(entry.attackerDice, entry.defenderDice);
        }
        String result = entry.defendersAfter == 0 ? context.getString(R.string.territory_captured)
                : entry.attackersAfter < 2 ? context.getString(R.string.attack_repelled) : null;
        holder.show(R.id.resultLine, result != null || entry.capital);
        holder.show(R.id.result, result != null);
        holder.show(R.id.capitalTag, entry.capital);
        if (result != null) {
            holder.text(R.id.result).setText(result);
            holder.text(R.id.result).setTextColor(context.getColor(entry.defendersAfter == 0 ? R.color.attacker : R.color.defender));
        }
        bindLosses(holder, entry.attackerLost, entry.defenderLost, entry.attackersAfter, entry.defendersAfter);
    }

    private void bindRound(Holder holder, Row row) {
        Context context = holder.itemView.getContext();
        float dp = context.getResources().getDisplayMetrics().density;
        BattleViewModel.Round round = row.entry.rounds.get(row.roundIndex);
        boolean first = row.roundIndex == 0, last = row.roundIndex == row.entry.rounds.size() - 1;
        holder.show(R.id.divider, first);
        View line = holder.itemView.findViewById(R.id.row);
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) line.getLayoutParams();
        params.topMargin = Math.round((first ? 8 : 4) * dp);
        line.setLayoutParams(params);
        holder.itemView.setPadding(holder.itemView.getPaddingLeft(), 0, holder.itemView.getPaddingRight(),
                last ? Math.round(8 * dp) : 0);
        holder.itemView.setBackground(stripe(context, row.entryIndex, last ? Corners.BOTTOM : Corners.NONE));
        // even:bg-background/40 (CSS nth-child counts from 1).
        if (row.roundIndex % 2 == 1) {
            GradientDrawable background = new GradientDrawable();
            background.setColor(context.getColor(R.color.background_40));
            background.setCornerRadius(10 * dp);
            line.setBackground(background);
        } else {
            line.setBackground(null);
        }
        holder.text(R.id.number).setText(context.getString(R.string.round_number, row.roundIndex + 1));
        ((DiceStackView) holder.itemView.findViewById(R.id.dice)).bind(round.roll.attackerDice, round.roll.defenderDice);
        bindLosses(holder, round.roll.attackerLost, round.roll.defenderLost, round.attackersAfter, round.defendersAfter);
    }

    private static void bindLosses(Holder holder, int attackerLost, int defenderLost, int attackers, int defenders) {
        Context context = holder.itemView.getContext();
        String attacker = "-" + attackerLost, defender = "-" + defenderLost;
        SpannableStringBuilder text = new SpannableStringBuilder(context.getString(R.string.losses, attackerLost, defenderLost));
        int split = attacker.length();
        text.setSpan(new ForegroundColorSpan(context.getColor(R.color.attacker)), 0, split, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(context.getColor(R.color.muted_foreground)), split, text.length() - defender.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(context.getColor(R.color.defender)), text.length() - defender.length(), text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        holder.text(R.id.lossText).setText(text);
        holder.text(R.id.afterText).setText(context.getString(R.string.armies_after, attackers, defenders));
    }

    private enum Corners { ALL, TOP, BOTTOM, NONE }

    /** odd:bg-muted/40 rounded-xl on the list item (CSS nth-child counts from 1). */
    private static GradientDrawable stripe(Context context, int entryIndex, Corners corners) {
        if (entryIndex % 2 == 1) return null;
        float r = 14 * context.getResources().getDisplayMetrics().density;
        float top = corners == Corners.ALL || corners == Corners.TOP ? r : 0;
        float bottom = corners == Corners.ALL || corners == Corners.BOTTOM ? r : 0;
        GradientDrawable background = new GradientDrawable();
        background.setColor(context.getColor(R.color.muted_40));
        background.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
        return background;
    }

    static final class Holder extends RecyclerView.ViewHolder {
        Holder(View view) { super(view); }
        TextView text(int id) { return itemView.findViewById(id); }
        void show(int id, boolean visible) { itemView.findViewById(id).setVisibility(visible ? View.VISIBLE : View.GONE); }
    }
}
