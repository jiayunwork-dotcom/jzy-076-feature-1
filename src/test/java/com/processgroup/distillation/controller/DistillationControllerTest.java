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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP 接口端到端测试：结构化参数进、结构化结果出，错误响应同样结构化。
 */
@SpringBootTest
class DistillationControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    private static String json(String body) {
        return body;
    }

    @Test
    void happyPathReturnsExactlyTheThreeOutputs() throws Exception {
        String body = json("""
                {
                  "feedFlow": 1.0,
                  "feedComposition": 0.5,
                  "distillateComposition": 0.95,
                  "bottomsComposition": 0.05,
                  "feedThermalFactor": 1.0,
                  "relativeVolatility": 2.5,
                  "refluxRatio": 5.0
                }
                """);

        MvcResult result = mockMvc.perform(post("/api/distillation/shortcut")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minimumRefluxRatio").value(org.hamcrest.Matchers.closeTo(1.1, 1.0e-9)))
                .andExpect(jsonPath("$.minimumStages").exists())
                .andExpect(jsonPath("$.stepping.totalStages").value(8))
                .andExpect(jsonPath("$.stepping.feedStageIndex").value(4))
                .andExpect(jsonPath("$.stepping.balance.residual").value(0.0))
                .andExpect(jsonPath("$.stepping.stages[0].liquidComposition").exists())
                .andExpect(jsonPath("$.stepping.stages[0].section").value("RECTIFYING"))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        // 对外只吐三样东西
        assertThat(root.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("minimumRefluxRatio", "minimumStages", "stepping");
    }

    @Test
    void volatilityAtOrBelowOneReturns422CannotSeparate() throws Exception {
        String body = json("""
                {"feedFlow":1.0,"feedComposition":0.5,"distillateComposition":0.95,
                 "bottomsComposition":0.05,"feedThermalFactor":1.0,
                 "relativeVolatility":1.0,"refluxRatio":5.0}
                """);
        mockMvc.perform(post("/api/distillation/shortcut")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SEPARATION_IMPOSSIBLE"))
                .andExpect(jsonPath("$.message").value("无法分离：相对挥发度小于等于 1"));
    }

    @Test
    void refluxAtMinimumReturns422InsufficientReflux() throws Exception {
        String body = json("""
                {"feedFlow":1.0,"feedComposition":0.5,"distillateComposition":0.95,
                 "bottomsComposition":0.05,"feedThermalFactor":1.0,
                 "relativeVolatility":2.5,"refluxRatio":1.1}
                """);
        mockMvc.perform(post("/api/distillation/shortcut")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REFLUX_INSUFFICIENT"));
    }

    @Test
    void compositionOutOfRangeReturns400StructuredError() throws Exception {
        String body = json("""
                {"feedFlow":1.0,"feedComposition":1.5,"distillateComposition":0.95,
                 "bottomsComposition":0.05,"feedThermalFactor":1.0,
                 "relativeVolatility":2.5,"refluxRatio":5.0}
                """);
        mockMvc.perform(post("/api/distillation/shortcut")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fields.feedComposition").exists());
    }

    @Test
    void reversedCompositionOrderReturns400() throws Exception {
        String body = json("""
                {"feedFlow":1.0,"feedComposition":0.9,"distillateComposition":0.5,
                 "bottomsComposition":0.1,"feedThermalFactor":1.0,
                 "relativeVolatility":2.5,"refluxRatio":5.0}
                """);
        mockMvc.perform(post("/api/distillation/shortcut")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fields.compositionOrder").exists());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/distillation/shortcut")
                        .contentType(MediaType.APPLICATION_JSON).content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
