package com.example.myapp;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.route53.model.Change;
import software.amazon.awssdk.services.route53.model.ChangeAction;
import software.amazon.awssdk.services.route53.model.ChangeResourceRecordSetsRequest;
import software.amazon.awssdk.services.route53.model.ChangeResourceRecordSetsResponse;
import software.amazon.awssdk.services.route53.model.HostedZone;
import software.amazon.awssdk.services.route53.model.ResourceRecordSetFailover;
import software.amazon.awssdk.services.route53.model.ListHostedZonesRequest;
import software.amazon.awssdk.services.route53.model.ListHostedZonesResponse;
import software.amazon.awssdk.services.route53.model.ListResourceRecordSetsRequest;
import software.amazon.awssdk.services.route53.model.ListResourceRecordSetsResponse;
import software.amazon.awssdk.services.route53.model.RRType;
import software.amazon.awssdk.services.route53.model.ResourceRecord;
import software.amazon.awssdk.services.route53.model.ResourceRecordSet;
import software.amazon.awssdk.services.route53.model.Route53Exception;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;

import java.util.ArrayList;
import java.util.List;

/**
 * Route53 故障转移（主备）路由 A 记录更新示例。
 *
 * 将该域名下现有的加权 A 记录转换为故障转移（主备）路由：
 * 先删除现有加权/路由策略记录（仅删除带 setIdentifier 的记录，同名简单 A 记录保留不动），
 * 再创建 PRIMARY 和 SECONDARY 两条故障转移记录，全部在一次 changeResourceRecordSets
 * 调用内原子完成，不影响其他域名。
 *
 * 注意：要使主备真正自动切换，PRIMARY 记录必须关联健康检查（healthCheckId）。
 * 当主记录健康检查失败时，Route53 才会把流量切到 SECONDARY。
 *
 * 域名末尾不需要加点号，代码会自动处理。
 *
 * 命令行参数：
 * 1) accessKeyId
 * 2) secretAccessKey
 * 3) domainName（域名，例如 testvivo001.rgslb.link）
 * 4) healthCheckId（主记录的健康检查 ID；可省略，但省略后不会真正触发主备切换）
 *
 * 环境变量：
 * - AWS_ACCESS_KEY_ID
 * - AWS_SECRET_ACCESS_KEY
 * - ROUTE53_DOMAIN_NAME
 * - ROUTE53_HEALTH_CHECK_ID
 *
 * 运行示例：
 * export AWS_ACCESS_KEY_ID="<YOUR_AK>"
 * export AWS_SECRET_ACCESS_KEY="<YOUR_SK>"
 * export ROUTE53_DOMAIN_NAME="testvivo001.rgslb.link"
 * export ROUTE53_HEALTH_CHECK_ID="<health-check-id>"
 * mvn -q exec:java -Dexec.mainClass=com.example.myapp.Route53Update3
 */
public class Route53Update3 {

    private static final String DEFAULT_ACCESS_KEY_ID = "";
    private static final String DEFAULT_SECRET_ACCESS_KEY = "";

    private static final String ENV_AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
    private static final String ENV_AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
    private static final String ENV_DOMAIN_NAME = "ROUTE53_DOMAIN_NAME";
    private static final String ENV_HEALTH_CHECK_ID = "ROUTE53_HEALTH_CHECK_ID";

    private static final String AWS_GLOBAL = "aws-global";

    // ===== 故障转移（主备）配置（按需修改） =====

    private static final String IP_PRIMARY = "192.168.1.100";
    private static final String IP_SECONDARY = "192.168.1.200";

    private static final long TTL = 60L;

    private static final String SET_ID_PRIMARY = "server-primary";
    private static final String SET_ID_SECONDARY = "server-secondary";

    public static void main(String[] args) {
        String accessKeyId = resolveValue(args, 0, ENV_AWS_ACCESS_KEY_ID, DEFAULT_ACCESS_KEY_ID);
        String secretAccessKey = resolveValue(args, 1, ENV_AWS_SECRET_ACCESS_KEY, DEFAULT_SECRET_ACCESS_KEY);
        String domainName = resolveValue(args, 2, ENV_DOMAIN_NAME, "");
        String healthCheckId = resolveValue(args, 3, ENV_HEALTH_CHECK_ID, "");

        if (accessKeyId.isEmpty() || secretAccessKey.isEmpty()) {
            System.err.println("AK/SK 未提供。请通过参数或环境变量提供：AWS_ACCESS_KEY_ID、AWS_SECRET_ACCESS_KEY");
            return;
        }

        if (domainName.isEmpty()) {
            System.err.println("域名未提供。请通过参数(第3个)或环境变量 ROUTE53_DOMAIN_NAME 提供");
            return;
        }

        String fqdn = ensureTrailingDot(domainName);

        if (healthCheckId.isEmpty()) {
            System.err.println("警告：未提供健康检查 ID（参数第4个或环境变量 ROUTE53_HEALTH_CHECK_ID）。");
            System.err.println("      主记录将不带健康检查，Route53 会视主记录始终健康，不会自动切换到备。");
        }

        AwsBasicCredentials basicCredentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);

        try (Route53Client route53Client = Route53Client.builder()
                .region(Region.of(AWS_GLOBAL))
                .credentialsProvider(StaticCredentialsProvider.create(basicCredentials))
                .build()) {

            String hostedZoneId = findHostedZoneId(route53Client, domainName);
            if (hostedZoneId == null) {
                System.err.println("未找到匹配域名 " + domainName + " 的托管区域");
                return;
            }

            // 查找现有的 A 记录。
            List<ResourceRecordSet> existingRecords = findARecords(route53Client, hostedZoneId, fqdn);

            List<Change> changes = new ArrayList<>();
            int deletedCount = 0;

            // 先删除现有加权/路由策略 A 记录（带 setIdentifier 的）。
            // 同名的简单 A 记录（无 setIdentifier）保留不动。
            for (ResourceRecordSet record : existingRecords) {
                if (record.setIdentifier() == null) {
                    continue;
                }
                System.out.println("[删除] " + record.name()
                        + " setIdentifier=" + record.setIdentifier()
                        + " weight=" + nullSafe(record.weight())
                        + " failover=" + nullSafe(record.failover())
                        + " values=" + formatRecordValues(record));
                changes.add(Change.builder()
                        .action(ChangeAction.DELETE)
                        .resourceRecordSet(record)
                        .build());
                deletedCount++;
            }

            // 再创建故障转移（主备）A 记录。
            ResourceRecordSet.Builder primaryBuilder = ResourceRecordSet.builder()
                    .name(fqdn)
                    .type(RRType.A)
                    .ttl(TTL)
                    .setIdentifier(SET_ID_PRIMARY)
                    .failover(ResourceRecordSetFailover.PRIMARY)
                    .resourceRecords(ResourceRecord.builder().value(IP_PRIMARY).build());
            if (!healthCheckId.isEmpty()) {
                primaryBuilder.healthCheckId(healthCheckId);
            }
            ResourceRecordSet primaryRecord = primaryBuilder.build();

            ResourceRecordSet secondaryRecord = ResourceRecordSet.builder()
                    .name(fqdn)
                    .type(RRType.A)
                    .ttl(TTL)
                    .setIdentifier(SET_ID_SECONDARY)
                    .failover(ResourceRecordSetFailover.SECONDARY)
                    .resourceRecords(ResourceRecord.builder().value(IP_SECONDARY).build())
                    .build();

            changes.add(Change.builder()
                    .action(ChangeAction.CREATE)
                    .resourceRecordSet(primaryRecord)
                    .build());
            changes.add(Change.builder()
                    .action(ChangeAction.CREATE)
                    .resourceRecordSet(secondaryRecord)
                    .build());

            System.out.println("[新增] " + fqdn + " -> " + IP_PRIMARY
                    + " (PRIMARY, healthCheck=" + nullSafe(healthCheckId) + ")");
            System.out.println("[新增] " + fqdn + " -> " + IP_SECONDARY + " (SECONDARY)");

            ChangeResourceRecordSetsRequest request = ChangeResourceRecordSetsRequest.builder()
                    .hostedZoneId(hostedZoneId)
                    .changeBatch(cb -> cb.changes(changes))
                    .build();

            ChangeResourceRecordSetsResponse response = route53Client.changeResourceRecordSets(request);

            System.out.println("=== Route53 故障转移（主备）路由 A 记录更新成功 ===");
            System.out.println("域名: " + fqdn);
            System.out.println("托管区域 ID: " + hostedZoneId);
            System.out.println("删除记录数: " + deletedCount);
            System.out.println("新增记录数: 2");
            System.out.println("主 IP (PRIMARY): " + IP_PRIMARY + " 健康检查: " + nullSafe(healthCheckId));
            System.out.println("备 IP (SECONDARY): " + IP_SECONDARY);
            System.out.println("变更 ID: " + response.changeInfo().id());
            System.out.println("变更状态: " + response.changeInfo().status());

        } catch (Route53Exception ex) {
            AwsErrorDetails details = ex.awsErrorDetails();
            if (details != null) {
                System.err.println("调用 Route53 接口失败: " + nullSafe(details.errorMessage()));
                System.err.println("错误码: " + nullSafe(details.errorCode()));
            } else {
                System.err.println("调用 Route53 接口失败: " + ex.getMessage());
            }
        } catch (Exception ex) {
            System.err.println("程序执行失败: " + ex.getMessage());
        }
    }

    private static String ensureTrailingDot(String domain) {
        if (domain.endsWith(".")) {
            return domain;
        }
        return domain + ".";
    }

    private static String extractZoneDomain(String domain) {
        String normalized = domain.endsWith(".") ? domain.substring(0, domain.length() - 1) : domain;
        String[] parts = normalized.split("\\.");
        if (parts.length >= 2) {
            return parts[parts.length - 2] + "." + parts[parts.length - 1] + ".";
        }
        return normalized + ".";
    }

    private static String findHostedZoneId(Route53Client client, String domain) {
        List<HostedZone> zones = listAllHostedZones(client);
        String fqdn = ensureTrailingDot(domain);

        for (HostedZone zone : zones) {
            if (zone.name().equalsIgnoreCase(fqdn)) {
                return zone.id();
            }
        }

        String zoneDomain = extractZoneDomain(domain);
        for (HostedZone zone : zones) {
            if (zone.name().equalsIgnoreCase(zoneDomain)) {
                return zone.id();
            }
        }

        return null;
    }

    private static List<ResourceRecordSet> findARecords(Route53Client client, String hostedZoneId, String fqdn) {
        List<ResourceRecordSet> matched = new ArrayList<>();
        String name = null;
        RRType type = null;

        do {
            ListResourceRecordSetsRequest.Builder builder = ListResourceRecordSetsRequest.builder()
                    .hostedZoneId(hostedZoneId);
            if (name != null && type != null) {
                builder.startRecordName(name).startRecordType(type);
            }

            ListResourceRecordSetsResponse response = client.listResourceRecordSets(builder.build());

            for (ResourceRecordSet record : response.resourceRecordSets()) {
                if (record.type() == RRType.A && record.name().equalsIgnoreCase(fqdn)) {
                    matched.add(record);
                }
            }

            if (response.isTruncated()) {
                name = response.nextRecordName();
                type = response.nextRecordType();
            } else {
                name = null;
                type = null;
            }

        } while (name != null && type != null);

        return matched;
    }

    private static List<HostedZone> listAllHostedZones(Route53Client client) {
        List<HostedZone> allZones = new ArrayList<>();
        String marker = null;

        do {
            ListHostedZonesRequest request = ListHostedZonesRequest.builder()
                    .marker(marker)
                    .build();

            ListHostedZonesResponse response = client.listHostedZones(request);
            allZones.addAll(response.hostedZones());
            marker = response.nextMarker();

        } while (marker != null && !marker.isEmpty());

        return allZones;
    }

    private static String formatRecordValues(ResourceRecordSet record) {
        if (record.resourceRecords() == null || record.resourceRecords().isEmpty()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < record.resourceRecords().size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(record.resourceRecords().get(i).value());
        }
        return sb.toString();
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

    private static String nullSafe(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

}
