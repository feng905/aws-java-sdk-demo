package com.example.myapp;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CreateResponseHeadersPolicyRequest;
import software.amazon.awssdk.services.cloudfront.model.CreateResponseHeadersPolicyResponse;
import software.amazon.awssdk.services.cloudfront.model.FrameOptionsList;
import software.amazon.awssdk.services.cloudfront.model.ReferrerPolicyList;
import software.amazon.awssdk.services.cloudfront.model.ResponseHeadersPolicyConfig;
import software.amazon.awssdk.services.cloudfront.model.ResponseHeadersPolicyContentTypeOptions;
import software.amazon.awssdk.services.cloudfront.model.ResponseHeadersPolicyCustomHeader;
import software.amazon.awssdk.services.cloudfront.model.ResponseHeadersPolicyCustomHeadersConfig;
import software.amazon.awssdk.services.cloudfront.model.ResponseHeadersPolicyFrameOptions;
import software.amazon.awssdk.services.cloudfront.model.ResponseHeadersPolicyReferrerPolicy;
import software.amazon.awssdk.services.cloudfront.model.ResponseHeadersPolicySecurityHeadersConfig;
import software.amazon.awssdk.services.cloudfront.model.ResponseHeadersPolicyStrictTransportSecurity;
import software.amazon.awssdk.services.cloudfront.model.ResponseHeadersPolicyXSSProtection;
import software.amazon.awssdk.services.cloudfront.model.CloudFrontException;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;

/**
 * CloudFront 自定义响应标头策略创建示例。
 *
 * 使用默认策略名称和常用安全标头配置创建响应标头策略。
 * 代表性配置：HSTS + X-Frame-Options + Referrer-Policy + X-Content-Type-Options
 *             + X-XSS-Protection + 自定义标头。
 *
 * 参数优先级：命令行参数 > 环境变量 > 代码默认值（空）。
 *
 * 命令行参数：
 * 1) accessKeyId
 * 2) secretAccessKey
 * 3) region（可选，默认 us-east-1）
 *
 * 环境变量：
 * - AWS_ACCESS_KEY_ID
 * - AWS_SECRET_ACCESS_KEY
 * - AWS_REGION
 *
 * 运行示例（推荐：环境变量方式）：
 * export AWS_ACCESS_KEY_ID="<YOUR_AK>"
 * export AWS_SECRET_ACCESS_KEY="<YOUR_SK>"
 * mvn -q exec:java -Dexec.mainClass=com.example.myapp.ResponseHeaderCustomPolicyCreate
 */
public class ResponseHeaderCustomPolicyCreate {

    private static final String DEFAULT_POLICY_NAME = "ExampleCustomResponseHeadersPolicy";

    private static final String DEFAULT_ACCESS_KEY_ID = "";
    private static final String DEFAULT_SECRET_ACCESS_KEY = "";
    private static final Region DEFAULT_REGION = Region.US_EAST_1;

    private static final String ENV_AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
    private static final String ENV_AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
    private static final String ENV_AWS_REGION = "AWS_REGION";

    public static void main(String[] args) {
        String accessKeyId = resolveValue(args, 0, ENV_AWS_ACCESS_KEY_ID, DEFAULT_ACCESS_KEY_ID);
        String secretAccessKey = resolveValue(args, 1, ENV_AWS_SECRET_ACCESS_KEY, DEFAULT_SECRET_ACCESS_KEY);
        String regionArg = resolveValue(args, 2, ENV_AWS_REGION, "");

        if (accessKeyId.isEmpty() || secretAccessKey.isEmpty()) {
            System.err.println("AK/SK 未提供。请通过参数或环境变量提供：AWS_ACCESS_KEY_ID、AWS_SECRET_ACCESS_KEY");
            return;
        }

        Region region = resolveRegion(regionArg);
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);

        try (CloudFrontClient cloudFrontClient = CloudFrontClient.builder()
                .region(region)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build()) {

            ResponseHeadersPolicySecurityHeadersConfig securityHeaders =
                ResponseHeadersPolicySecurityHeadersConfig.builder()
                    .strictTransportSecurity(ResponseHeadersPolicyStrictTransportSecurity.builder()
                        .override(true)
                        .accessControlMaxAgeSec(31536000)
                        .includeSubdomains(true)
                        .preload(true)
                        .build())
                    .frameOptions(ResponseHeadersPolicyFrameOptions.builder()
                        .override(true)
                        .frameOption(FrameOptionsList.SAMEORIGIN)
                        .build())
                    .referrerPolicy(ResponseHeadersPolicyReferrerPolicy.builder()
                        .override(true)
                        .referrerPolicy(ReferrerPolicyList.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                        .build())
                    .contentTypeOptions(ResponseHeadersPolicyContentTypeOptions.builder()
                        .override(true)
                        .build())
                    .xssProtection(ResponseHeadersPolicyXSSProtection.builder()
                        .override(true)
                        .protection(true)
                        .modeBlock(true)
                        .reportUri(null)
                        .build())
                    .build();

            ResponseHeadersPolicyCustomHeadersConfig customHeaders =
                ResponseHeadersPolicyCustomHeadersConfig.builder()
                    .quantity(1)
                    .items(ResponseHeadersPolicyCustomHeader.builder()
                        .header("X-Request-ID")
                        .value("{{uuid}}")
                        .override(true)
                        .build())
                    .build();

            ResponseHeadersPolicyConfig policyConfig = ResponseHeadersPolicyConfig.builder()
                    .name(DEFAULT_POLICY_NAME)
                    .comment("自定义响应标头策略示例 - 常用安全标头配置")
                    .securityHeadersConfig(securityHeaders)
                    .customHeadersConfig(customHeaders)
                    .build();

            CreateResponseHeadersPolicyRequest request = CreateResponseHeadersPolicyRequest.builder()
                    .responseHeadersPolicyConfig(policyConfig)
                    .build();

            CreateResponseHeadersPolicyResponse response = cloudFrontClient.createResponseHeadersPolicy(request);

            System.out.println("创建成功");
            System.out.println("策略名称: " + DEFAULT_POLICY_NAME);
            System.out.println("策略 ID: " + nullSafe(response.responseHeadersPolicy().id()));
            System.out.println("安全标头:");
            System.out.println("  HSTS: max-age=31536000; includeSubDomains; preload");
            System.out.println("  X-Frame-Options: SAMEORIGIN");
            System.out.println("  Referrer-Policy: strict-origin-when-cross-origin");
            System.out.println("  X-Content-Type-Options: nosniff");
            System.out.println("  X-XSS-Protection: 1; mode=block");
            System.out.println("自定义标头: X-Request-ID");

        } catch (CloudFrontException ex) {
            AwsErrorDetails details = ex.awsErrorDetails();
            if (details != null) {
                System.err.println("调用 CloudFront 创建响应标头策略失败: " + nullSafe(details.errorMessage()));
                System.err.println("错误码: " + nullSafe(details.errorCode()));
            } else {
                System.err.println("调用 CloudFront 创建响应标头策略失败: " + ex.getMessage());
            }
        } catch (Exception ex) {
            System.err.println("程序执行失败(" + ex.getClass().getSimpleName() + "): " + ex.getMessage());
        }
    }

    private static Region resolveRegion(String regionArg) {
        if (regionArg == null || regionArg.trim().isEmpty()) {
            return DEFAULT_REGION;
        }
        return Region.of(regionArg.trim());
    }

    private static String resolveValue(String[] args, int index, String envKey, String defaultValue) {
        if (args != null && args.length > index && args[index] != null && !args[index].trim().isEmpty()) {
            return args[index].trim();
        }
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.trim().isEmpty()) {
            return envValue.trim();
        }
        return defaultValue;
    }

    private static String nullSafe(String value) {
        return value == null ? "-" : value;
    }

}
