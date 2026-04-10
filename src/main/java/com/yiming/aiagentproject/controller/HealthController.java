package com.yiming.aiagentproject.controller;

import com.yiming.aiagentproject.common.BaseResponse;
import com.yiming.aiagentproject.common.ResultUtils;
import com.yiming.aiagentproject.model.dto.DatabaseConnectivityInfo;
import com.yiming.aiagentproject.service.DatabaseConnectivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/health")
public class HealthController {

    private final DatabaseConnectivityService databaseConnectivityService;

    @GetMapping("/")
    public BaseResponse<String> healthCheck() {

        return ResultUtils.success("ok");
    }

    @GetMapping("/db")
    public BaseResponse<DatabaseConnectivityInfo> databaseHealthCheck() {
        return ResultUtils.success(databaseConnectivityService.checkConnection());
    }
}

