package com.example.myapp;

import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CloudFrontException;
import software.amazon.awssdk.services.cloudfront.model.DistributionConfig;
import software.amazon.awssdk.services.cloudfront.model.GetDistributionRequest;
import software.amazon.awssdk.services.cloudfront.model.GetDistributionResponse;
import software.amazon.awssdk.services.cloudfront.model.UpdateDistributionRequest;
import software.amazon.awssdk.services.cloudfront.model.UpdateDistributionResponse;
import software.amazon.awssdk.services.wafv2.Wafv2Client;
import software.amazon.awssdk.services.wafv2.model.AllowAction;
import software.amazon.awssdk.services.wafv2.model.BlockAction;
import software.amazon.awssdk.services.wafv2.model.CreateIpSetRequest;
import software.amazon.awssdk.services.wafv2.model.CreateIpSetResponse;
import software.amazon.awssdk.services.wafv2.model.CreateWebAclRequest;
import software.amazon.awssdk.services.wafv2.model.CreateWebAclResponse;
import software.amazon.awssdk.services.wafv2.model.DefaultAction;
import software.amazon.awssdk.services.wafv2.model.DeleteIpSetRequest;
import software.amazon.awssdk.services.wafv2.model.DeleteWebAclRequest;
import software.amazon.awssdk.services.wafv2.model.GetIpSetRequest;
import software.amazon.awssdk.services.wafv2.model.GetIpSetResponse;
import software.amazon.awssdk.services.wafv2.model.GetWebAclRequest;
import software.amazon.awssdk.services.wafv2.model.GetWebAclResponse;
import software.amazon.awssdk.services.wafv2.model.IPAddressVersion;
import software.amazon.awssdk.services.wafv2.model.IPSetReferenceStatement;
import software.amazon.awssdk.services.wafv2.model.IPSetSummary;
import software.amazon.awssdk.services.wafv2.model.ListIpSetsRequest;
import software.amazon.awssdk.services.wafv2.model.ListWebAcLsRequest;
import software.amazon.awssdk.services.wafv2.model.Rule;
import software.amazon.awssdk.services.wafv2.model.RuleAction;
import software.amazon.awssdk.services.wafv2.model.Scope;
import software.amazon.awssdk.services.wafv2.model.Statement;
import software.amazon.awssdk.services.wafv2.model.VisibilityConfig;
import software.amazon.awssdk.services.wafv2.model.WebACLSummary;
import software.amazon.awssdk.services.wafv2.model.Wafv2Exception;

/**
 * 为 CloudFront Distribution 创建并绑定一个基于 IP 黑名单的 AWS WAF Web ACL。
 *
 * 参数优先级：命令行参数 > 环境变量 > 代码默认值。
 *
 * 命令行参数：
 * 1) accessKeyId
 * 2) secretAccessKey
 * 3) region（可选，CloudFront WAF 固定要求 us-east-1）
 * 4) distributionId（CloudFront Distribution ID，必填）
 * 5) webAclName（可选）
 * 6) ipSetName（可选）
 * 7) blockedIps（可选，逗号分隔，默认 10.0.0.0/32,192.168.0.0/32）
 *
 * 环境变量：
 * - AWS_ACCESS_KEY_ID
 * - AWS_SECRET_ACCESS_KEY
 * - AWS_REGION
 * - CF_DISTRIBUTION_ID
 * - WAF_WEB_ACL_NAME
 * - WAF_IP_SET_NAME
 * - WAF_BLOCKED_IPS
 *
 * 运行示例：
 * export AWS_ACCESS_KEY_ID="<YOUR_AK>"
 * export AWS_SECRET_ACCESS_KEY="<YOUR_SK>"
 * export AWS_REGION="us-east-1"
 * export CF_DISTRIBUTION_ID="E1234567890ABC"
 * export WAF_BLOCKED_IPS="10.0.0.0/32,192.168.0.0/32"
 * mvn -q exec:java -Dexec.mainClass=com.example.myapp.WafCreate
 */
public class WafCreate {

    private static final String DEFAULT_ACCESS_KEY_ID = "";
    private static final String DEFAULT_SECRET_ACCESS_KEY = "";
    private static final Region DEFAULT_REGION = Region.US_EAST_1;
    private static final String DEFAULT_BLOCKED_IPS = "10.0.0.0/32,192.168.0.0/32";
    private static final String DEFAULT_RULE_NAME = "BlockListedIps";
    private static final String DEFAULT_WEB_ACL_SUFFIX = "BlacklistWebAcl";
    private static final String DEFAULT_IP_SET_SUFFIX = "BlockedIpSet";
    private static final String DEFAULT_DESCRIPTION = "Created by WafCreate";
    private static final int DEFAULT_METRIC_NAME_LIMIT = 128;
    private static final int DEFAULT_LIST_LIMIT = 100;

    private static final String ENV_AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
    private static final String ENV_AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
    private static final String ENV_AWS_REGION = "AWS_REGION";
    private static final String ENV_CF_DISTRIBUTION_ID = "CF_DISTRIBUTION_ID";
    private static final String ENV_WAF_WEB_ACL_NAME = "WAF_WEB_ACL_NAME";
    private static final String ENV_WAF_IP_SET_NAME = "WAF_IP_SET_NAME";
    private static final String ENV_WAF_BLOCKED_IPS = "WAF_BLOCKED_IPS";

    public static void main(String[] args) {
        String accessKeyId = resolveValue(args, 0, ENV_AWS_ACCESS_KEY_ID, DEFAULT_ACCESS_KEY_ID);
        String secretAccessKey = resolveValue(args, 1, ENV_AWS_SECRET_ACCESS_KEY, DEFAULT_SECRET_ACCESS_KEY);
        String regionArg = resolveValue(args, 2, ENV_AWS_REGION, DEFAULT_REGION.id());
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
        if (!DEFAULT_REGION.id().equals(region.id())) {
            System.err.println("CloudFront 关联的 AWS WAF 必须使用 us-east-1 区域");
            return;
        }

        String defaultWebAclName = buildDefaultName(distributionId, DEFAULT_WEB_ACL_SUFFIX);
        String defaultIpSetName = buildDefaultName(distributionId, DEFAULT_IP_SET_SUFFIX);
        String webAclName = resolveValue(args, 4, ENV_WAF_WEB_ACL_NAME, defaultWebAclName);
        String ipSetName = resolveValue(args, 5, ENV_WAF_IP_SET_NAME, defaultIpSetName);
        String blockedIpsArg = resolveValue(args, 6, ENV_WAF_BLOCKED_IPS, DEFAULT_BLOCKED_IPS);
        List<String> blockedIps = parseBlockedIps(blockedIpsArg);

        if (blockedIps.isEmpty()) {
            System.err.println("黑名单 IP 不能为空，请通过参数或环境变量 WAF_BLOCKED_IPS 提供 CIDR 列表");
            return;
        }

        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);

        try (Wafv2Client wafv2Client = Wafv2Client.builder()
                .region(region)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
             CloudFrontClient cloudFrontClient = CloudFrontClient.builder()
                     .region(region)
                     .credentialsProvider(StaticCredentialsProvider.create(credentials))
                     .build()) {

            AcquiredResource ipSet = null;
            AcquiredResource webAcl = null;
            UpdateDistributionResponse distributionResponse;

            try {
                ipSet = acquireIpSet(wafv2Client, ipSetName, blockedIps);
                webAcl = acquireWebAcl(wafv2Client, webAclName, ipSet.resource.arn);

                DistributionUpdateContext distributionContext = inspectDistribution(
                        cloudFrontClient,
                        distributionId,
                        webAcl.resource.arn);

                if (distributionContext.alreadyAssociated) {
                    System.out.println("Web ACL 已绑定到该 Distribution，跳过关联");
                    System.out.println("区域: " + region.id());
                    System.out.println("Distribution ID: " + nullSafe(distributionId));
                    System.out.println("Distribution 域名: " + nullSafe(distributionContext.domainName));
                    System.out.println("IPSet 名称: " + nullSafe(ipSet.resource.name));
                    System.out.println("Web ACL 名称: " + nullSafe(webAcl.resource.name));
                    System.out.println("已屏蔽 IP: " + String.join(",", blockedIps));
                    return;
                }

                distributionResponse = attachWebAclToDistribution(cloudFrontClient, distributionContext, webAcl.resource.arn);
                System.out.println("创建并绑定成功");
                System.out.println("区域: " + region.id());
                System.out.println("Distribution ID: " + nullSafe(distributionId));
                System.out.println("Distribution 域名: " + nullSafe(distributionContext.domainName));
                System.out.println("IPSet 名称: " + nullSafe(ipSet.resource.name));
                System.out.println("Web ACL 名称: " + nullSafe(webAcl.resource.name));
                System.out.println("已屏蔽 IP: " + String.join(",", blockedIps));
                System.out.println("Distribution 状态: " + nullSafe(distributionResponse.distribution().status()));
                System.out.println("提示：WAF 规则和 CloudFront 绑定生效通常需要 15-30 分钟传播");
            } catch (Exception ex) {
                rollbackCreatedResources(wafv2Client, webAcl, ipSet);
                throw ex;
            }

        } catch (Wafv2Exception ex) {
            AwsErrorDetails details = ex.awsErrorDetails();
            if (details != null) {
                System.err.println("调用 WAFv2 失败: " + nullSafe(details.errorMessage()));
                System.err.println("错误码: " + nullSafe(details.errorCode()));
            } else {
                System.err.println("调用 WAFv2 失败: " + ex.getMessage());
            }
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

    private static AcquiredResource acquireIpSet(Wafv2Client wafv2Client, String ipSetName, List<String> blockedIps) {
        ResourceRef existing = findExistingIpSet(wafv2Client, ipSetName);
        if (existing != null) {
            System.out.println("IPSet 已存在，跳过创建: " + ipSetName);
            return new AcquiredResource(existing, false);
        }

        ResourceRef created = createIpSet(wafv2Client, ipSetName, blockedIps);
        return new AcquiredResource(created, true);
    }

    private static ResourceRef createIpSet(Wafv2Client wafv2Client, String ipSetName, List<String> blockedIps) {
        CreateIpSetResponse response = wafv2Client.createIPSet(CreateIpSetRequest.builder()
                .name(ipSetName)
                .scope(Scope.CLOUDFRONT)
                .description(DEFAULT_DESCRIPTION)
                .ipAddressVersion(IPAddressVersion.IPV4)
                .addresses(blockedIps)
                .build());

        IPSetSummary summary = response.summary();
        if (summary == null || isBlank(summary.arn()) || isBlank(summary.id())) {
            throw new IllegalStateException("创建 IPSet 成功但未返回完整标识");
        }
        return new ResourceRef(summary.name(), summary.id(), summary.arn());
    }

    private static AcquiredResource acquireWebAcl(Wafv2Client wafv2Client, String webAclName, String ipSetArn) {
        ResourceRef existing = findExistingWebAcl(wafv2Client, webAclName);
        if (existing != null) {
            System.out.println("Web ACL 已存在，跳过创建: " + webAclName);
            return new AcquiredResource(existing, false);
        }

        ResourceRef created = createWebAcl(wafv2Client, webAclName, ipSetArn);
        return new AcquiredResource(created, true);
    }

    private static ResourceRef createWebAcl(Wafv2Client wafv2Client, String webAclName, String ipSetArn) {
        VisibilityConfig webAclVisibility = VisibilityConfig.builder()
                .cloudWatchMetricsEnabled(true)
                .sampledRequestsEnabled(true)
                .metricName(buildMetricName(webAclName, "WebAcl"))
                .build();

        VisibilityConfig ruleVisibility = VisibilityConfig.builder()
                .cloudWatchMetricsEnabled(true)
                .sampledRequestsEnabled(true)
                .metricName(buildMetricName(webAclName, "BlockedIpsRule"))
                .build();

        Rule blockIpRule = Rule.builder()
                .name(DEFAULT_RULE_NAME)
                .priority(0)
                .statement(Statement.builder()
                        .ipSetReferenceStatement(IPSetReferenceStatement.builder()
                                .arn(ipSetArn)
                                .build())
                        .build())
                .action(RuleAction.builder()
                        .block(BlockAction.builder().build())
                        .build())
                .visibilityConfig(ruleVisibility)
                .build();

        CreateWebAclResponse response = wafv2Client.createWebACL(CreateWebAclRequest.builder()
                .name(webAclName)
                .scope(Scope.CLOUDFRONT)
                .description(DEFAULT_DESCRIPTION)
                .defaultAction(DefaultAction.builder()
                        .allow(AllowAction.builder().build())
                        .build())
                .visibilityConfig(webAclVisibility)
                .rules(blockIpRule)
                .build());

            WebACLSummary summary = response.summary();
            if (summary == null || isBlank(summary.arn()) || isBlank(summary.id())) {
                throw new IllegalStateException("创建 Web ACL 成功但未返回完整标识");
            }
            return new ResourceRef(summary.name(), summary.id(), summary.arn());
    }

            private static UpdateDistributionResponse attachWebAclToDistribution(CloudFrontClient cloudFrontClient,
                                             DistributionUpdateContext distributionContext,
                                                                         String webAclArn) {
        UpdateDistributionRequest request = UpdateDistributionRequest.builder()
                .id(distributionContext.distributionId)
                .ifMatch(distributionContext.eTag)
                .distributionConfig(distributionContext.distributionConfig.toBuilder()
                        .webACLId(webAclArn)
                        .build())
                .build();

        return cloudFrontClient.updateDistribution(request);
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

    private static List<String> parseBlockedIps(String blockedIpsArg) {
        List<String> blockedIps = new ArrayList<String>();
        if (blockedIpsArg == null || blockedIpsArg.trim().isEmpty()) {
            return blockedIps;
        }

        String[] candidates = blockedIpsArg.split(",");
        for (String candidate : candidates) {
            String value = candidate == null ? "" : candidate.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (!isValidIpv4Cidr(value)) {
                throw new IllegalArgumentException("非法 CIDR: " + value);
            }
            blockedIps.add(value);
        }
        return blockedIps;
    }

    private static String buildDefaultName(String distributionId, String suffix) {
        String normalizedId = distributionId == null ? "cloudfront" : distributionId.trim();
        if (normalizedId.isEmpty()) {
            normalizedId = "cloudfront";
        }
        return normalizedId + "-" + suffix;
    }

    private static String buildMetricName(String baseName, String suffix) {
        String metricName = sanitizeForMetric(baseName) + "-" + sanitizeForMetric(suffix);
        if (metricName.length() <= DEFAULT_METRIC_NAME_LIMIT) {
            return metricName;
        }
        return metricName.substring(0, DEFAULT_METRIC_NAME_LIMIT);
    }

    private static String sanitizeForMetric(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "metric";
        }
        String sanitized = value.trim().replaceAll("[^A-Za-z0-9_-]", "-");
        return sanitized.isEmpty() ? "metric" : sanitized;
    }

    private static DistributionUpdateContext inspectDistribution(CloudFrontClient cloudFrontClient,
                                                                String distributionId,
                                                                String targetWebAclArn) {
        GetDistributionResponse response = cloudFrontClient.getDistribution(GetDistributionRequest.builder()
                .id(distributionId)
                .build());

        DistributionConfig config = response.distribution().distributionConfig();
        String status = response.distribution().status();
        if (!"Deployed".equalsIgnoreCase(status)) {
            throw new IllegalStateException("Distribution 当前状态为 " + nullSafe(status) + "，请等待部署完成后重试");
        }
        if (!isBlank(config.webACLId()) && !config.webACLId().equals(targetWebAclArn)) {
            throw new IllegalStateException("Distribution 已绑定 Web ACL，如需替换请先手动确认并解除现有绑定: " + config.webACLId());
        }
        if (isBlank(response.eTag())) {
            throw new IllegalStateException("无法获取 Distribution 的 ETag，不能执行更新");
        }

        return new DistributionUpdateContext(
                distributionId,
                response.distribution().domainName(),
                response.eTag(),
                config,
                targetWebAclArn.equals(config.webACLId()));
    }

    private static ResourceRef findExistingIpSet(Wafv2Client wafv2Client, String ipSetName) {
        String nextMarker = null;
        do {
            ListIpSetsRequest request = ListIpSetsRequest.builder()
                    .scope(Scope.CLOUDFRONT)
                    .limit(DEFAULT_LIST_LIMIT)
                    .nextMarker(nextMarker)
                    .build();

            software.amazon.awssdk.services.wafv2.model.ListIpSetsResponse response = wafv2Client.listIPSets(request);

            for (IPSetSummary summary : response.ipSets()) {
                if (ipSetName.equals(summary.name())) {
                    return new ResourceRef(summary.name(), summary.id(), summary.arn());
                }
            }

            nextMarker = response.nextMarker();
        } while (!isBlank(nextMarker));

        return null;
    }

    private static ResourceRef findExistingWebAcl(Wafv2Client wafv2Client, String webAclName) {
        String nextMarker = null;
        do {
            ListWebAcLsRequest request = ListWebAcLsRequest.builder()
                    .scope(Scope.CLOUDFRONT)
                    .limit(DEFAULT_LIST_LIMIT)
                    .nextMarker(nextMarker)
                    .build();

            software.amazon.awssdk.services.wafv2.model.ListWebAcLsResponse response = wafv2Client.listWebACLs(request);

            for (WebACLSummary summary : response.webACLs()) {
                if (webAclName.equals(summary.name())) {
                    return new ResourceRef(summary.name(), summary.id(), summary.arn());
                }
            }

            nextMarker = response.nextMarker();
        } while (!isBlank(nextMarker));

        return null;
    }

    private static void rollbackCreatedResources(Wafv2Client wafv2Client, AcquiredResource webAcl, AcquiredResource ipSet) {
        deleteWebAclQuietly(wafv2Client, webAcl);
        deleteIpSetQuietly(wafv2Client, ipSet);
    }

    private static void deleteWebAclQuietly(Wafv2Client wafv2Client, AcquiredResource webAcl) {
        if (webAcl == null || !webAcl.created) {
            return;
        }
        try {
            GetWebAclResponse response = wafv2Client.getWebACL(GetWebAclRequest.builder()
                    .name(webAcl.resource.name)
                    .id(webAcl.resource.id)
                    .scope(Scope.CLOUDFRONT)
                    .build());

            wafv2Client.deleteWebACL(DeleteWebAclRequest.builder()
                    .name(webAcl.resource.name)
                    .id(webAcl.resource.id)
                    .scope(Scope.CLOUDFRONT)
                    .lockToken(response.lockToken())
                    .build());
            System.err.println("已回滚 Web ACL: " + webAcl.resource.name);
        } catch (Wafv2Exception ex) {
            System.err.println("警告：回滚 Web ACL 失败: " + describeAwsError(ex));
        }
    }

    private static void deleteIpSetQuietly(Wafv2Client wafv2Client, AcquiredResource ipSet) {
        if (ipSet == null || !ipSet.created) {
            return;
        }
        try {
            GetIpSetResponse response = wafv2Client.getIPSet(GetIpSetRequest.builder()
                    .name(ipSet.resource.name)
                    .id(ipSet.resource.id)
                    .scope(Scope.CLOUDFRONT)
                    .build());

            wafv2Client.deleteIPSet(DeleteIpSetRequest.builder()
                    .name(ipSet.resource.name)
                    .id(ipSet.resource.id)
                    .scope(Scope.CLOUDFRONT)
                    .lockToken(response.lockToken())
                    .build());
            System.err.println("已回滚 IPSet: " + ipSet.resource.name);
        } catch (Wafv2Exception ex) {
            System.err.println("警告：回滚 IPSet 失败: " + describeAwsError(ex));
        }
    }

    private static boolean isValidIpv4Cidr(String cidr) {
        if (isBlank(cidr)) {
            return false;
        }
        String[] parts = cidr.split("/");
        if (parts.length != 2) {
            return false;
        }

        String[] octets = parts[0].split("\\.");
        if (octets.length != 4) {
            return false;
        }

        try {
            for (String octet : octets) {
                int value = Integer.parseInt(octet);
                if (value < 0 || value > 255) {
                    return false;
                }
            }

            int prefixLength = Integer.parseInt(parts[1]);
            return prefixLength >= 0 && prefixLength <= 32;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String describeAwsError(Wafv2Exception ex) {
        AwsErrorDetails details = ex.awsErrorDetails();
        if (details == null) {
            return nullSafe(ex.getMessage());
        }
        return nullSafe(details.errorCode()) + ": " + nullSafe(details.errorMessage());
    }

    private static String nullSafe(String value) {
        return value == null ? "-" : value;
    }

    private static final class ResourceRef {
        private final String name;
        private final String id;
        private final String arn;

        private ResourceRef(String name, String id, String arn) {
            this.name = name;
            this.id = id;
            this.arn = arn;
        }
    }

    private static final class AcquiredResource {
        private final ResourceRef resource;
        private final boolean created;

        private AcquiredResource(ResourceRef resource, boolean created) {
            this.resource = resource;
            this.created = created;
        }
    }

    private static final class DistributionUpdateContext {
        private final String distributionId;
        private final String domainName;
        private final String eTag;
        private final DistributionConfig distributionConfig;
        private final boolean alreadyAssociated;

        private DistributionUpdateContext(String distributionId,
                                          String domainName,
                                          String eTag,
                                          DistributionConfig distributionConfig,
                                          boolean alreadyAssociated) {
            this.distributionId = distributionId;
            this.domainName = domainName;
            this.eTag = eTag;
            this.distributionConfig = distributionConfig;
            this.alreadyAssociated = alreadyAssociated;
        }
    }
}