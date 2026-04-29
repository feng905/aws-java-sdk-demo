package com.example.myapp;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CachePolicyConfig;
import software.amazon.awssdk.services.cloudfront.model.CachePolicyCookiesConfig;
import software.amazon.awssdk.services.cloudfront.model.CachePolicyCookieBehavior;
import software.amazon.awssdk.services.cloudfront.model.CachePolicyHeadersConfig;
import software.amazon.awssdk.services.cloudfront.model.CachePolicyHeaderBehavior;
import software.amazon.awssdk.services.cloudfront.model.CachePolicyList;
import software.amazon.awssdk.services.cloudfront.model.CachePolicyQueryStringsConfig;
import software.amazon.awssdk.services.cloudfront.model.CachePolicyQueryStringBehavior;
import software.amazon.awssdk.services.cloudfront.model.CachePolicySummary;
import software.amazon.awssdk.services.cloudfront.model.GetCachePolicyRequest;
import software.amazon.awssdk.services.cloudfront.model.GetCachePolicyResponse;
import software.amazon.awssdk.services.cloudfront.model.ListCachePoliciesRequest;
import software.amazon.awssdk.services.cloudfront.model.ListCachePoliciesResponse;
import software.amazon.awssdk.services.cloudfront.model.ParametersInCacheKeyAndForwardedToOrigin;
import software.amazon.awssdk.services.cloudfront.model.QueryStringNames;
import software.amazon.awssdk.services.cloudfront.model.UpdateCachePolicyRequest;
import software.amazon.awssdk.services.cloudfront.model.UpdateCachePolicyResponse;
import software.amazon.awssdk.services.cloudfront.model.CloudFrontException;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;

/**
 * CloudFront 自定义缓存策略修改示例。
 *
 * 使用与创建示例相同的默认策略名称，将策略修改为另一组常用配置。
 * 修改后配置：Cookie 全部 + Header 无 + QueryString 白名单 + 较短 TTL。
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
 * mvn -q exec:java -Dexec.mainClass=com.example.myapp.CacheCustomPolicyUpdate
 *
 * 注意：请先运行 CacheCustomPolicyCreate 创建策略，再运行本示例修改。
 */
public class CacheCustomPolicyUpdate {

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

            String policyId = findPolicyIdByName(cloudFrontClient, DEFAULT_POLICY_NAME);
            if (policyId == null) {
                System.err.println("未找到策略: " + DEFAULT_POLICY_NAME + "。请先运行 CacheCustomPolicyCreate 创建策略。");
                return;
            }

            GetCachePolicyResponse getResponse = cloudFrontClient.getCachePolicy(
                GetCachePolicyRequest.builder().id(policyId).build());
            String eTag = getResponse.eTag();

            ParametersInCacheKeyAndForwardedToOrigin parameters =
                ParametersInCacheKeyAndForwardedToOrigin.builder()
                    .cookiesConfig(CachePolicyCookiesConfig.builder()
                        .cookieBehavior(CachePolicyCookieBehavior.ALL)
                        .build())
                    .headersConfig(CachePolicyHeadersConfig.builder()
                        .headerBehavior(CachePolicyHeaderBehavior.NONE)
                        .build())
                    .queryStringsConfig(CachePolicyQueryStringsConfig.builder()
                        .queryStringBehavior(CachePolicyQueryStringBehavior.WHITELIST)
                        .queryStrings(QueryStringNames.builder()
                            .quantity(3)
                            .items("page", "sort", "q")
                            .build())
                        .build())
                    .enableAcceptEncodingGzip(true)
                    .enableAcceptEncodingBrotli(true)
                    .build();

            CachePolicyConfig newConfig = CachePolicyConfig.builder()
                    .name(DEFAULT_POLICY_NAME)
                    .comment("自定义缓存策略示例 - 常用配置（已修改）")
                    .minTTL(0L)
                    .defaultTTL(3600L)
                    .maxTTL(86400L)
                    .parametersInCacheKeyAndForwardedToOrigin(parameters)
                    .build();

            UpdateCachePolicyRequest updateRequest = UpdateCachePolicyRequest.builder()
                    .cachePolicyConfig(newConfig)
                    .id(policyId)
                    .ifMatch(eTag)
                    .build();

            UpdateCachePolicyResponse response = cloudFrontClient.updateCachePolicy(updateRequest);

            System.out.println("修改成功");
            System.out.println("策略名称: " + DEFAULT_POLICY_NAME);
            System.out.println("策略 ID: " + nullSafe(response.cachePolicy().id()));
            System.out.println("新配置:");
            System.out.println("  MinTTL: 0s | DefaultTTL: 3600s(1小时) | MaxTTL: 86400s(1天)");
            System.out.println("  Cookies: ALL");
            System.out.println("  Headers: NONE");
            System.out.println("  QueryStrings: WHITELIST(page, sort, q)");
            System.out.println("  Gzip/Brotli: 已启用");

        } catch (CloudFrontException ex) {
            AwsErrorDetails details = ex.awsErrorDetails();
            if (details != null) {
                System.err.println("调用 CloudFront 修改缓存策略失败: " + nullSafe(details.errorMessage()));
                System.err.println("错误码: " + nullSafe(details.errorCode()));
            } else {
                System.err.println("调用 CloudFront 修改缓存策略失败: " + ex.getMessage());
            }
        } catch (Exception ex) {
            System.err.println("程序执行失败(" + ex.getClass().getSimpleName() + "): " + ex.getMessage());
        }
    }

    private static String findPolicyIdByName(CloudFrontClient client, String policyName) {
        String nextMarker = null;
        do {
            ListCachePoliciesRequest request = ListCachePoliciesRequest.builder()
                    .type("custom")
                    .marker(nextMarker)
                    .build();
            ListCachePoliciesResponse response = client.listCachePolicies(request);
            CachePolicyList policyList = response.cachePolicyList();

            if (policyList.items() != null) {
                for (CachePolicySummary summary : policyList.items()) {
                    if (summary.cachePolicy() != null
                            && summary.cachePolicy().cachePolicyConfig() != null
                            && policyName.equals(summary.cachePolicy().cachePolicyConfig().name())) {
                        return summary.cachePolicy().id();
                    }
                }
            }
            nextMarker = policyList.nextMarker();
        } while (nextMarker != null && !nextMarker.isEmpty());
        return null;
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
