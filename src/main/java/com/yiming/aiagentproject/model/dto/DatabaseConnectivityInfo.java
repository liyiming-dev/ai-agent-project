package com.yiming.aiagentproject.model.dto;

import lombok.Data;

@Data
public class DatabaseConnectivityInfo {

    private boolean available;

    private String message;

    private String jdbcUrl;

    private Long responseTimeMs;

    private String databaseProductName;

    private String databaseProductVersion;

    private String errorType;

    private String sqlState;

    private Integer vendorCode;

    private String rootCause;
}
