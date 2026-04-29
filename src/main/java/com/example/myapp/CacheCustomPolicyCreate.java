package com.example.myapp;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CachePolicyConfig;
import software.amazon.awssdk.services.cloudfront.model.CachePolicyCookiesConfig;
import software.amazon.awssdk.services.cloudfront.model.CachePolicyHeadersConfig;
import software.amazon.awssdk.services.cloudfront.model.CachePolicyQueryStringsConfig;
import software.amazon.awssdk.services.cloudfront.model.CachePolicyCookieBehavior;
import software.amazon.awssdk.services.cloudfront.model.CookieNames;
import software.amazon.awssdk.services.cloudfront.model.CreateCachePolicyRequest;
import software.amazon.awssdk.services.cloudfront.model.CreateCachePolicyResponse;
import software.amazon.awssdk.services.cloudfront.model.CachePolicyHeaderBehavior;
import software.amazon.awssdk.services.cloudfront.model.Headers;
import software.amazon.awssdk.services.cloudfront.model.ParametersInCacheKeyAndForwardedToOrigin;
import software.amazon.awssdk.services.cloudfront.model.CachePolicyQueryStringBehavior;
import software.amazon.awssdk.services.cloudfront.model.CloudFrontException;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;

/**
 * CloudFront 自定义缓存策略创建示例。
 *
 * 使用默认策略名称和常用缓存配置创建自定义缓存策略。
 * 代表性配置：Cookie 白名单 + Header 白名单 + QueryString 全部 + Gzip/Brotli 压缩。
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
 * mvn -q exec:java -Dexec.mainClass=com.example.myapp.CacheCustomPolicyCreate
 */
public class CacheCustomPolicyCreate {

    private static final String DEFAULT_POLICY_NAME = "ExampleCustomCachePolicy";

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

            ParametersInCacheKeyAndForwardedToOrigin parameters =
                ParametersInCacheKeyAndForwardedToOrigin.builder()
                    .cookiesConfig(CachePolicyCookiesConfig.builder()
                        .cookieBehavior(CachePolicyCookieBehavior.WHITELIST)
                        .cookies(CookieNames.builder()
                            .quantity(2)
                            .items("session_id", "language")
                            .build())
                        .build())
                    .headersConfig(CachePolicyHeadersConfig.builder()
                        .headerBehavior(CachePolicyHeaderBehavior.WHITELIST)
                        .headers(Headers.builder()
                            .quantity(2)
                            .items("Authorization", "CloudFront-Viewer-Country")
                            .build())
                        .build())
                    .queryStringsConfig(CachePolicyQueryStringsConfig.builder()
                        .queryStringBehavior(CachePolicyQueryStringBehavior.ALL)
                        .build())
                    .enableAcceptEncodingGzip(true)
                    .enableAcceptEncodingBrotli(true)
                    .build();

            CachePolicyConfig policyConfig = CachePolicyConfig.builder()
                    .name(DEFAULT_POLICY_NAME)
                    .comment("自定义缓存策略示例 - 常用配置")
                    .minTTL(0L)
                    .defaultTTL(86400L)
                    .maxTTL(31536000L)
                    .parametersInCacheKeyAndForwardedToOrigin(parameters)
                    .build();

            CreateCachePolicyRequest request = CreateCachePolicyRequest.builder()
                    .cachePolicyConfig(policyConfig)
                    .build();

            CreateCachePolicyResponse response = cloudFrontClient.createCachePolicy(request);

            System.out.println("创建成功");
            System.out.println("策略名称: " + DEFAULT_POLICY_NAME);
            System.out.println("策略 ID: " + nullSafe(response.cachePolicy().id()));
            System.out.println("MinTTL: 0s | DefaultTTL: 86400s(1天) | MaxTTL: 31536000s(365天)");
            System.out.println("Cookies: WHITELIST(session_id, language)");
            System.out.println("Headers: WHITELIST(Authorization, CloudFront-Viewer-Country)");
            System.out.println("QueryStrings: ALL");
            System.out.println("Gzip/Brotli: 已启用");

        } catch (CloudFrontException ex) {
            AwsErrorDetails details = ex.awsErrorDetails();
            if (details != null) {
                System.err.println("调用 CloudFront 创建缓存策略失败: " + nullSafe(details.errorMessage()));
                System.err.println("错误码: " + nullSafe(details.errorCode()));
            } else {
                System.err.println("调用 CloudFront 创建缓存策略失败: " + ex.getMessage());
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
