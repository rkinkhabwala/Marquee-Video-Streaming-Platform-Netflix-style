package com.marquee.api.recsys;

import static com.marquee.api.recsys.EngagementType.COMPLETED;
import static com.marquee.api.recsys.EngagementType.PROGRESS_25;
import static com.marquee.api.recsys.EngagementType.PROGRESS_50;
import static com.marquee.api.recsys.EngagementType.PROGRESS_75;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WatchMilestonesTest {
    @Test
    void reportsEachMilestoneOnceAsPlaybackAdvances() {
        var start = WatchMilestones.advance(0, 10, 100, false);
        assertThat(start.reached()).isEmpty();

        var quarter = WatchMilestones.advance(start.milestone(), 26, 100, false);
        assertThat(quarter.reached()).containsExactly(PROGRESS_25);

        var again = WatchMilestones.advance(quarter.milestone(), 30, 100, false);
        assertThat(again.reached()).isEmpty();

        var half = WatchMilestones.advance(again.milestone(), 55, 100, false);
        assertThat(half.reached()).containsExactly(PROGRESS_50);
        assertThat(half.milestone()).isEqualTo(50);
    }

    @Test
    void jumpingAheadReportsEveryMilestonePassedAndCompletion() {
        assertThat(WatchMilestones.advance(0, 96, 100, true).reached())
                .containsExactly(PROGRESS_25, PROGRESS_50, PROGRESS_75, COMPLETED);
        assertThat(WatchMilestones.advance(50, 80, 100, false).reached()).containsExactly(PROGRESS_75);
    }

    @Test
    void seekingBackDoesNotRepeatMilestones() {
        var result = WatchMilestones.advance(75, 20, 100, false);
        assertThat(result.reached()).isEmpty();
        assertThat(result.milestone()).isEqualTo(75);
    }

    @Test
    void restartingAFinishedTitleStartsANewViewing() {
        var restart = WatchMilestones.advance(WatchMilestones.COMPLETE, 5, 100, false);
        assertThat(restart.milestone()).isZero();
        assertThat(WatchMilestones.advance(restart.milestone(), 30, 100, false).reached()).containsExactly(PROGRESS_25);
    }

    @Test
    void unknownDurationReportsNothing() {
        assertThat(WatchMilestones.advance(0, 500, 0, false).reached()).isEmpty();
    }
}
