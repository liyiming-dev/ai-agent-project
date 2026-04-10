package com.yiming.aiagentproject.service;

import cn.hutool.core.util.StrUtil;
import com.yiming.aiagentproject.config.DataSourceProbeProperties;
import com.yiming.aiagentproject.model.dto.DatabaseConnectivityInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

@Service
@RequiredArgsConstructor
public class DatabaseConnectivityService {

    private static final String CONNECT_TIMEOUT_MS = "5000";
    private static final String SOCKET_TIMEOUT_MS = "5000";

    private final DataSourceProbeProperties dataSourceProbeProperties;

    public DatabaseConnectivityInfo checkConnection() {
        DatabaseConnectivityInfo info = new DatabaseConnectivityInfo();
        info.setJdbcUrl(dataSourceProbeProperties.getUrl());

        long startTime = System.currentTimeMillis();
        try {
            loadDriverIfNecessary();
            try (Connection connection = DriverManager.getConnection(dataSourceProbeProperties.getUrl(), buildProperties())) {
                DatabaseMetaData metaData = connection.getMetaData();
                info.setAvailable(true);
                info.setMessage("database connection ok");
                info.setDatabaseProductName(metaData.getDatabaseProductName());
                info.setDatabaseProductVersion(metaData.getDatabaseProductVersion());
            }
        } catch (Exception e) {
            info.setAvailable(false);
            info.setMessage("database connection failed");
            info.setErrorType(e.getClass().getSimpleName());
            info.setRootCause(getRootCauseMessage(e));
            if (e instanceof SQLException sqlException) {
                info.setSqlState(sqlException.getSQLState());
                info.setVendorCode(sqlException.getErrorCode());
            }
        }
        info.setResponseTimeMs(System.currentTimeMillis() - startTime);
        return info;
    }

    private void loadDriverIfNecessary() throws ClassNotFoundException {
        if (StrUtil.isNotBlank(dataSourceProbeProperties.getDriverClassName())) {
            Class.forName(dataSourceProbeProperties.getDriverClassName());
        }
    }

    private Properties buildProperties() {
        Properties properties = new Properties();
        if (StrUtil.isNotBlank(dataSourceProbeProperties.getUsername())) {
            properties.setProperty("user", dataSourceProbeProperties.getUsername());
        }
        if (StrUtil.isNotBlank(dataSourceProbeProperties.getPassword())) {
            properties.setProperty("password", dataSourceProbeProperties.getPassword());
        }
        properties.setProperty("connectTimeout", CONNECT_TIMEOUT_MS);
        properties.setProperty("socketTimeout", SOCKET_TIMEOUT_MS);
        return properties;
    }

    private String getRootCauseMessage(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return rootCause.getMessage();
    }
}
