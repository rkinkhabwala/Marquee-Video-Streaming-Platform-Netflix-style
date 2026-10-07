package com.marquee.api.progress;

import com.marquee.api.catalog.TitleCardResponse;
import java.util.List;

public record HomeRowResponse(String title, List<TitleCardResponse> items) {
}
