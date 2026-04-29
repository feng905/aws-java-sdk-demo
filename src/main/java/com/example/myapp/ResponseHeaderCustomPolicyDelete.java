package com.example.myapp;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.DeleteResponseHeadersPolicyRequest;
import software.amazon.awssdk.services.cloudfront.model.GetResponseHeadersPolicyRequest;
import software.amazon.awssdk.services.cloudfront.model.GetResponseHeadersPolicyResponse;
import software.amazon.awssdk.services.cloudfront.model.ListResponseHeadersPoliciesRequest;
import software.amazon.awssdk.services.cloudfront.model.ListResponseHeadersPoliciesResponse;
import software.amazon.awssdk.services.cloudfront.model.ResponseHeadersPolicyList;
import software.amazon.awssdk.services.cloudfront.model.ResponseHeadersPolicySummary;
import software.amazon.awssdk.services.cloudfront.model.ResponseHeadersPolicyType;
import software.amazon.awssdk.services.cloudfront.model.CloudFrontException;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;

/**
 * CloudFront 自定义响应标头策略删除示例。
 *
 * 使用与创建示例相同的默认策略名称删除响应标头策略。
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
 * mvn -q exec:java -Dexec.mainClass=com.example.myapp.ResponseHeaderCustomPolicyDelete
 *
 * 注意：如果策略正在被 Distribution 使用，删除会失败。请先解除关联。
 */
public class ResponseHeaderCustomPolicyDelete {

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

            String policyId = findPolicyIdByName(cloudFrontClient, DEFAULT_POLICY_NAME);
            if (policyId == null) {
                System.err.println("未找到策略: " + DEFAULT_POLICY_NAME);
                return;
            }

            GetResponseHeadersPolicyResponse getResponse = cloudFrontClient.getResponseHeadersPolicy(
                GetResponseHeadersPolicyRequest.builder().id(policyId).build());
            String eTag = getResponse.eTag();

            DeleteResponseHeadersPolicyRequest deleteRequest = DeleteResponseHeadersPolicyRequest.builder()
                    .id(policyId)
                    .ifMatch(eTag)
                    .build();

            cloudFrontClient.deleteResponseHeadersPolicy(deleteRequest);

            System.out.println("删除成功");
            System.out.println("策略名称: " + DEFAULT_POLICY_NAME);
            System.out.println("策略 ID: " + policyId);

        } catch (CloudFrontException ex) {
            AwsErrorDetails details = ex.awsErrorDetails();
            if (details != null) {
                System.err.println("调用 CloudFront 删除响应标头策略失败: " + nullSafe(details.errorMessage()));
                System.err.println("错误码: " + nullSafe(details.errorCode()));
            } else {
                System.err.println("调用 CloudFront 删除响应标头策略失败: " + ex.getMessage());
            }
        } catch (Exception ex) {
            System.err.println("程序执行失败(" + ex.getClass().getSimpleName() + "): " + ex.getMessage());
        }
    }

    private static String findPolicyIdByName(CloudFrontClient client, String policyName) {
        String nextMarker = null;
        do {
            ListResponseHeadersPoliciesRequest request = ListResponseHeadersPoliciesRequest.builder()
                    .type(ResponseHeadersPolicyType.CUSTOM)
                    .marker(nextMarker)
                    .build();
            ListResponseHeadersPoliciesResponse response = client.listResponseHeadersPolicies(request);
            ResponseHeadersPolicyList policyList = response.responseHeadersPolicyList();

            if (policyList.items() != null) {
                for (ResponseHeadersPolicySummary summary : policyList.items()) {
                    if (summary.responseHeadersPolicy() != null
                            && summary.responseHeadersPolicy().responseHeadersPolicyConfig() != null
                            && policyName.equals(summary.responseHeadersPolicy().responseHeadersPolicyConfig().name())) {
                        return summary.responseHeadersPolicy().id();
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
