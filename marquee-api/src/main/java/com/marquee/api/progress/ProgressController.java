package com.marquee.api.progress;

import com.marquee.api.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ProgressController {
    private final ProgressService progressService;

    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    @PutMapping("/profiles/{profileId}/progress/{assetId}")
    public WatchProgressResponse saveProgress(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable Long profileId,
                                             @PathVariable Long assetId,
                                             @Valid @RequestBody WatchProgressRequest request) {
        return progressService.saveProgress(principal.getId(), profileId, assetId, request.positionSeconds());
    }

    @GetMapping("/home")
    public List<HomeRowResponse> getHome(@AuthenticationPrincipal UserPrincipal principal,
                                        @RequestHeader("X-Profile-Id") Long profileId) {
        return progressService.getHome(principal.getId(), profileId);
    }

    @PutMapping("/my-list/{titleId}")
    public ResponseEntity<Void> addToMyList(@AuthenticationPrincipal UserPrincipal principal,
                                           @RequestHeader("X-Profile-Id") Long profileId,
                                           @PathVariable Long titleId) {
        progressService.addToMyList(principal.getId(), profileId, titleId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/my-list/{titleId}")
    public ResponseEntity<Void> removeFromMyList(@AuthenticationPrincipal UserPrincipal principal,
                                                @RequestHeader("X-Profile-Id") Long profileId,
                                                @PathVariable Long titleId) {
        progressService.removeFromMyList(principal.getId(), profileId, titleId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/ratings/{titleId}")
    public ResponseEntity<Void> rate(@AuthenticationPrincipal UserPrincipal principal,
                                     @RequestHeader("X-Profile-Id") Long profileId,
                                     @PathVariable Long titleId,
                                     @Valid @RequestBody RatingRequest request) {
        progressService.rate(principal.getId(), profileId, titleId, request.value());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/series/{titleId}/next-episode")
    public NextEpisodeResponse getNextEpisode(@AuthenticationPrincipal UserPrincipal principal,
                                             @RequestHeader("X-Profile-Id") Long profileId,
                                             @PathVariable Long titleId) {
        return progressService.getNextEpisode(principal.getId(), profileId, titleId);
    }
}
