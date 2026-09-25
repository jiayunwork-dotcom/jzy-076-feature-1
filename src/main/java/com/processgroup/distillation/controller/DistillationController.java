package com.processgroup.distillation.controller;

import com.processgroup.distillation.domain.ShortcutRequest;
import com.processgroup.distillation.domain.ShortcutResponse;
import com.processgroup.distillation.service.DistillationShortcutService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 精馏塔简捷法核算 HTTP 接口。仅提供结构化 JSON，不提供任何界面。
 */
@RestController
@RequestMapping("/api/distillation")
public class DistillationController {

    private final DistillationShortcutService service;

    public DistillationController(DistillationShortcutService service) {
        this.service = service;
    }

    /**
     * 一次调用返回：最小回流比、最少理论板数、逐板阶梯结果。
     */
    @PostMapping("/shortcut")
    public ResponseEntity<ShortcutResponse> shortcut(@RequestBody ShortcutRequest request) {
        return ResponseEntity.ok(service.compute(request));
    }
}
