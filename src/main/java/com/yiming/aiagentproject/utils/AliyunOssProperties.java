package com.yiming.aiagentproject.utils;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Data
@ConfigurationProperties(prefix = "aliyun.oss") // 直接将yml中该路径下的值复制到这个实体类里面
public class AliyunOssProperties {
    private String endpoint;
    private String bucketName;
    private String region;
    // 属性名称要和 yml中一一对应!

}