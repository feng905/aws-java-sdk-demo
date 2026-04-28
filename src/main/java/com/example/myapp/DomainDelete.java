package com.example.myapp;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.*;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;

/**
 * CloudFront Distribution 删除示例。
 *
 * 参数优先级：命令行参数 > 环境变量 > 代码默认值（空）。
 *
 * 命令行参数：
 * 1) accessKeyId
 * 2) secretAccessKey
 * 3) region（可选，默认 us-east-1）
 * 4) distributionId（Distribution ID，必填）
 *
 * 环境变量：
 * - AWS_ACCESS_KEY_ID
 * - AWS_SECRET_ACCESS_KEY
 * - AWS_REGION
 * - CF_DISTRIBUTION_ID
 *
 * 运行示例（推荐：环境变量方式）：
 * export AWS_ACCESS_KEY_ID="<YOUR_AK>"
 * export AWS_SECRET_ACCESS_KEY="<YOUR_SK>"
 * export AWS_REGION="us-east-1"
 * export CF_DISTRIBUTION_ID="E1234567890ABC"
 * mvn -q exec:java -Dexec.mainClass=com.example.myapp.DomainDelete
 *
 * 运行示例（命令行参数方式）：
 * mvn -q exec:java -Dexec.mainClass=com.example.myapp.DomainDelete -Dexec.args="<AK> <SK> us-east-1 E1234567890ABC"
 *
 * 注意：删除前需要先禁用 Distribution，等待其状态变为 Deployed 后才能删除
 */
public class DomainDelete {

    private static final String DEFAULT_ACCESS_KEY_ID = "";
    private static final String DEFAULT_SECRET_ACCESS_KEY = "";
    private static final Region DEFAULT_REGION = Region.US_EAST_1;

    private static final String ENV_AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
    private static final String ENV_AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
    private static final String ENV_AWS_REGION = "AWS_REGION";
    private static final String ENV_CF_DISTRIBUTION_ID = "CF_DISTRIBUTION_ID";

    public static void main(String[] args) {
        String accessKeyId = resolveValue(args, 0, ENV_AWS_ACCESS_KEY_ID, DEFAULT_ACCESS_KEY_ID);
        String secretAccessKey = resolveValue(args, 1, ENV_AWS_SECRET_ACCESS_KEY, DEFAULT_SECRET_ACCESS_KEY);
        String regionArg = resolveValue(args, 2, ENV_AWS_REGION, "");
        String distributionId = resolveValue(args, 3, ENV_CF_DISTRIBUTION_ID, "");

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

            if (config.enabled()) {
                System.err.println("Distribution 当前处于启用状态，无法直接删除。");
                System.err.println("请先禁用 Distribution (使用 DomainUpdate 设置 enabled=false)，等待状态变为 Deployed 后再删除。");
                System.err.println("Distribution ID: " + distributionId);
                System.err.println("当前状态: " + nullSafe(distribution.status()));
                return;
            }

            DeleteDistributionRequest deleteReq = DeleteDistributionRequest.builder()
                    .id(distributionId)
                    .ifMatch(getResp.eTag())
                    .build();

            cloudFrontClient.deleteDistribution(deleteReq);

            System.out.println("删除成功");
            System.out.println("区域: " + region.id());
            System.out.println("Distribution ID: " + nullSafe(distributionId));

        } catch (NoSuchDistributionException ex) {
            System.err.println("Distribution 不存在: " + distributionId);
        } catch (DistributionNotDisabledException ex) {
            System.err.println("Distribution 未禁用，无法删除: " + distributionId);
        } catch (CloudFrontException ex) {
            AwsErrorDetails details = ex.awsErrorDetails();
            if (details != null) {
                System.err.println("调用 CloudFront 删除失败: " + nullSafe(details.errorMessage()));
                System.err.println("错误码: " + nullSafe(details.errorCode()));
            } else {
                System.err.println("调用 CloudFront 删除失败: " + ex.getMessage());
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