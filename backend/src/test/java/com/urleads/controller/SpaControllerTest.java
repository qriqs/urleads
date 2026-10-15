package com.urleads.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import com.urleads.repository.UsuarioRepository;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration"
})
@AutoConfigureMockMvc
class SpaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private com.urleads.repository.LeadRepository leadRepository;

    @MockBean
    private com.urleads.security.CurrentUserProvider currentUserProvider;

    @Test
    void rootIndexAndExtensionlessClientRoutesServeActualSpaHtml() throws Exception {
        mockMvc.perform(get("/").accept("text/html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("<div id=\"root\"></div>")));

        mockMvc.perform(get("/index.html").accept("text/html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<div id=\"root\"></div>")));

        mockMvc.perform(get("/leads/42").accept("text/html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<div id=\"root\"></div>")));
    }

    @Test
    void generatedAssetsAreServedAndMissingAssetsStay404() throws Exception {
        String html = mockMvc.perform(get("/").accept("text/html"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher assetMatcher = Pattern.compile("(?:src|href)=\\\"(/assets/[^\\\"]+)\\\"").matcher(html);
        int assetCount = 0;
        while (assetMatcher.find()) {
            byte[] body = mockMvc.perform(get(assetMatcher.group(1)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
            org.junit.jupiter.api.Assertions.assertTrue(body.length > 0, "Generated asset must not be empty");
            assetCount++;
        }
        org.junit.jupiter.api.Assertions.assertTrue(assetCount >= 2, "Expected generated JS and CSS links");

        mockMvc.perform(get("/assets/missing.js"))
                .andExpect(status().isNotFound());
    }

    @Test
    void apiPathsRemainOutsideSpaFallbackBeforeAndAfterAuthentication() throws Exception {
        mockMvc.perform(get("/api"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().string(not(containsString("<div id=\"root\"></div>"))));

        mockMvc.perform(get("/api/unknown"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    @WithMockUser
    void authenticatedUnknownApiStillDoesNotBecomeSpaHtml() throws Exception {
        mockMvc.perform(get("/api"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("<div id=\"root\"></div>"))));

        mockMvc.perform(get("/api/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("<div id=\"root\"></div>"))));
    }
}
