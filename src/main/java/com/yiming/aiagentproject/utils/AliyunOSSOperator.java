package com.yiming.aiagentproject.utils;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.auth.EnvironmentVariableCredentialsProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.yiming.aiagentproject.exception.BusinessException;
import com.yiming.aiagentproject.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
@Slf4j
public class AliyunOSSOperator {

    @Autowired
    private AliyunOssProperties aliyunOssProperties;

    /**
     * 上传文件到阿里云 OSS
     *
     * @param file      本地文件
     * @param objectKey OSS Object 完整路径（如 screenshots/2025/07/31/xxx.jpg，不能以 / 开头）
     * @return 可访问的文件 URL
     */
    public String upload(File file, String objectKey) {
        String endpoint = aliyunOssProperties.getEndpoint();
        String bucketName = aliyunOssProperties.getBucketName();
        String region = aliyunOssProperties.getRegion();

        // OSS Object 路径不能以 / 开头
        String normalizedKey = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;

        OSS ossClient = null;
        try {
            // 从环境变量 OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET 获取访问凭证
            EnvironmentVariableCredentialsProvider credentialsProvider =
                    CredentialsProviderFactory.newEnvironmentVariableCredentialsProvider();

            ClientBuilderConfiguration clientBuilderConfiguration = new ClientBuilderConfiguration();
            clientBuilderConfiguration.setSignatureVersion(SignVersion.V4);

            ossClient = OSSClientBuilder.create()
                    .endpoint(endpoint)
                    .credentialsProvider(credentialsProvider)
                    .clientConfiguration(clientBuilderConfiguration)
                    .region(region)
                    .build();

            ossClient.putObject(bucketName, normalizedKey, file);
        } catch (Exception e) {
            log.error("文件上传 OSS 失败, objectKey={}", normalizedKey, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件上传 OSS 失败");
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }

        // 拼接访问 URL，例如 https://{bucket}.oss-cn-beijing.aliyuncs.com/{objectKey}
        String[] endpointParts = endpoint.split("//");
        return endpointParts[0] + "//" + bucketName + "." + endpointParts[1] + "/" + normalizedKey;
    }

}
