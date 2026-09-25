package com.processgroup.distillation.controller;

import com.processgroup.distillation.domain.GillilandPointResponse;
import com.processgroup.distillation.domain.GillilandRefluxRequest;
import com.processgroup.distillation.domain.GillilandStagesRequest;
import com.processgroup.distillation.service.GillilandTradeoffService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gilliland 回流比—理论板数权衡曲线 HTTP 接口。
 *
 * <p>与 {@link DistillationController} 一样仅提供结构化 JSON，不提供任何界面。
 * Nmin/Rmin 由现有 Fenske/Underwood 逻辑在服务内部取得，调用方只需给分离任务列。
 */
@RestController
@RequestMapping("/api/distillation/gilliland")
public class GillilandController {

    private final GillilandTradeoffService service;

    public GillilandController(GillilandTradeoffService service) {
        this.service = service;
    }

    /**
     * 路径一：给定实际回流比 R，求所需理论板数 N。
     */
    @PostMapping("/stages-for-reflux")
    public ResponseEntity<GillilandPointResponse> stagesForReflux(
            @RequestBody GillilandRefluxRequest request) {
        return ResponseEntity.ok(service.stagesForReflux(request));
    }

    /**
     * 路径二：给定可接受的理论板数 N，反求所需实际回流比 R。
     */
    @PostMapping("/reflux-for-stages")
    public ResponseEntity<GillilandPointResponse> refluxForStages(
            @RequestBody GillilandStagesRequest request) {
        return ResponseEntity.ok(service.refluxForStages(request));
    }
}
