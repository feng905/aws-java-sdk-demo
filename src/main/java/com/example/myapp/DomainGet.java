package com.example.myapp;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.*;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CloudFront Distribution 查询示例。
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
 * export AWS_REGION="us-east-1"
 * mvn -q exec:java -Dexec.mainClass=com.example.myapp.DomainGet
 *
 * 运行示例（命令行参数方式）：
 * mvn -q exec:java -Dexec.mainClass=com.example.myapp.DomainGet -Dexec.args="<AK> <SK> us-east-1"
 */
public class DomainGet {

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

            List<DistributionSummary> distributions = listAllDistributions(cloudFrontClient);
            Map<String, CachePolicyConfig> cachePolicyCache = new HashMap<>();

            System.out.println("=== CloudFront Distribution 列表（区域: " + region.id() + "）===");
            System.out.println("Distribution 总数: " + distributions.size());

            for (int i = 0; i < distributions.size(); i++) {
                DistributionSummary dist = distributions.get(i);
                System.out.println("----------------------------------------");
                System.out.println("序号: " + (i + 1));
                System.out.println("    Distribution ID: " + nullSafe(dist.id()));
                System.out.println("    Distribution 域名: " + nullSafe(dist.domainName()));
                System.out.println("    自定义域名: " + nullSafe(aliasesToString(dist.aliases())));
                System.out.println("    状态: " + nullSafe(dist.status()));
                System.out.println("    最后修改时间: " + nullSafeInstant(dist.lastModifiedTime()));
                System.out.println("    证书 ARN: " + nullSafe(dist.viewerCertificate().acmCertificateArn()));
                System.out.println("    源站列表: " + nullSafe(originsToString(dist.origins())));

                DefaultCacheBehavior defaultBehavior = dist.defaultCacheBehavior();
                if (defaultBehavior != null) {
                    System.out.println("    [默认缓存行为]");
                    System.out.println("      优先级: Default");
                    System.out.println("      路径模式: * (默认)");
                    System.out.println("      源站 ID: " + nullSafe(defaultBehavior.targetOriginId()));
                    printCachePolicyInfo(cloudFrontClient, defaultBehavior.cachePolicyId(), cachePolicyCache);
                }

                CacheBehaviors cacheBehaviors = dist.cacheBehaviors();
                if (cacheBehaviors != null && cacheBehaviors.items() != null && !cacheBehaviors.items().isEmpty()) {
                    List<CacheBehavior> behaviorItems = cacheBehaviors.items();
                    for (int j = 0; j < behaviorItems.size(); j++) {
                        CacheBehavior behavior = behaviorItems.get(j);
                        System.out.println("    [缓存行为 " + (j + 1) + "]");
                        System.out.println("      优先级: " + (j + 1));
                        System.out.println("      路径模式: " + nullSafe(behavior.pathPattern()));
                        System.out.println("      源站 ID: " + nullSafe(behavior.targetOriginId()));
                        printCachePolicyInfo(cloudFrontClient, behavior.cachePolicyId(), cachePolicyCache);
                    }
                }
            }

        } catch (CloudFrontException ex) {
            AwsErrorDetails details = ex.awsErrorDetails();
            if (details != null) {
                System.err.println("调用 CloudFront 接口失败: " + nullSafe(details.errorMessage()));
                System.err.println("错误码: " + nullSafe(details.errorCode()));
            } else {
                System.err.println("调用 CloudFront 接口失败: " + ex.getMessage());
            }
        } catch (Exception ex) {
            System.err.println("程序执行失败: " + ex.getMessage());
        }
    }

    private static Region resolveRegion(String regionArg) {
        if (regionArg == null || regionArg.trim().isEmpty()) {
            return DEFAULT_REGION;
        }
        return Region.of(regionArg.trim());
    }

    private static String resolveValue(String[] args, int index, String envKey, String defaultValue) {
        if (args == null || args.length <= index || args[index] == null || args[index].trim().isEmpty()) {
            String envValue = System.getenv(envKey);
            if (envValue == null || envValue.trim().isEmpty()) {
                return defaultValue;
            }
            return envValue.trim();
        }
        return args[index].trim();
    }

    /**
     * 分页获取所有 CloudFront Distribution。
     */
    private static List<DistributionSummary> listAllDistributions(CloudFrontClient cloudFrontClient) {
        List<DistributionSummary> allDistributions = new ArrayList<>();
        String marker = null;

        do {
            ListDistributionsRequest request = ListDistributionsRequest.builder()
                    .maxItems("100")
                    .marker(marker)
                    .build();

            ListDistributionsResponse response = cloudFrontClient.listDistributions(request);
            allDistributions.addAll(response.distributionList().items());
            marker = response.distributionList().marker();

        } while (marker != null && !marker.isEmpty());

        return allDistributions;
    }

    private static String originsToString(Origins origins) {
        if (origins == null || origins.items() == null || origins.items().isEmpty()) {
            return "-";
        }
        List<String> parts = new ArrayList<>();
        for (Origin origin : origins.items()) {
            parts.add(nullSafe(origin.domainName()) + " (id=" + nullSafe(origin.id()) + ")");
        }
        return String.join(", ", parts);
    }

    private static String aliasesToString(Aliases aliases) {
        if (aliases == null || aliases.items() == null || aliases.items().isEmpty()) {
            return "-";
        }
        return String.join(", ", aliases.items());
    }

    private static String nullSafe(String value) {
        return value == null ? "-" : value;
    }

    private static void printCachePolicyInfo(CloudFrontClient client, String policyId,
            Map<String, CachePolicyConfig> cache) {
        if (policyId == null || policyId.isEmpty()) {
            System.out.println("      缓存策略: 未配置");
            return;
        }

        CachePolicyConfig config = cache.get(policyId);
        if (config == null) {
            try {
                config = client.getCachePolicy(GetCachePolicyRequest.builder().id(policyId).build())
                        .cachePolicy().cachePolicyConfig();
                cache.put(policyId, config);
            } catch (Exception e) {
                System.out.println("      缓存策略 ID: " + policyId + " (获取详情失败)");
                return;
            }
        }

        System.out.println("      缓存策略名: " + nullSafe(config.name()));
        System.out.println("      缓存策略详情:");
        System.out.println("        最小 TTL: " + config.minTTL());
        System.out.println("        默认 TTL: " + config.defaultTTL());
        System.out.println("        最大 TTL: " + config.maxTTL());

        ParametersInCacheKeyAndForwardedToOrigin params = config.parametersInCacheKeyAndForwardedToOrigin();
        if (params != null) {
            System.out.println("        Gzip 压缩: " + params.enableAcceptEncodingGzip());
            System.out.println("        Brotli 压缩: " + params.enableAcceptEncodingBrotli());

            CachePolicyHeadersConfig headersConfig = params.headersConfig();
            if (headersConfig != null) {
                System.out.println("        Headers 行为: " + headersConfig.headerBehavior());
                if (headersConfig.headers() != null && headersConfig.headers().items() != null
                        && !headersConfig.headers().items().isEmpty()) {
                    System.out.println("        Headers 白名单: " + String.join(", ", headersConfig.headers().items()));
                }
            }

            CachePolicyCookiesConfig cookiesConfig = params.cookiesConfig();
            if (cookiesConfig != null) {
                System.out.println("        Cookies 行为: " + cookiesConfig.cookieBehavior());
                if (cookiesConfig.cookies() != null && cookiesConfig.cookies().items() != null
                        && !cookiesConfig.cookies().items().isEmpty()) {
                    System.out.println("        Cookies 白名单: " + String.join(", ", cookiesConfig.cookies().items()));
                }
            }

            CachePolicyQueryStringsConfig queryStringsConfig = params.queryStringsConfig();
            if (queryStringsConfig != null) {
                System.out.println("        Query Strings 行为: " + queryStringsConfig.queryStringBehavior());
                if (queryStringsConfig.queryStrings() != null && queryStringsConfig.queryStrings().items() != null
                        && !queryStringsConfig.queryStrings().items().isEmpty()) {
                    System.out.println("        Query Strings 白名单: "
                            + String.join(", ", queryStringsConfig.queryStrings().items()));
                }
            }
        }
    }

    private static String nullSafeInstant(Instant instant) {
        if (instant == null) {
            return "-";
        }
        return DateTimeFormatter.ISO_INSTANT.format(instant);
    }

}