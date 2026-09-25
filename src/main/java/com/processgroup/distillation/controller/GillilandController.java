package com.processgroup.distillation.controller;

import com.processgroup.distillation.domain.GillilandDesignPoint;
import com.processgroup.distillation.domain.RefluxToStagesRequest;
import com.processgroup.distillation.domain.StagesToRefluxRequest;
import com.processgroup.distillation.service.gilliland.GillilandDesignService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gilliland 权衡曲线 HTTP 接口：在「回流比 ↔ 理论板数」曲线上双向定点。
 * 仅提供结构化 JSON，不提供任何界面。
 */
@RestController
@RequestMapping("/api/distillation/gilliland")
public class GillilandController {

    private final GillilandDesignService service;

    public GillilandController(GillilandDesignService service) {
        this.service = service;
    }

    /**
     * 给定实际回流比 R，返回曲线上对应的理论板数 N（含 Rmin/Nmin 两个锚点）。
     */
    @PostMapping("/stages")
    public ResponseEntity<GillilandDesignPoint> stages(@RequestBody RefluxToStagesRequest request) {
        return ResponseEntity.ok(service.stagesForReflux(request));
    }

    /**
     * 给定目标理论板数 N，在同一条曲线上反查所需的实际回流比 R。
     */
    @PostMapping("/reflux")
    public ResponseEntity<GillilandDesignPoint> reflux(@RequestBody StagesToRefluxRequest request) {
        return ResponseEntity.ok(service.refluxForStages(request));
    }
}
