package com.marquee.api.storage;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marquee.api.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.core.sync.RequestBody;

class MediaIT extends IntegrationTestSupport {
    @Autowired private MockMvc mockMvc;

    @Test
    void servesTitleArtworkWithoutAuthentication() throws Exception {
        S3.putObject(b -> b.bucket(BUCKET).key("images/901/poster.png").contentType("image/png"), RequestBody.fromString("png-bytes"));

        MvcResult result = mockMvc.perform(get("/media/images/901/poster.png")).andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", "max-age=3600, public"))
                .andExpect(content().string("png-bytes"));
    }

    @Test
    void exposesOnlyArtworkKeys() throws Exception {
        mockMvc.perform(get("/media/images/901/source.mp4")).andExpect(status().isNotFound());
        mockMvc.perform(get("/media/images/902/poster.jpg")).andExpect(status().isNotFound());
        mockMvc.perform(get("/media/raw/1/source.mp4")).andExpect(status().isUnauthorized());
    }
}
