package com.marquee.api.playback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marquee.api.IntegrationTestSupport;
import com.marquee.api.catalog.MaturityRating;
import com.marquee.api.catalog.Title;
import com.marquee.api.catalog.TitleRepository;
import com.marquee.api.catalog.TitleType;
import com.marquee.api.catalog.VideoAsset;
import com.marquee.api.catalog.VideoAssetRepository;
import com.marquee.api.catalog.VideoAssetStatus;
import com.marquee.api.profile.Profile;
import com.marquee.api.profile.ProfileRepository;
import com.marquee.api.security.JwtService;
import com.marquee.api.user.User;
import com.marquee.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;

@Transactional
class PlaybackAuthIT extends IntegrationTestSupport {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtService jwtService;
    @Autowired private PlaybackTokenService tokenService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private TitleRepository titleRepository;
    @Autowired private VideoAssetRepository videoAssetRepository;
    @Value("${app.jwt.stream-secret}") private String streamSecret;

    private String bearer;
    private Profile adult;
    private Profile kid;
    private VideoAsset asset;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(new User("player-" + System.nanoTime() + "@example.com", "hash"));
        bearer = "Bearer " + jwtService.generateAccessToken(user);
        adult = profileRepository.save(new Profile(user, "Adult", null, false));
        kid = profileRepository.save(new Profile(user, "Kid", null, true));

        Title title = new Title(TitleType.MOVIE, "Rated R", null, 2024, MaturityRating.R);
        title.setPublished(true);
        title = titleRepository.save(title);
        asset = new VideoAsset(title, VideoAssetStatus.READY);
        asset.setDurationSeconds(12);
        asset = videoAssetRepository.save(asset);
        asset.setMasterPlaylistKey("hls/" + asset.getId() + "/master.m3u8");

        String prefix = "hls/" + asset.getId() + "/";
        put(prefix + "master.m3u8", "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=896000,RESOLUTION=640x360\n360p/index.m3u8\n");
        put(prefix + "360p/index.m3u8", "#EXTM3U\n#EXTINF:6.0,\nseg_00000.ts\n#EXT-X-ENDLIST\n");
        put(prefix + "360p/seg_00000.ts", "0123456789");
    }

    @Test
    void validTokenStreamsRewrittenPlaylistsAndRangedSegments() throws Exception {
        String manifestUrl = manifestUrl(adult);
        String token = manifestUrl.substring(manifestUrl.indexOf("token=") + "token=".length());

        String master = streamBody(manifestUrl, null, 200);
        assertThat(master).contains("/stream/" + asset.getId() + "/360p/index.m3u8?token=" + token);

        String variant = streamBody("/stream/" + asset.getId() + "/360p/index.m3u8?token=" + token, null, 200);
        assertThat(variant).contains("/stream/" + asset.getId() + "/360p/seg_00000.ts?token=" + token);

        MvcResult segment = mockMvc.perform(get("/stream/" + asset.getId() + "/360p/seg_00000.ts?token=" + token).header("Range", "bytes=2-5"))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(segment))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Type", "video/mp2t"))
                .andExpect(header().string("Content-Range", "bytes 2-5/10"));
        assertThat(segment.getResponse().getContentAsString()).isEqualTo("2345");
    }

    @Test
    void streamRejectsMissingTamperedExpiredAndForeignTokens() throws Exception {
        String path = "/stream/" + asset.getId() + "/master.m3u8";
        String valid = tokenService.generate(asset.getId(), adult.getId());
        String expired = new PlaybackTokenService(streamSecret, -1).generate(asset.getId(), adult.getId());
        String otherAsset = tokenService.generate(asset.getId() + 1000, adult.getId());

        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(path + "?token=" + valid.substring(0, valid.length() - 2) + "xx")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(path + "?token=" + expired)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(path + "?token=" + otherAsset)).andExpect(status().isUnauthorized());
    }

    @Test
    void playbackRequiresLoginAndRespectsKidsRating() throws Exception {
        mockMvc.perform(get("/api/playback/" + asset.getId()).header("X-Profile-Id", adult.getId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/playback/" + asset.getId()).header("Authorization", bearer).header("X-Profile-Id", kid.getId()))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", "application/problem+json"));
    }

    private String manifestUrl(Profile profile) throws Exception {
        String body = mockMvc.perform(get("/api/playback/" + asset.getId()).header("Authorization", bearer).header("X-Profile-Id", profile.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationSeconds").value(12))
                .andExpect(jsonPath("$.resumeAt").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("manifestUrl").asText();
    }

    private String streamBody(String url, String range, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(get(url)).andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().is(expectedStatus))
                .andExpect(header().string("Content-Type", "application/vnd.apple.mpegurl"))
                .andExpect(header().exists("Content-Length"));
        return result.getResponse().getContentAsString();
    }

    private static void put(String key, String content) {
        S3.putObject(b -> b.bucket(BUCKET).key(key), RequestBody.fromString(content));
    }
}
