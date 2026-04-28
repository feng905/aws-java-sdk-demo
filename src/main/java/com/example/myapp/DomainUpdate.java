package com.example.myapp;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.*;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;

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
 *
 * 环境变量：
 * - AWS_ACCESS_KEY_ID
 * - AWS_SECRET_ACCESS_KEY
 * - AWS_REGION
 * - CF_DISTRIBUTION_ID
 * - CF_ENABLED
 * - CF_COMMENT
 *
 * 运行示例（推荐：环境变量方式）：
 * export AWS_ACCESS_KEY_ID="<YOUR_AK>"
 * export AWS_SECRET_ACCESS_KEY="<YOUR_SK>"
 * export AWS_REGION="us-east-1"
 * export CF_DISTRIBUTION_ID="E1234567890ABC"
 * export CF_ENABLED="true"
 * export CF_COMMENT="Updated comment"
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

    public static void main(String[] args) {
        String accessKeyId = resolveValue(args, 0, ENV_AWS_ACCESS_KEY_ID, DEFAULT_ACCESS_KEY_ID);
        String secretAccessKey = resolveValue(args, 1, ENV_AWS_SECRET_ACCESS_KEY, DEFAULT_SECRET_ACCESS_KEY);
        String regionArg = resolveValue(args, 2, ENV_AWS_REGION, "");
        String distributionId = resolveValue(args, 3, ENV_CF_DISTRIBUTION_ID, "");
        String enabledArg = resolveValue(args, 4, ENV_CF_ENABLED, "false");
        String commentArg = resolveValue(args, 5, ENV_CF_COMMENT, "");

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

        try (CloudFrontClient cloudFrontClient = CloudFrontClient.builder()
                .region(region)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build()) {

            GetDistributionRequest getReq = GetDistributionRequest.builder()
                    .id(distributionId)
                    .build();

            GetDistributionResponse getResp = cloudFrontClient.getDistribution(getReq);
            Distribution distribution = getResp.distribution();
            DistributionConfig config = distribution.distributionConfig();
            String eTag = getResp.eTag();

            DistributionConfig.Builder configBuilder = config.toBuilder();

            if (!enabledArg.isEmpty()) {
                boolean enabled = Boolean.parseBoolean(enabledArg);
                configBuilder.enabled(enabled);
            }

            if (!commentArg.isEmpty()) {
                configBuilder.comment(commentArg);
            }

            UpdateDistributionRequest updateReq = UpdateDistributionRequest.builder()
                    .id(distributionId)
                    .distributionConfig(configBuilder.build())
                    .ifMatch(eTag)
                    .build();

            UpdateDistributionResponse updateResp = cloudFrontClient.updateDistribution(updateReq);

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

}