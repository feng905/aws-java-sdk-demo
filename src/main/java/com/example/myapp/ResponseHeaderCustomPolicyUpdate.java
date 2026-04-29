package com.example.myapp;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.FrameOptionsList;
import software.amazon.awssdk.services.cloudfront.model.GetResponseHeadersPolicyRequest;
import software.amazon.awssdk.services.cloudfront.model.GetResponseHeadersPolicyResponse;
import software.amazon.awssdk.services.cloudfront.model.ListResponseHeadersPoliciesRequest;
import software.amazon.awssdk.services.cloudfront.model.ListResponseHeadersPoliciesResponse;
import software.amazon.awssdk.services.cloudfront.model.ReferrerPolicyList;
import software.amazon.awssdk.services.cloudfront.model.ResponseHeadersPolicyConfig;
import software.amazon.awssdk.services.cloudfront.model.ResponseHeadersPolicyContentTypeOptions;
import software.amazon.awssdk.services.cloudfront.model.ResponseHeadersPolicyFrameOptions;
import software.amazon.awssdk.services.cloudfront.model.ResponseHeadersPolicyList;
import software.amazon.awssdk.services.cloudfront.model.ResponseHeadersPolicyReferrerPolicy;
import software.amazon.awssdk.services.cloudfront.model.ResponseHeadersPolicySecurityHeadersConfig;
import software.amazon.awssdk.services.cloudfront.model.ResponseHeadersPolicyStrictTransportSecurity;
import software.amazon.awssdk.services.cloudfront.model.ResponseHeadersPolicySummary;
import software.amazon.awssdk.services.cloudfront.model.ResponseHeadersPolicyType;
import software.amazon.awssdk.services.cloudfront.model.UpdateResponseHeadersPolicyRequest;
import software.amazon.awssdk.services.cloudfront.model.UpdateResponseHeadersPolicyResponse;
import software.amazon.awssdk.services.cloudfront.model.CloudFrontException;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;

/**
 * CloudFront 自定义响应标头策略修改示例。
 *
 * 使用与创建示例相同的默认策略名称，将策略修改为另一组常用配置。
 * 修改后配置：HSTS（较短 max-age）+ X-Frame-Options: DENY + Referrer-Policy: no-referrer。
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
 * mvn -q exec:java -Dexec.mainClass=com.example.myapp.ResponseHeaderCustomPolicyUpdate
 *
 * 注意：请先运行 ResponseHeaderCustomPolicyCreate 创建策略，再运行本示例修改。
 */
public class ResponseHeaderCustomPolicyUpdate {

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
                System.err.println("未找到策略: " + DEFAULT_POLICY_NAME + "。请先运行 ResponseHeaderCustomPolicyCreate 创建策略。");
                return;
            }

            GetResponseHeadersPolicyResponse getResponse = cloudFrontClient.getResponseHeadersPolicy(
                GetResponseHeadersPolicyRequest.builder().id(policyId).build());
            String eTag = getResponse.eTag();

            ResponseHeadersPolicySecurityHeadersConfig securityHeaders =
                ResponseHeadersPolicySecurityHeadersConfig.builder()
                    .strictTransportSecurity(ResponseHeadersPolicyStrictTransportSecurity.builder()
                        .override(true)
                        .accessControlMaxAgeSec(86400)
                        .includeSubdomains(false)
                        .preload(false)
                        .build())
                    .frameOptions(ResponseHeadersPolicyFrameOptions.builder()
                        .override(true)
                        .frameOption(FrameOptionsList.DENY)
                        .build())
                    .referrerPolicy(ResponseHeadersPolicyReferrerPolicy.builder()
                        .override(true)
                        .referrerPolicy(ReferrerPolicyList.NO_REFERRER)
                        .build())
                    .contentTypeOptions(ResponseHeadersPolicyContentTypeOptions.builder()
                        .override(true)
                        .build())
                    .build();

            ResponseHeadersPolicyConfig newConfig = ResponseHeadersPolicyConfig.builder()
                    .name(DEFAULT_POLICY_NAME)
                    .comment("自定义响应标头策略示例 - 常用配置（已修改）")
                    .securityHeadersConfig(securityHeaders)
                    .build();

            UpdateResponseHeadersPolicyRequest updateRequest = UpdateResponseHeadersPolicyRequest.builder()
                    .responseHeadersPolicyConfig(newConfig)
                    .id(policyId)
                    .ifMatch(eTag)
                    .build();

            UpdateResponseHeadersPolicyResponse response = cloudFrontClient.updateResponseHeadersPolicy(updateRequest);

            System.out.println("修改成功");
            System.out.println("策略名称: " + DEFAULT_POLICY_NAME);
            System.out.println("策略 ID: " + nullSafe(response.responseHeadersPolicy().id()));
            System.out.println("新配置:");
            System.out.println("  HSTS: max-age=86400");
            System.out.println("  X-Frame-Options: DENY");
            System.out.println("  Referrer-Policy: no-referrer");
            System.out.println("  X-Content-Type-Options: nosniff");

        } catch (CloudFrontException ex) {
            AwsErrorDetails details = ex.awsErrorDetails();
            if (details != null) {
                System.err.println("调用 CloudFront 修改响应标头策略失败: " + nullSafe(details.errorMessage()));
                System.err.println("错误码: " + nullSafe(details.errorCode()));
            } else {
                System.err.println("调用 CloudFront 修改响应标头策略失败: " + ex.getMessage());
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
