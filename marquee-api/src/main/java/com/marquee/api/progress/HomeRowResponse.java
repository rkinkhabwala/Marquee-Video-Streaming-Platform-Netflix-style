package com.marquee.api.progress;

import java.util.List;

public record HomeRowResponse(String title, List<TitleCardResponse> items) {
}
