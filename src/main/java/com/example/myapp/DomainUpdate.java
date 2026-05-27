package com.example.myapp;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.*;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.SdkBytes;

/**
 * CloudFront Distribution 更新示例。
 *
 * 参数优先级：命令行参数 > 环境变量 > 代码默认值（空）。
 *
 * 命令行参数：
 * 1) accessKeyId
 * 2) secretAccessKey
 * 3) region（可选，默认 us-east-1）
 * 4) distributionId（Distribution ID，必填）
 * 5) enabled（是否启用，true/false，可选）
 * 6) comment（备注，可选）
 * 7) updateBehavior（是否更新缓存行为，true/false，可选）
 * 8) enableIpBlacklist（是否开启 IP 黑名单，true/false，可选，默认 false）
 *
 * 环境变量：
 * - AWS_ACCESS_KEY_ID
 * - AWS_SECRET_ACCESS_KEY
 * - AWS_REGION
 * - CF_DISTRIBUTION_ID
 * - CF_ENABLED
 * - CF_COMMENT
 * - CF_UPDATE_BEHAVIOR
 * - CF_ENABLE_IP_BLACKLIST
 *
 * 运行示例（推荐：环境变量方式）：
 * export AWS_ACCESS_KEY_ID="<YOUR_AK>"
 * export AWS_SECRET_ACCESS_KEY="<YOUR_SK>"
 * export AWS_REGION="us-east-1"
 * export CF_DISTRIBUTION_ID="E1234567890ABC"
 * export CF_ENABLED="true"
 * export CF_COMMENT="Updated comment"
 * export CF_UPDATE_BEHAVIOR="true"
 * export CF_ENABLE_IP_BLACKLIST="false"
 * mvn -q exec:java -Dexec.mainClass=com.example.myapp.DomainUpdate
 */
public class DomainUpdate {

    private static final String DEFAULT_ACCESS_KEY_ID = "";
    private static final String DEFAULT_SECRET_ACCESS_KEY = "";
    private static final Region DEFAULT_REGION = Region.US_EAST_1;

    private static final String ENV_AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
    private static final String ENV_AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
    private static final String ENV_AWS_REGION = "AWS_REGION";
    private static final String ENV_CF_DISTRIBUTION_ID = "CF_DISTRIBUTION_ID";
    private static final String ENV_CF_ENABLED = "CF_ENABLED";
    private static final String ENV_CF_COMMENT = "CF_COMMENT";
    private static final String ENV_CF_UPDATE_BEHAVIOR = "CF_UPDATE_BEHAVIOR";
    private static final String ENV_CF_ENABLE_IP_BLACKLIST = "CF_ENABLE_IP_BLACKLIST";

    private static final List<String> NO_CACHE_EXTENSIONS = Arrays.asList("*.php", "*.jsp", "*.asp");
    private static final String IP_BLACKLIST_FUNCTION_NAME_PREFIX = "ip-blacklist-";
    private static final String IP_BLACKLIST_FUNCTION_COMMENT = "Block requests from blacklist IPs";
    private static final String NO_SUCH_FUNCTION_ERROR_CODE = "NoSuchFunctionExists";
    private static final String PRECONDITION_FAILED_ERROR_CODE = "PreconditionFailed";
    private static final int FUNCTION_NAME_MAX_LENGTH = 64;
    private static final int MAX_RETRY_ATTEMPTS = 2;
        private static final String IP_BLACKLIST_FUNCTION_CODE = String.join("\n",
                        "function handler(event) {",
                        "  var request = event.request;",
                        "  var clientIP = event.viewer.ip;",
                        "",
                        "  var ipBlackList = [",
                        "    \"114.215.30.8\",",
                        "    \"139.129.247.131\"",
                        "  ];",
                        "",
                        "  if (ipBlackList.includes(clientIP)) {",
                        "    return {",
                        "      statusCode: 403,",
                        "      statusDescription: \"Forbidden - IP Blocked\"",
                        "    };",
                        "  }",
                        "",
                        "  return request;",
                        "}");

    public static void main(String[] args) {
        String accessKeyId = resolveValue(args, 0, ENV_AWS_ACCESS_KEY_ID, DEFAULT_ACCESS_KEY_ID);
        String secretAccessKey = resolveValue(args, 1, ENV_AWS_SECRET_ACCESS_KEY, DEFAULT_SECRET_ACCESS_KEY);
        String regionArg = resolveValue(args, 2, ENV_AWS_REGION, "");
        String distributionId = resolveValue(args, 3, ENV_CF_DISTRIBUTION_ID, "");
        String enabledArg = resolveValue(args, 4, ENV_CF_ENABLED, "false");
        String commentArg = resolveValue(args, 5, ENV_CF_COMMENT, "");
        String updateBehaviorArg = resolveValue(args, 6, ENV_CF_UPDATE_BEHAVIOR, "false");
        String enableIpBlacklistArg = resolveValue(args, 7, ENV_CF_ENABLE_IP_BLACKLIST, "false");

        if (accessKeyId.isEmpty() || secretAccessKey.isEmpty()) {
            System.err.println("AK/SK 未提供。请通过参数或环境变量提供：AWS_ACCESS_KEY_ID、AWS_SECRET_ACCESS_KEY");
            return;
        }
        if (distributionId.isEmpty()) {
            System.err.println("Distribution ID 未提供。请通过参数或环境变量 CF_DISTRIBUTION_ID 传入");
            return;
        }

        Region region = resolveRegion(regionArg);
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
        boolean shouldUpdateBehavior = Boolean.parseBoolean(updateBehaviorArg);
        boolean shouldEnableIpBlacklist = Boolean.parseBoolean(enableIpBlacklistArg);

        try (CloudFrontClient cloudFrontClient = CloudFrontClient.builder()
                .region(region)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build()) {
            String functionArn = shouldEnableIpBlacklist
                ? ensureIpBlacklistFunctionArn(cloudFrontClient, distributionId)
                : "";
            UpdateDistributionResponse updateResp = updateDistributionWithRetry(
                cloudFrontClient,
                distributionId,
                enabledArg,
                commentArg,
                shouldUpdateBehavior,
                shouldEnableIpBlacklist,
                functionArn);

            System.out.println("更新成功");
            System.out.println("区域: " + region.id());
            System.out.println("Distribution ID: " + nullSafe(distributionId));
            System.out.println("Distribution 域名: " + nullSafe(updateResp.distribution().domainName()));
            System.out.println("状态: " + nullSafe(updateResp.distribution().status()));
            System.out.println("提示：Distribution 更新可能需要几分钟完成");

        } catch (CloudFrontException ex) {
            AwsErrorDetails details = ex.awsErrorDetails();
            if (details != null) {
                System.err.println("调用 CloudFront 更新失败: " + nullSafe(details.errorMessage()));
                System.err.println("错误码: " + nullSafe(details.errorCode()));
            } else {
                System.err.println("调用 CloudFront 更新失败: " + ex.getMessage());
            }
        } catch (IllegalArgumentException ex) {
            System.err.println("参数错误: " + ex.getMessage());
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
        if (args == null || args.length <= index || args[index] == null || args[index].trim().isEmpty()) {
            String envValue = System.getenv(envKey);
            if (envValue == null || envValue.trim().isEmpty()) {
                return defaultValue;
            }
            return envValue.trim();
        }
        return args[index].trim();
    }

    private static String nullSafe(String value) {
        return value == null ? "-" : value;
    }

    private static final String NO_CACHE_POLICY_ID = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad";

    private static List<CacheBehavior> buildNoCacheBehaviors(String targetOriginId,
                                                              ViewerProtocolPolicy protocolPolicy,
                                                              AllowedMethods allowedMethods) {

        LambdaFunctionAssociations emptyLambda = LambdaFunctionAssociations.builder().quantity(0).build();
        return NO_CACHE_EXTENSIONS.stream()
                .map(ext -> CacheBehavior.builder()
                        .pathPattern(ext)
                        .targetOriginId(targetOriginId)
                        .viewerProtocolPolicy(protocolPolicy)
                        .allowedMethods(allowedMethods)
                        .cachePolicyId(NO_CACHE_POLICY_ID)
                        .smoothStreaming(false)
                        .fieldLevelEncryptionId("")
                        .lambdaFunctionAssociations(emptyLambda)
                        .compress(true)
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }

    private static UpdateDistributionResponse updateDistributionWithRetry(CloudFrontClient cloudFrontClient,
                                                                          String distributionId,
                                                                          String enabledArg,
                                                                          String commentArg,
                                                                          boolean shouldUpdateBehavior,
                                                                          boolean shouldEnableIpBlacklist,
                                                                          String functionArn) {
        CloudFrontException lastException = null;
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            GetDistributionResponse getResp = cloudFrontClient.getDistribution(GetDistributionRequest.builder()
                    .id(distributionId)
                    .build());

            Distribution distribution = getResp.distribution();
            DistributionConfig updatedConfig = buildUpdatedDistributionConfig(
                    distribution.distributionConfig(),
                    enabledArg,
                    commentArg,
                    shouldUpdateBehavior,
                    shouldEnableIpBlacklist,
                    functionArn);

            try {
                return cloudFrontClient.updateDistribution(UpdateDistributionRequest.builder()
                        .id(distributionId)
                        .distributionConfig(updatedConfig)
                        .ifMatch(getResp.eTag())
                        .build());
            } catch (CloudFrontException ex) {
                lastException = ex;
                if (!isPreconditionFailed(ex) || attempt == MAX_RETRY_ATTEMPTS - 1) {
                    throw ex;
                }
            }
        }

        throw lastException == null
                ? new IllegalStateException("更新 Distribution 失败，未拿到可用响应")
                : lastException;
    }

    private static DistributionConfig buildUpdatedDistributionConfig(DistributionConfig config,
                                                                     String enabledArg,
                                                                     String commentArg,
                                                                     boolean shouldUpdateBehavior,
                                                                     boolean shouldEnableIpBlacklist,
                                                                     String functionArn) {
        DistributionConfig.Builder configBuilder = config.toBuilder();
        DefaultCacheBehavior defaultBehavior = config.defaultCacheBehavior();
        CacheBehaviors cacheBehaviors = config.cacheBehaviors();

        if (!enabledArg.isEmpty()) {
            configBuilder.enabled(Boolean.parseBoolean(enabledArg));
        }

        if (!commentArg.isEmpty()) {
            configBuilder.comment(commentArg);
        }

        if (shouldUpdateBehavior) {
            List<CacheBehavior> behaviorItems = buildNoCacheBehaviors(
                    defaultBehavior.targetOriginId(),
                    defaultBehavior.viewerProtocolPolicy(),
                    defaultBehavior.allowedMethods());
            cacheBehaviors = CacheBehaviors.builder()
                    .items(behaviorItems)
                    .quantity(behaviorItems.size())
                    .build();
        }

        if (shouldEnableIpBlacklist) {
            defaultBehavior = attachIpBlacklistFunction(defaultBehavior, functionArn);

            if (cacheBehaviors != null && cacheBehaviors.items() != null && !cacheBehaviors.items().isEmpty()) {
                List<CacheBehavior> updatedBehaviors = new ArrayList<CacheBehavior>();
                for (CacheBehavior behavior : cacheBehaviors.items()) {
                    updatedBehaviors.add(attachIpBlacklistFunction(behavior, functionArn));
                }
                cacheBehaviors = CacheBehaviors.builder()
                        .quantity(updatedBehaviors.size())
                        .items(updatedBehaviors)
                        .build();
            }
        }

        configBuilder.defaultCacheBehavior(defaultBehavior);
        if (cacheBehaviors != null) {
            configBuilder.cacheBehaviors(cacheBehaviors);
        }
        return configBuilder.build();
    }

    private static String ensureIpBlacklistFunctionArn(CloudFrontClient cloudFrontClient, String distributionId) {
        String functionName = buildIpBlacklistFunctionName(distributionId);
        FunctionConfig functionConfig = FunctionConfig.builder()
                .runtime(FunctionRuntime.CLOUDFRONT_JS_1_0)
                .comment(IP_BLACKLIST_FUNCTION_COMMENT)
                .build();
        SdkBytes functionCode = SdkBytes.fromString(IP_BLACKLIST_FUNCTION_CODE, StandardCharsets.UTF_8);

        CloudFrontException lastException = null;
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            String etag;
            boolean needsUpdate = true;
            try {
                DescribeFunctionResponse describeResponse = cloudFrontClient.describeFunction(
                        DescribeFunctionRequest.builder()
                                .name(functionName)
                                .stage(FunctionStage.DEVELOPMENT)
                                .build());
                etag = describeResponse.eTag();
            } catch (CloudFrontException ex) {
                String errorCode = ex.awsErrorDetails() == null ? null : ex.awsErrorDetails().errorCode();
                if (!NO_SUCH_FUNCTION_ERROR_CODE.equals(errorCode)) {
                    throw ex;
                }

                CreateFunctionResponse createResponse = cloudFrontClient.createFunction(CreateFunctionRequest.builder()
                        .name(functionName)
                        .functionConfig(functionConfig)
                        .functionCode(functionCode)
                        .build());
                etag = createResponse.eTag();
                needsUpdate = false;
            }

            try {
                if (needsUpdate) {
                    UpdateFunctionResponse updateResponse = cloudFrontClient.updateFunction(UpdateFunctionRequest.builder()
                        .name(functionName)
                        .ifMatch(etag)
                        .functionConfig(functionConfig)
                        .functionCode(functionCode)
                        .build());
                    etag = updateResponse.eTag();
                }

                PublishFunctionResponse publishResponse = cloudFrontClient.publishFunction(PublishFunctionRequest.builder()
                        .name(functionName)
                    .ifMatch(etag)
                        .build());

                String functionArn = publishResponse.functionSummary() == null
                        ? ""
                        : publishResponse.functionSummary().functionMetadata().functionARN();
                if (functionArn == null || functionArn.trim().isEmpty()) {
                    throw new IllegalStateException("发布 CloudFront Function 成功但未返回 ARN");
                }
                return functionArn;
            } catch (CloudFrontException ex) {
                lastException = ex;
                if (!isPreconditionFailed(ex) || attempt == MAX_RETRY_ATTEMPTS - 1) {
                    throw ex;
                }
            }
        }

        throw lastException == null
                ? new IllegalStateException("发布 CloudFront Function 失败，未拿到可用响应")
                : lastException;
    }

    private static boolean isPreconditionFailed(CloudFrontException ex) {
        if (ex == null || ex.awsErrorDetails() == null) {
            return false;
        }
        return PRECONDITION_FAILED_ERROR_CODE.equals(ex.awsErrorDetails().errorCode());
    }

    private static String buildIpBlacklistFunctionName(String distributionId) {
        String baseName = distributionId == null ? "distribution" : distributionId.trim();
        if (baseName.isEmpty()) {
            baseName = "distribution";
        }

        String sanitized = baseName.replaceAll("[^A-Za-z0-9_-]", "-");
        if (sanitized.isEmpty()) {
            sanitized = "distribution";
        }

        String hashSuffix = Integer.toHexString(sanitized.hashCode());
        String fixedSuffix = "-" + hashSuffix;
        int maxBaseLength = FUNCTION_NAME_MAX_LENGTH - IP_BLACKLIST_FUNCTION_NAME_PREFIX.length() - fixedSuffix.length();
        if (maxBaseLength < 1) {
            maxBaseLength = 1;
        }
        if (sanitized.length() > maxBaseLength) {
            sanitized = sanitized.substring(0, maxBaseLength);
        }

        return IP_BLACKLIST_FUNCTION_NAME_PREFIX + sanitized + fixedSuffix;
    }

    private static DefaultCacheBehavior attachIpBlacklistFunction(DefaultCacheBehavior behavior, String functionArn) {
        List<FunctionAssociation> updatedAssociations = upsertViewerRequestAssociation(
                behavior.functionAssociations(),
                functionArn);
        return behavior.toBuilder()
                .functionAssociations(FunctionAssociations.builder()
                        .quantity(updatedAssociations.size())
                        .items(updatedAssociations)
                        .build())
                .build();
    }

    private static CacheBehavior attachIpBlacklistFunction(CacheBehavior behavior, String functionArn) {
        List<FunctionAssociation> updatedAssociations = upsertViewerRequestAssociation(
                behavior.functionAssociations(),
                functionArn);
        return behavior.toBuilder()
                .functionAssociations(FunctionAssociations.builder()
                        .quantity(updatedAssociations.size())
                        .items(updatedAssociations)
                        .build())
                .build();
    }

    private static List<FunctionAssociation> upsertViewerRequestAssociation(FunctionAssociations associations,
                                                                            String functionArn) {
        List<FunctionAssociation> items = new ArrayList<FunctionAssociation>();
        boolean hasViewerRequestAssociation = false;
        if (associations != null && associations.items() != null) {
            for (FunctionAssociation item : associations.items()) {
                if (item.eventType() == EventType.VIEWER_REQUEST) {
                    String existingArn = item.functionARN() == null ? "" : item.functionARN().trim();
                    if (!existingArn.equals(functionArn)) {
                        throw new IllegalStateException(
                                "检测到已有 VIEWER_REQUEST Function 关联: " + existingArn
                                        + "。为避免覆盖现有逻辑，已停止更新，请手动合并函数逻辑后再执行。");
                    }
                    hasViewerRequestAssociation = true;
                    items.add(item);
                    continue;
                }

                items.add(item);
            }
        }

        if (!hasViewerRequestAssociation) {
            items.add(FunctionAssociation.builder()
                    .eventType(EventType.VIEWER_REQUEST)
                    .functionARN(functionArn)
                    .build());
        }
        return items;
    }

}