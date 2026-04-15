package com.yiming.aiagentproject.constant;

public interface AppConstant {
    // 优质应用的优先级，数值越大优先级越高
    Integer GOOD_APP_PRIORITY = 99;
    Integer DEFAULT_APP_PRIORITY = 0;
    /**
     * 应用生成目录
     */
    String CODE_OUTPUT_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";

    /**
     * 应用部署目录
     */
    String CODE_DEPLOY_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_deploy";

    /**
     * 应用部署域名
     */
    String CODE_DEPLOY_HOST = "http://localhost:8123/api/static";

}
