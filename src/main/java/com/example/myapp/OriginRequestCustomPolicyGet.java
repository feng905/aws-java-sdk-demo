package com.example.myapp;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.ListOriginRequestPoliciesRequest;
import software.amazon.awssdk.services.cloudfront.model.ListOriginRequestPoliciesResponse;
import software.amazon.awssdk.services.cloudfront.model.OriginRequestPolicyConfig;
import software.amazon.awssdk.services.cloudfront.model.OriginRequestPolicyList;
import software.amazon.awssdk.services.cloudfront.model.OriginRequestPolicySummary;
import software.amazon.awssdk.services.cloudfront.model.OriginRequestPolicyType;

import software.amazon.awssdk.services.cloudfront.model.CloudFrontException;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;

import java.util.ArrayList;
import java.util.List;

/**
 * CloudFront 自定义源请求策略查询示例。
 *
 * 列出所有自定义源请求策略，展示策略名称、ID 及缓存键配置。
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
 * mvn -q exec:java -Dexec.mainClass=com.example.myapp.OriginRequestCustomPolicyGet
 */
public class OriginRequestCustomPolicyGet {

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

            List<OriginRequestPolicySummary> policies = listAllCustomPolicies(cloudFrontClient);

            System.out.println("=== CloudFront 自定义源请求策略列表 ===");
            System.out.println("策略总数: " + policies.size());

            for (int i = 0; i < policies.size(); i++) {
                OriginRequestPolicySummary summary = policies.get(i);
                OriginRequestPolicyConfig config = summary.originRequestPolicy().originRequestPolicyConfig();

                System.out.println("----------------------------------------");
                System.out.println("序号: " + (i + 1));
                System.out.println("策略名称: " + nullSafe(config.name()));
                System.out.println("策略 ID: " + nullSafe(summary.originRequestPolicy().id()));
                System.out.println("类型: " + nullSafe(summary.typeAsString()));

                System.out.println("Cookies: " + nullSafe(config.cookiesConfig().cookieBehaviorAsString()));
                System.out.println("Headers: " + nullSafe(config.headersConfig().headerBehaviorAsString()));
                System.out.println("QueryStrings: " + nullSafe(config.queryStringsConfig().queryStringBehaviorAsString()));
            }

        } catch (CloudFrontException ex) {
            AwsErrorDetails details = ex.awsErrorDetails();
            if (details != null) {
                System.err.println("调用 CloudFront 查询源请求策略失败: " + nullSafe(details.errorMessage()));
                System.err.println("错误码: " + nullSafe(details.errorCode()));
            } else {
                System.err.println("调用 CloudFront 查询源请求策略失败: " + ex.getMessage());
            }
        } catch (Exception ex) {
            System.err.println("程序执行失败(" + ex.getClass().getSimpleName() + "): " + ex.getMessage());
        }
    }

    private static List<OriginRequestPolicySummary> listAllCustomPolicies(CloudFrontClient client) {
        List<OriginRequestPolicySummary> allPolicies = new ArrayList<>();
        String nextMarker = null;

        do {
            ListOriginRequestPoliciesRequest request = ListOriginRequestPoliciesRequest.builder()
                    .type(OriginRequestPolicyType.CUSTOM)
                    .marker(nextMarker)
                    .build();
            ListOriginRequestPoliciesResponse response = client.listOriginRequestPolicies(request);
            OriginRequestPolicyList policyList = response.originRequestPolicyList();

            if (policyList.items() != null) {
                allPolicies.addAll(policyList.items());
            }
            nextMarker = policyList.nextMarker();
        } while (nextMarker != null && !nextMarker.isEmpty());

        return allPolicies;
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
