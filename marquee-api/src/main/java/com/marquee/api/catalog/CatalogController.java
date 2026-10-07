package com.marquee.api.catalog;

import com.marquee.api.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/titles/{id}")
    public TitleDetailResponse getTitle(@AuthenticationPrincipal UserPrincipal principal,
                                        @RequestHeader("X-Profile-Id") Long profileId,
                                        @PathVariable Long id) {
        return catalogService.getTitle(principal.getId(), profileId, id);
    }

    @GetMapping("/titles")
    public PageResponse<TitleCardResponse> browse(@AuthenticationPrincipal UserPrincipal principal,
                                                  @RequestHeader("X-Profile-Id") Long profileId,
                                                  @RequestParam(required = false) String genre,
                                                  @RequestParam(required = false) TitleType type,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return catalogService.browse(principal.getId(), profileId, genre, type, page, size);
    }

    @GetMapping("/search")
    public PageResponse<TitleCardResponse> search(@AuthenticationPrincipal UserPrincipal principal,
                                                  @RequestHeader("X-Profile-Id") Long profileId,
                                                  @RequestParam String q,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return catalogService.search(principal.getId(), profileId, q, page, size);
    }
}
