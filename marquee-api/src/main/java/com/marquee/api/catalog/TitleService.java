package com.marquee.api.catalog;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TitleService {
    private final TitleRepository titleRepository;
    private final GenreRepository genreRepository;
    private final SeasonRepository seasonRepository;
    private final EpisodeRepository episodeRepository;

    public TitleService(TitleRepository titleRepository,
                        GenreRepository genreRepository,
                        SeasonRepository seasonRepository,
                        EpisodeRepository episodeRepository) {
        this.titleRepository = titleRepository;
        this.genreRepository = genreRepository;
        this.seasonRepository = seasonRepository;
        this.episodeRepository = episodeRepository;
    }

    @Transactional(readOnly = true)
    public List<TitleResponse> listTitles() {
        return titleRepository.findAll().stream()
                .map(this::toTitleResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TitleResponse getTitle(Long id) {
        Title title = titleRepository.findWithGenresById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Title not found"));
        return toTitleResponse(title);
    }

    @Transactional
    public TitleResponse createTitle(CreateTitleRequest request) {
        if (request.type() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title type is required");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title name is required");
        }

        Title title = new Title(request.type(), request.name().trim(), request.synopsis(), request.releaseYear(), parseMaturity(request.maturityRating()));
        if (request.posterKey() != null) {
            title.setPosterKey(request.posterKey());
        }
        if (request.backdropKey() != null) {
            title.setBackdropKey(request.backdropKey());
        }
        if (request.published() != null) {
            title.setPublished(request.published());
        }

        Set<Genre> genres = resolveGenres(request.genreIds());
        title.setGenres(genres);
        Title savedTitle = titleRepository.save(title);
        return toTitleResponse(savedTitle);
    }

    @Transactional
    public TitleResponse updateTitle(Long id, UpdateTitleRequest request) {
        Title title = titleRepository.findWithGenresById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Title not found"));

        if (request.type() != null) {
            title.setType(request.type());
        }
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title name cannot be blank");
            }
            title.setName(request.name().trim());
        }
        if (request.synopsis() != null) {
            title.setSynopsis(request.synopsis());
        }
        if (request.releaseYear() != null) {
            title.setReleaseYear(request.releaseYear());
        }
        if (request.maturityRating() != null) {
            title.setMaturityRating(parseMaturity(request.maturityRating()));
        }
        if (request.posterKey() != null) {
            title.setPosterKey(request.posterKey());
        }
        if (request.backdropKey() != null) {
            title.setBackdropKey(request.backdropKey());
        }
        if (request.published() != null) {
            title.setPublished(request.published());
        }
        if (request.genreIds() != null) {
            title.setGenres(resolveGenres(request.genreIds()));
        }
        titleRepository.save(title);
        return toTitleResponse(title);
    }

    @Transactional
    public void deleteTitle(Long id) {
        if (!titleRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Title not found");
        }
        titleRepository.deleteById(id);
    }

    @Transactional
    public TitleResponse publishTitle(Long id) {
        Title title = titleRepository.findWithGenresById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Title not found"));
        title.setPublished(true);
        return toTitleResponse(titleRepository.save(title));
    }

    @Transactional(readOnly = true)
    public List<GenreResponse> listGenres() {
        return genreRepository.findAllByOrderByNameAsc().stream().map(GenreResponse::from).toList();
    }

    @Transactional
    public GenreResponse createGenre(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Genre name is required");
        }
        String normalized = name.trim();
        return genreRepository.findByNameIgnoreCase(normalized)
                .map(GenreResponse::from)
                .orElseGet(() -> GenreResponse.from(genreRepository.save(new Genre(normalized))));
    }

    @Transactional(readOnly = true)
    public List<SeasonResponse> listSeasons(Long titleId) {
        Title title = titleRepository.findById(titleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Title not found"));
        return seasonRepository.findByTitleIdOrderBySeasonNumberAsc(title.getId()).stream()
                .map(this::toSeasonResponse)
                .toList();
    }

    @Transactional
    public SeasonResponse createSeason(Long titleId, CreateSeasonRequest request) {
        Title title = titleRepository.findById(titleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Title not found"));
        if (request.seasonNumber() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Season number is required");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Season name is required");
        }
        Season season = new Season(title, request.seasonNumber(), request.name().trim());
        return toSeasonResponse(seasonRepository.save(season));
    }

    @Transactional
    public SeasonResponse updateSeason(Long seasonId, UpdateSeasonRequest request) {
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Season not found"));
        if (request.seasonNumber() != null) {
            season.setSeasonNumber(request.seasonNumber());
        }
        if (request.name() != null && !request.name().isBlank()) {
            season.setName(request.name().trim());
        }
        return toSeasonResponse(seasonRepository.save(season));
    }

    @Transactional
    public void deleteSeason(Long seasonId) {
        if (!seasonRepository.existsById(seasonId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Season not found");
        }
        seasonRepository.deleteById(seasonId);
    }

    @Transactional(readOnly = true)
    public SeasonResponse getSeason(Long seasonId) {
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Season not found"));
        return toSeasonResponse(season);
    }

    @Transactional(readOnly = true)
    public List<EpisodeResponse> listEpisodes(Long seasonId) {
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Season not found"));
        return episodeRepository.findBySeasonIdOrderByEpisodeNumberAsc(season.getId()).stream()
                .map(EpisodeResponse::from)
                .toList();
    }

    @Transactional
    public EpisodeResponse createEpisode(Long seasonId, CreateEpisodeRequest request) {
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Season not found"));
        if (request.episodeNumber() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Episode number is required");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Episode name is required");
        }
        Episode episode = new Episode(season, request.episodeNumber(), request.name().trim(), request.synopsis(), null);
        return EpisodeResponse.from(episodeRepository.save(episode));
    }

    @Transactional
    public EpisodeResponse updateEpisode(Long episodeId, UpdateEpisodeRequest request) {
        Episode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Episode not found"));
        if (request.episodeNumber() != null) {
            episode.setEpisodeNumber(request.episodeNumber());
        }
        if (request.name() != null && !request.name().isBlank()) {
            episode.setName(request.name().trim());
        }
        if (request.synopsis() != null) {
            episode.setSynopsis(request.synopsis());
        }
        return EpisodeResponse.from(episodeRepository.save(episode));
    }

    @Transactional
    public void deleteEpisode(Long episodeId) {
        if (!episodeRepository.existsById(episodeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Episode not found");
        }
        episodeRepository.deleteById(episodeId);
    }

    private TitleResponse toTitleResponse(Title title) {
        List<SeasonResponse> seasons = seasonRepository.findByTitleIdOrderBySeasonNumberAsc(title.getId())
                .stream()
                .map(this::toSeasonResponse)
                .toList();
        return TitleResponse.from(title, seasons);
    }

    private SeasonResponse toSeasonResponse(Season season) {
        List<Episode> episodes = episodeRepository.findBySeasonIdOrderByEpisodeNumberAsc(season.getId());
        return SeasonResponse.from(season, episodes);
    }

    private Set<Genre> resolveGenres(List<Long> genreIds) {
        if (genreIds == null || genreIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<Genre> genres = new LinkedHashSet<>();
        for (Long id : genreIds) {
            Genre genre = genreRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Genre not found: " + id));
            genres.add(genre);
        }
        return genres;
    }

    private MaturityRating parseMaturity(String maturityRating) {
        if (maturityRating == null || maturityRating.isBlank()) {
            return null;
        }
        try {
            return MaturityRating.fromValue(maturityRating);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
