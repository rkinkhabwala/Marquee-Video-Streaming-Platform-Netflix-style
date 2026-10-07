package com.marquee.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class FlywaySchemaMigrationIT extends IntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayAppliesThePhaseOneSchema() {
        Set<String> tables = jdbcTemplate.queryForList(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name",
                        String.class)
                .stream()
                .collect(Collectors.toSet());

        Set<String> expectedTables = Set.of(
                "episodes",
                "genres",
                "my_list",
                "profiles",
                "ratings",
                "seasons",
                "titles",
                "title_genres",
                "transcode_jobs",
                "users",
                "video_assets",
                "watch_progress");

        assertThat(tables).containsAll(expectedTables);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'role'",
                        Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'video_assets' AND column_name = 'status'",
                        Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'titles' AND column_name = 'search_vector'",
                        Integer.class))
                .isEqualTo(1);
    }
}
