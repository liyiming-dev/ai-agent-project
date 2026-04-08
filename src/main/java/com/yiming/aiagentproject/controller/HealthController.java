package com.yiming.aiagentproject.controller;

import com.yiming.aiagentproject.common.BaseResponse;
import com.yiming.aiagentproject.common.ResultUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping("/")
    public BaseResponse<String> healthCheck() {

        return ResultUtils.success("ok");
    }
}

