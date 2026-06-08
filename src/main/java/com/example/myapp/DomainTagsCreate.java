package com.example.myapp;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CloudFrontException;
import software.amazon.awssdk.services.cloudfront.model.GetDistributionRequest;
import software.amazon.awssdk.services.cloudfront.model.Tag;
import software.amazon.awssdk.services.cloudfront.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.cloudfront.model.TagResourceRequest;
import software.amazon.awssdk.services.cloudfront.model.Tags;

/**
 * CloudFront Distribution 标签管理示例。
 *
 * 参数优先级：命令行参数 > 环境变量 > 代码默认值（空）。
 *
 * 命令行参数：
 * 1) accessKeyId
 * 2) secretAccessKey
 * 3) region（可选，默认 us-east-1）
 * 4) distributionId（Distribution ID，必填）
 * 5) enableTags（是否创建标签，true/false，默认 false）
 *
 * 环境变量：
 * - AWS_ACCESS_KEY_ID
 * - AWS_SECRET_ACCESS_KEY
 * - AWS_REGION
 * - CF_DISTRIBUTION_ID
 * - CF_ENABLE_TAGS（是否创建标签，默认 false）
 *
 * 当 CF_ENABLE_TAGS=true 时，会为 Distribution 创建以下标签：
 * - Name: Static
 * - MyTag: test
 *
 * 运行示例（推荐：环境变量方式）：
 * export AWS_ACCESS_KEY_ID="<YOUR_AK>"
 * export AWS_SECRET_ACCESS_KEY="<YOUR_SK>"
 * export AWS_REGION="us-east-1"
 * export CF_DISTRIBUTION_ID="E1234567890ABC"
 * export CF_ENABLE_TAGS="true"
 * mvn -q exec:java -Dexec.mainClass=com.example.myapp.DomainTagsCreate
 */
public class DomainTagsCreate {

    private static final String DEFAULT_ACCESS_KEY_ID = "";
    private static final String DEFAULT_SECRET_ACCESS_KEY = "";
    private static final Region DEFAULT_REGION = Region.US_EAST_1;

    private static final String ENV_AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
    private static final String ENV_AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
    private static final String ENV_AWS_REGION = "AWS_REGION";
    private static final String ENV_CF_DISTRIBUTION_ID = "CF_DISTRIBUTION_ID";
    private static final String ENV_CF_ENABLE_TAGS = "CF_ENABLE_TAGS";

    private static final String TAG_NAME_KEY = "Name";
    private static final String TAG_NAME_VALUE = "Static";
    private static final String TAG_MY_KEY = "MyTag";
    private static final String TAG_MY_VALUE = "test-1";

    public static void main(String[] args) {
        String accessKeyId = resolveValue(args, 0, ENV_AWS_ACCESS_KEY_ID, DEFAULT_ACCESS_KEY_ID);
        String secretAccessKey = resolveValue(args, 1, ENV_AWS_SECRET_ACCESS_KEY, DEFAULT_SECRET_ACCESS_KEY);
        String regionArg = resolveValue(args, 2, ENV_AWS_REGION, "");
        String distributionId = resolveValue(args, 3, ENV_CF_DISTRIBUTION_ID, "");
        String enableTagsArg = resolveValue(args, 4, ENV_CF_ENABLE_TAGS, "false");

        if (accessKeyId.isEmpty() || secretAccessKey.isEmpty()) {
            System.err.println("AK/SK 未提供。请通过参数或环境变量提供：AWS_ACCESS_KEY_ID、AWS_SECRET_ACCESS_KEY");
            return;
        }
        if (distributionId.isEmpty()) {
            System.err.println("Distribution ID 未提供。请通过参数或环境变量 CF_DISTRIBUTION_ID 传入");
            return;
        }

        boolean enableTags = Boolean.parseBoolean(enableTagsArg);

        Region region = resolveRegion(regionArg);
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);

        try (CloudFrontClient cloudFrontClient = CloudFrontClient.builder()
                .region(region)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build()) {

            String distributionArn = getDistributionArn(cloudFrontClient, distributionId);

            if(enableTags) {
                Tags tags = Tags.builder()
                        .items(Tag.builder().key(TAG_NAME_KEY).value(TAG_NAME_VALUE).build(),
                            Tag.builder().key(TAG_MY_KEY).value(TAG_MY_VALUE).build())
                        .build();

                cloudFrontClient.tagResource(TagResourceRequest.builder()
                        .resource(distributionArn)
                        .tags(tags)
                        .build());

                System.out.println("标签创建成功");
                System.out.println("区域: " + region.id());
                System.out.println("Distribution ID: " + nullSafe(distributionId));
            }else {
                System.out.println("标签创建已关闭（CF_ENABLE_TAGS=" + enableTagsArg + "），跳过创建标签操作");
            }

            System.out.println("正在获取所有标签...");
            Tags currentTags = cloudFrontClient.listTagsForResource(ListTagsForResourceRequest.builder()
                            .resource(distributionArn)
                            .build())
                    .tags();

            if (currentTags.items() == null || currentTags.items().isEmpty()) {
                System.out.println("当前标签列表: (无)");
            } else {
                System.out.println("当前标签列表:");
                for (Tag t : currentTags.items()) {
                    System.out.println("  - " + nullSafe(t.key()) + ": " + nullSafe(t.value()));
                }
            }

        } catch (CloudFrontException ex) {
            AwsErrorDetails details = ex.awsErrorDetails();
            if (details != null) {
                System.err.println("调用 CloudFront 标签操作失败: " + nullSafe(details.errorMessage()));
                System.err.println("错误码: " + nullSafe(details.errorCode()));
            } else {
                System.err.println("调用 CloudFront 标签操作失败: " + ex.getMessage());
            }
        } catch (Exception ex) {
            System.err.println("程序执行失败(" + ex.getClass().getSimpleName() + "): " + ex.getMessage());
        }
    }

    private static String getDistributionArn(CloudFrontClient cloudFrontClient, String distributionId) {
        return cloudFrontClient.getDistribution(GetDistributionRequest.builder()
                        .id(distributionId)
                        .build())
                .distribution()
                .arn();
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
