package com.marquee.api.recsys;

import java.util.ArrayList;
import java.util.List;

/**
 * Progress milestones (25/50/75/100 %) reached by a viewing. Each is reported once per viewing:
 * seeking back does not repeat them, and a jump forward reports every milestone it passed.
 */
public final class WatchMilestones {
    public static final int COMPLETE = 100;
    /** Restarting a finished title from (near) the beginning starts a new viewing. */
    private static final double REWATCH_RATIO = 0.10;

    private WatchMilestones() {
    }

    public record Result(int milestone, List<EngagementType> reached) {
    }

    public static Result advance(int previousMilestone, int positionSeconds, int durationSeconds, boolean completed) {
        if (durationSeconds <= 0) {
            return new Result(previousMilestone, List.of());
        }
        double ratio = (double) positionSeconds / durationSeconds;
        int from = previousMilestone;
        if (previousMilestone == COMPLETE && !completed && ratio < REWATCH_RATIO) {
            from = 0;
        }
        int current = completed ? COMPLETE : ratio >= 0.75 ? 75 : ratio >= 0.5 ? 50 : ratio >= 0.25 ? 25 : 0;
        if (current <= from) {
            return new Result(from, List.of());
        }
        List<EngagementType> reached = new ArrayList<>();
        if (from < 25 && current >= 25) {
            reached.add(EngagementType.PROGRESS_25);
        }
        if (from < 50 && current >= 50) {
            reached.add(EngagementType.PROGRESS_50);
        }
        if (from < 75 && current >= 75) {
            reached.add(EngagementType.PROGRESS_75);
        }
        if (current == COMPLETE) {
            reached.add(EngagementType.COMPLETED);
        }
        return new Result(current, reached);
    }
}
