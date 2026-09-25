package com.processgroup.distillation.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gilliland 两条路径的 HTTP 端到端测试：结构化 JSON 进/出，失败同样结构化。
 */
@SpringBootTest
class GillilandControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    private static final String COLUMN = """
            "feedComposition":0.5,"distillateComposition":0.95,
            "bottomsComposition":0.05,"feedThermalFactor":1.0,
            "relativeVolatility":2.5
            """;

    @Test
    void stagesForRefluxReturnsPointOnCurve() throws Exception {
        String body = "{" + COLUMN + ",\"refluxRatio\":2.2}";

        MvcResult result = mockMvc.perform(post("/api/distillation/gilliland/stages-for-reflux")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refluxRatio").value(2.2))
                .andExpect(jsonPath("$.theoreticalStages").value(org.hamcrest.Matchers.greaterThan(10.0)))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(root.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "minimumRefluxRatio", "minimumStages", "refluxRatio", "theoreticalStages", "x", "y");
        assertThat(root.get("minimumRefluxRatio").asDouble()).isCloseTo(1.1, within(1.0e-9));
        // X=(2.2-1.1)/3.2=0.34375，Y≈0.34945（经典 Gilliland 图上 X=0.344 的读数）
        assertThat(root.get("x").asDouble()).isCloseTo(0.34375, within(1.0e-9));
        assertThat(root.get("y").asDouble()).isCloseTo(0.3494485, within(1.0e-6));
        assertThat(root.get("minimumStages").asDouble()).isPositive();
    }

    @Test
    void refluxForStagesRoundTripsWithForwardEndpoint() throws Exception {
        // 先用给 N 求 R 拿到 R，再用给 R 求 N 回到同一个设计点
        String stagesBody = "{" + COLUMN + ",\"targetStages\":10.416}";
        MvcResult stagesResult = mockMvc.perform(post("/api/distillation/gilliland/reflux-for-stages")
                        .contentType(MediaType.APPLICATION_JSON).content(stagesBody))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode reverse = objectMapper.readTree(stagesResult.getResponse().getContentAsString());
        double reflux = reverse.get("refluxRatio").asDouble();
        assertThat(reflux).isCloseTo(2.2, within(1.0e-3));

        String refluxBody = "{" + COLUMN + ",\"refluxRatio\":" + reflux + "}";
        MvcResult forwardResult = mockMvc.perform(post("/api/distillation/gilliland/stages-for-reflux")
                        .contentType(MediaType.APPLICATION_JSON).content(refluxBody))
                .andExpect(status().isOk())
                .andReturn();
        double stagesBack = objectMapper.readTree(forwardResult.getResponse().getContentAsString())
                .get("theoreticalStages").asDouble();
        assertThat(stagesBack).isCloseTo(10.416, within(1.0e-6));
    }

    @Test
    void refluxAtMinimumReturns422Insufficient() throws Exception {
        String body = "{" + COLUMN + ",\"refluxRatio\":1.1}";
        mockMvc.perform(post("/api/distillation/gilliland/stages-for-reflux")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REFLUX_INSUFFICIENT"));
    }

    @Test
    void stagesAtOrBelowMinimumReturns422BelowMinimum() throws Exception {
        // Nmin≈6.42687，给 6.0 必然低于物理下限
        String body = "{" + COLUMN + ",\"targetStages\":6.0}";
        mockMvc.perform(post("/api/distillation/gilliland/reflux-for-stages")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("STAGES_BELOW_MINIMUM"))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void alphaAtOneReturns422CannotSeparate() throws Exception {
        String body = """
                {"feedComposition":0.5,"distillateComposition":0.95,
                 "bottomsComposition":0.05,"feedThermalFactor":1.0,
                 "relativeVolatility":1.0,"refluxRatio":5.0}
                """;
        mockMvc.perform(post("/api/distillation/gilliland/stages-for-reflux")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SEPARATION_IMPOSSIBLE"));
    }

    @Test
    void compositionOutOfRangeReturns400StructuredError() throws Exception {
        String body = """
                {"feedComposition":1.5,"distillateComposition":0.95,
                 "bottomsComposition":0.05,"feedThermalFactor":1.0,
                 "relativeVolatility":2.5,"refluxRatio":5.0}
                """;
        mockMvc.perform(post("/api/distillation/gilliland/stages-for-reflux")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fields.feedComposition").exists());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/distillation/gilliland/reflux-for-stages")
                        .contentType(MediaType.APPLICATION_JSON).content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void existingShortcutEndpointStillReturnsExactlyTheThreeOutputs() throws Exception {
        // 新功能不得改动原有三样输出
        String body = """
                {"feedFlow":1.0,"feedComposition":0.5,"distillateComposition":0.95,
                 "bottomsComposition":0.05,"feedThermalFactor":1.0,
                 "relativeVolatility":2.5,"refluxRatio":5.0}
                """;
        MvcResult result = mockMvc.perform(post("/api/distillation/shortcut")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stepping.totalStages").value(8))
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(root.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("minimumRefluxRatio", "minimumStages", "stepping");
    }
}
