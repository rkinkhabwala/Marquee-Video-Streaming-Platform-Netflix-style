package com.marquee.api.recsys;

/** Engagement events Marquee reports (spec Phase 3), before translation to recsys event types. */
public enum EngagementType {
    PLAY_START,
    PROGRESS_25,
    PROGRESS_50,
    PROGRESS_75,
    COMPLETED,
    THUMBS_UP,
    THUMBS_DOWN,
    MY_LIST_ADD,
    SEARCH
}
