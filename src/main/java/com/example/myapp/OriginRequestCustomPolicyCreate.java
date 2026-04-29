package com.example.myapp;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CreateOriginRequestPolicyRequest;
import software.amazon.awssdk.services.cloudfront.model.CreateOriginRequestPolicyResponse;
import software.amazon.awssdk.services.cloudfront.model.Headers;
import software.amazon.awssdk.services.cloudfront.model.OriginRequestPolicyConfig;
import software.amazon.awssdk.services.cloudfront.model.OriginRequestPolicyCookieBehavior;
import software.amazon.awssdk.services.cloudfront.model.OriginRequestPolicyCookiesConfig;
import software.amazon.awssdk.services.cloudfront.model.OriginRequestPolicyHeaderBehavior;
import software.amazon.awssdk.services.cloudfront.model.OriginRequestPolicyHeadersConfig;
import software.amazon.awssdk.services.cloudfront.model.OriginRequestPolicyQueryStringBehavior;
import software.amazon.awssdk.services.cloudfront.model.OriginRequestPolicyQueryStringsConfig;
import software.amazon.awssdk.services.cloudfront.model.CloudFrontException;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;

/**
 * CloudFront 自定义源请求策略创建示例。
 *
 * 使用默认策略名称和常用配置创建源请求策略。
 * 代表性配置：Cookie 无 + Header 白名单（Host, Authorization）+ QueryString 全部。
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
 * mvn -q exec:java -Dexec.mainClass=com.example.myapp.OriginRequestCustomPolicyCreate
 */
public class OriginRequestCustomPolicyCreate {

    private static final String DEFAULT_POLICY_NAME = "ExampleCustomOriginRequestPolicy";

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

            OriginRequestPolicyConfig policyConfig = OriginRequestPolicyConfig.builder()
                    .name(DEFAULT_POLICY_NAME)
                    .comment("自定义源请求策略示例 - 常用配置")
                    .cookiesConfig(OriginRequestPolicyCookiesConfig.builder()
                        .cookieBehavior(OriginRequestPolicyCookieBehavior.NONE)
                        .build())
                    .headersConfig(OriginRequestPolicyHeadersConfig.builder()
                        .headerBehavior(OriginRequestPolicyHeaderBehavior.WHITELIST)
                        .headers(Headers.builder()
                            .quantity(2)
                            .items("Host", "Authorization")
                            .build())
                        .build())
                    .queryStringsConfig(OriginRequestPolicyQueryStringsConfig.builder()
                        .queryStringBehavior(OriginRequestPolicyQueryStringBehavior.ALL)
                        .build())
                    .build();

            CreateOriginRequestPolicyRequest request = CreateOriginRequestPolicyRequest.builder()
                    .originRequestPolicyConfig(policyConfig)
                    .build();

            CreateOriginRequestPolicyResponse response = cloudFrontClient.createOriginRequestPolicy(request);

            System.out.println("创建成功");
            System.out.println("策略名称: " + DEFAULT_POLICY_NAME);
            System.out.println("策略 ID: " + nullSafe(response.originRequestPolicy().id()));
            System.out.println("Cookies: NONE");
            System.out.println("Headers: WHITELIST(Host, Authorization)");
            System.out.println("QueryStrings: ALL");

        } catch (CloudFrontException ex) {
            AwsErrorDetails details = ex.awsErrorDetails();
            if (details != null) {
                System.err.println("调用 CloudFront 创建源请求策略失败: " + nullSafe(details.errorMessage()));
                System.err.println("错误码: " + nullSafe(details.errorCode()));
            } else {
                System.err.println("调用 CloudFront 创建源请求策略失败: " + ex.getMessage());
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
