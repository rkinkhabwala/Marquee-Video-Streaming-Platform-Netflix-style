package com.marquee.api.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "titles")
public class Title {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TitleType type;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String synopsis;

    @Column(name = "release_year")
    private Integer releaseYear;

    @Convert(converter = MaturityRatingConverter.class)
    @Column(name = "maturity_rating")
    private MaturityRating maturityRating;

    @Column(name = "poster_key")
    private String posterKey;

    @Column(name = "backdrop_key")
    private String backdropKey;

    @Column(nullable = false)
    private boolean published = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "title_genres",
            joinColumns = @JoinColumn(name = "title_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id"))
    private Set<Genre> genres = new HashSet<>();

    protected Title() {
    }

    public Title(TitleType type, String name, String synopsis, Integer releaseYear, MaturityRating maturityRating) {
        this.type = type;
        this.name = name;
        this.synopsis = synopsis;
        this.releaseYear = releaseYear;
        this.maturityRating = maturityRating;
    }

    public Long getId() {
        return id;
    }

    public TitleType getType() {
        return type;
    }

    public void setType(TitleType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSynopsis() {
        return synopsis;
    }

    public void setSynopsis(String synopsis) {
        this.synopsis = synopsis;
    }

    public Integer getReleaseYear() {
        return releaseYear;
    }

    public void setReleaseYear(Integer releaseYear) {
        this.releaseYear = releaseYear;
    }

    public MaturityRating getMaturityRating() {
        return maturityRating;
    }

    public void setMaturityRating(MaturityRating maturityRating) {
        this.maturityRating = maturityRating;
    }

    public String getPosterKey() {
        return posterKey;
    }

    public void setPosterKey(String posterKey) {
        this.posterKey = posterKey;
    }

    public String getBackdropKey() {
        return backdropKey;
    }

    public void setBackdropKey(String backdropKey) {
        this.backdropKey = backdropKey;
    }

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<Genre> getGenres() {
        return genres;
    }

    public void setGenres(Set<Genre> genres) {
        this.genres = genres;
    }
}
