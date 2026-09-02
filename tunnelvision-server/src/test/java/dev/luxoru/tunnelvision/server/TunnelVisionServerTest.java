package dev.luxoru.tunnelvision.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "tunnelvision.refresh-enabled=false",
    "tunnelvision.snapshot-directory=",
    "tunnelvision.sources.disabled=tor,x4b-vpn,x4b-datacenter",
    "tunnelvision.sources.custom[0].id=test-vpn",
    "tunnelvision.sources.custom[0].name=Test VPN",
    "tunnelvision.sources.custom[0].category=VPN",
    "tunnelvision.sources.custom[0].confidence=CONFIRMED",
    "tunnelvision.sources.custom[0].entries=45.1.0.0/16",
    "tunnelvision.weights.confirmed.vpn=0.8",
    "management.server.port=",
})
@AutoConfigureMockMvc
class TunnelVisionServerTest {
    @Autowired
    MockMvc mvc;

    @Test
    void checksAnAddress() throws Exception {
        mvc.perform(get("/v1/check/45.1.2.3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ip").value("45.1.2.3"))
            .andExpect(jsonPath("$.blocked").value(true))
            .andExpect(jsonPath("$.provider").value("Test VPN"))
            .andExpect(jsonPath("$.risk").value(0.8)) // From the weight override above
            .andExpect(jsonPath("$.status").value("CHECKED"))
            .andExpect(jsonPath("$.categories[0]").value("VPN"))
            .andExpect(jsonPath("$.detections[0].source").value("test-vpn"));
    }

    @Test
    void checksABatch() throws Exception {
        mvc.perform(post("/v1/check")
                .contentType(MediaType.APPLICATION_JSON).content("{\"ips\":[\"45.1.2.3\",\"8.8.8.8\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verdicts[0].blocked").value(true))
            .andExpect(jsonPath("$.verdicts[1].blocked").value(false));
    }

    @Test
    void rejectsInvalidAddresses() throws Exception {
        mvc.perform(get("/v1/check/not-an-ip"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void listsSources() throws Exception {
        mvc.perform(get("/v1/sources"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("test-vpn"))
            .andExpect(jsonPath("$[0].state").value("ACTIVE"));
    }

    @Test
    void validatesBatchesAgainstTheSpec() throws Exception {
        String tooMany = "{\"ips\":[" + "\"1.1.1.1\",".repeat(1000) + "\"1.1.1.1\"]}";
        for (String body : new String[] { "{\"ips\":[]}", "{}", tooMany }) {
            mvc.perform(post("/v1/check")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
    }

    @Test
    void servesTheSpec() throws Exception {
        mvc.perform(get("/openapi.yaml"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("operationId: checkAddress")));
    }

    @Test
    void reportsReadiness() throws Exception {
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }
}
