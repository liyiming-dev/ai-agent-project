package com.yiming.aiagentproject.service;

import com.yiming.aiagentproject.config.DataSourceProbeProperties;
import com.yiming.aiagentproject.model.dto.DatabaseConnectivityInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DatabaseConnectivityServiceTest {

    @Test
    void shouldReturnFailureInfoWhenDatabaseIsUnavailable() {
        DataSourceProbeProperties properties = new DataSourceProbeProperties();
        properties.setUrl("jdbc:mysql://127.0.0.1:1/test");
        properties.setUsername("root");
        properties.setPassword("1234");
        properties.setDriverClassName("com.mysql.cj.jdbc.Driver");

        DatabaseConnectivityService service = new DatabaseConnectivityService(properties);

        DatabaseConnectivityInfo result = service.checkConnection();

        assertFalse(result.isAvailable());
        assertNotNull(result.getErrorType());
        assertNotNull(result.getRootCause());
    }
}
