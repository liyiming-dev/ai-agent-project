package com.yiming.aiagentproject.config;

import com.yiming.aiagentproject.model.dto.DatabaseConnectivityInfo;
import com.yiming.aiagentproject.service.DatabaseConnectivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.db-probe", name = "startup-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DatabaseConnectivityStartupLogger implements ApplicationRunner {

    private final DatabaseConnectivityService databaseConnectivityService;

    @Override
    public void run(ApplicationArguments args) {
        DatabaseConnectivityInfo info = databaseConnectivityService.checkConnection();
        if (info.isAvailable()) {
            log.info("Database connectivity check passed in {} ms, product={} {}",
                    info.getResponseTimeMs(),
                    info.getDatabaseProductName(),
                    info.getDatabaseProductVersion());
            return;
        }
        log.warn("Database connectivity check failed in {} ms, type={}, sqlState={}, rootCause={}",
                info.getResponseTimeMs(),
                info.getErrorType(),
                info.getSqlState(),
                info.getRootCause());
    }
}
