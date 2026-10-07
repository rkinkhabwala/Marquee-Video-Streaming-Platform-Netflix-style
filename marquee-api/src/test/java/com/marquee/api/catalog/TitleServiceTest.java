package com.marquee.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.marquee.api.recsys.EngagementRecorder;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TitleServiceTest {

    @Mock
    private TitleRepository titleRepository;

    @Mock
    private GenreRepository genreRepository;

    @Mock
    private SeasonRepository seasonRepository;

    @Mock
    private EpisodeRepository episodeRepository;

    @Mock
    private VideoAssetRepository videoAssetRepository;

    @Mock
    private EngagementRecorder engagement;

    @InjectMocks
    private TitleService titleService;

    @Test
    void createTitleStoresGenreLinksAndReturnsResponse() throws Exception {
        Genre genre = new Genre("Action");
        setId(genre, 5L);
        when(genreRepository.findById(5L)).thenReturn(Optional.of(genre));

        Title saved = new Title(TitleType.MOVIE, "Test Title", "Summary", 2024, MaturityRating.PG_13);
        setId(saved, 11L);
        saved.setPublished(true);
        saved.setGenres(Set.of(genre));
        when(titleRepository.save(any(Title.class))).thenReturn(saved);

        TitleResponse response = titleService.createTitle(new CreateTitleRequest(TitleType.MOVIE, "Test Title", "Summary", 2024,
                "PG-13", List.of(5L), "poster.jpg", "backdrop.jpg", true));

        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.genres()).containsExactly("Action");
        assertThat(response.published()).isTrue();
        verify(titleRepository).save(any(Title.class));
    }

    @Test
    void publishTitleMarksTitlePublished() throws Exception {
        Title title = new Title(TitleType.SERIES, "Series", "Synopsis", 2024, MaturityRating.TV_Y);
        setId(title, 77L);
        when(titleRepository.findWithGenresById(77L)).thenReturn(Optional.of(title));
        when(titleRepository.save(title)).thenReturn(title);
        when(seasonRepository.findByTitleIdOrderBySeasonNumberAsc(77L)).thenReturn(List.of());

        TitleResponse response = titleService.publishTitle(77L);

        assertThat(response.published()).isTrue();
    }

    @Test
    void createGenreRejectsBlankName() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> titleService.createGenre("   "));
        assertThat(ex.getStatusCode().value()).isEqualTo(400);
    }

    private static void setId(Object target, Long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
