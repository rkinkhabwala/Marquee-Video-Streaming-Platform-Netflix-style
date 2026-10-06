package com.marquee.api.catalog;

import com.marquee.api.storage.ImageUploadRequest;
import com.marquee.api.storage.ObjectStorageService;
import com.marquee.api.storage.PresignedUploadResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Validated
public class AdminCatalogController {
    private final TitleService titleService;
    private final ObjectStorageService objectStorageService;

    public AdminCatalogController(TitleService titleService, ObjectStorageService objectStorageService) {
        this.titleService = titleService;
        this.objectStorageService = objectStorageService;
    }

    @GetMapping("/titles")
    public List<TitleResponse> listTitles() {
        return titleService.listTitles();
    }

    @PostMapping("/titles")
    public ResponseEntity<TitleResponse> createTitle(@Valid @RequestBody CreateTitleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(titleService.createTitle(request));
    }

    @GetMapping("/titles/{id}")
    public TitleResponse getTitle(@PathVariable Long id) {
        return titleService.getTitle(id);
    }

    @PutMapping("/titles/{id}")
    public TitleResponse updateTitle(@PathVariable Long id, @RequestBody UpdateTitleRequest request) {
        return titleService.updateTitle(id, request);
    }

    @DeleteMapping("/titles/{id}")
    public ResponseEntity<Void> deleteTitle(@PathVariable Long id) {
        titleService.deleteTitle(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/titles/{id}/publish")
    public TitleResponse publishTitle(@PathVariable Long id) {
        return titleService.publishTitle(id);
    }

    @GetMapping("/genres")
    public List<GenreResponse> listGenres() {
        return titleService.listGenres();
    }

    @PostMapping("/genres")
    public ResponseEntity<GenreResponse> createGenre(@RequestBody CreateGenreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(titleService.createGenre(request.name()));
    }

    @GetMapping("/titles/{titleId}/seasons")
    public List<SeasonResponse> listSeasons(@PathVariable Long titleId) {
        return titleService.listSeasons(titleId);
    }

    @PostMapping("/titles/{titleId}/seasons")
    public ResponseEntity<SeasonResponse> createSeason(@PathVariable Long titleId,
                                                    @Valid @RequestBody CreateSeasonRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(titleService.createSeason(titleId, request));
    }

    @GetMapping("/seasons/{id}")
    public SeasonResponse getSeason(@PathVariable Long id) {
        return titleService.getSeason(id);
    }

    @PutMapping("/seasons/{id}")
    public SeasonResponse updateSeason(@PathVariable Long id, @RequestBody UpdateSeasonRequest request) {
        return titleService.updateSeason(id, request);
    }

    @DeleteMapping("/seasons/{id}")
    public ResponseEntity<Void> deleteSeason(@PathVariable Long id) {
        titleService.deleteSeason(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/seasons/{seasonId}/episodes")
    public List<EpisodeResponse> listEpisodes(@PathVariable Long seasonId) {
        return titleService.listEpisodes(seasonId);
    }

    @PostMapping("/seasons/{seasonId}/episodes")
    public ResponseEntity<EpisodeResponse> createEpisode(@PathVariable Long seasonId,
                                                        @Valid @RequestBody CreateEpisodeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(titleService.createEpisode(seasonId, request));
    }

    @PutMapping("/episodes/{id}")
    public EpisodeResponse updateEpisode(@PathVariable Long id, @RequestBody UpdateEpisodeRequest request) {
        return titleService.updateEpisode(id, request);
    }

    @DeleteMapping("/episodes/{id}")
    public ResponseEntity<Void> deleteEpisode(@PathVariable Long id) {
        titleService.deleteEpisode(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/images/presign")
    public PresignedUploadResponse presignImageUpload(@Valid @RequestBody ImageUploadRequest request) {
        return objectStorageService.presignImageUpload(request);
    }
}
