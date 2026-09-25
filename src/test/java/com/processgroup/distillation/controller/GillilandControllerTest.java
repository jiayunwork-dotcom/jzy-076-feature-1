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
 * Gilliland 权衡曲线 HTTP 接口端到端测试：
 * 结构化 JSON 进、设计点出，错误响应同样结构化，两条路径在接口层面闭合。
 */
@SpringBootTest
class GillilandControllerTest {

    private static final String BASIS = """
            "feedComposition":0.5,"distillateComposition":0.95,"bottomsComposition":0.05,
            "feedThermalFactor":1.0,"relativeVolatility":2.5
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    void stagesEndpointReturnsDesignPointOnTheCurve() throws Exception {
        String body = "{" + BASIS + ",\"refluxRatio\":5.0}";

        MvcResult result = mockMvc.perform(post("/api/distillation/gilliland/stages")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                // 锚点与简捷法接口同源：Rmin=1.1、Nmin=6.42687
                .andExpect(jsonPath("$.minimumRefluxRatio")
                        .value(org.hamcrest.Matchers.closeTo(1.1, 1.0e-9)))
                .andExpect(jsonPath("$.minimumStages")
                        .value(org.hamcrest.Matchers.closeTo(6.42687, 1.0e-5)))
                .andExpect(jsonPath("$.refluxRatio").value(5.0))
                // Molokanov 关联在 R=5（X=0.65）处读出 N≈7.901
                .andExpect(jsonPath("$.theoreticalStages")
                        .value(org.hamcrest.Matchers.closeTo(7.901, 1.0e-3)))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        // 设计点响应恰好四个字段：两个锚点 + 一对 (R, N)
        assertThat(root.fieldNames()).toIterable()
                .containsExactlyInAnyOrder(
                        "minimumRefluxRatio", "minimumStages", "refluxRatio", "theoreticalStages");
    }

    @Test
    void refluxEndpointInvertsTheStagesEndpoint() throws Exception {
        // 先给 R=5 求 N
        MvcResult forward = mockMvc.perform(post("/api/distillation/gilliland/stages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + BASIS + ",\"refluxRatio\":5.0}"))
                .andExpect(status().isOk())
                .andReturn();
        double n = objectMapper.readTree(forward.getResponse().getContentAsString())
                .get("theoreticalStages").asDouble();

        // 再拿这个 N 反求 R，HTTP 层面必须闭合回 5.0
        MvcResult backward = mockMvc.perform(post("/api/distillation/gilliland/reflux")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + BASIS + ",\"targetStages\":" + n + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minimumRefluxRatio")
                        .value(org.hamcrest.Matchers.closeTo(1.1, 1.0e-9)))
                .andReturn();
        double rBack = objectMapper.readTree(backward.getResponse().getContentAsString())
                .get("refluxRatio").asDouble();
        assertThat(rBack).isCloseTo(5.0, within(1.0e-6));
    }

    @Test
    void refluxAtMinimumReturns422InsufficientReflux() throws Exception {
        // R = Rmin = 1.1：X 落到 0，曲线上没有有限板数
        mockMvc.perform(post("/api/distillation/gilliland/stages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + BASIS + ",\"refluxRatio\":1.1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REFLUX_INSUFFICIENT"));
    }

    @Test
    void stagesBelowMinimumReturns422Unreachable() throws Exception {
        // Nmin≈6.427，目标 6 块板低于理论下限
        mockMvc.perform(post("/api/distillation/gilliland/reflux")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + BASIS + ",\"targetStages\":6.0}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("STAGES_BELOW_MINIMUM"));
    }

    @Test
    void compositionOutOfRangeReturns400StructuredError() throws Exception {
        String body = """
                {"feedComposition":1.5,"distillateComposition":0.95,"bottomsComposition":0.05,
                 "feedThermalFactor":1.0,"relativeVolatility":2.5,"refluxRatio":5.0}
                """;
        mockMvc.perform(post("/api/distillation/gilliland/stages")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fields.feedComposition").exists());
    }

    @Test
    void nonPositiveTargetStagesReturns400() throws Exception {
        mockMvc.perform(post("/api/distillation/gilliland/reflux")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + BASIS + ",\"targetStages\":0.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fields.targetStages").exists());
    }

    @Test
    void volatilityAtOneReturns422CannotSeparate() throws Exception {
        String body = """
                {"feedComposition":0.5,"distillateComposition":0.95,"bottomsComposition":0.05,
                 "feedThermalFactor":1.0,"relativeVolatility":1.0,"refluxRatio":5.0}
                """;
        mockMvc.perform(post("/api/distillation/gilliland/stages")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SEPARATION_IMPOSSIBLE"));
    }
}
