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
 * Route53 加权路由 A 记录更新示例。
 *
 * 先删除该域名下所有现有的加权 A 记录，再创建新的加权 A 记录。
 * 域名末尾不需要加点号，代码会自动处理。
 *
 * 命令行参数：
 * 1) accessKeyId
 * 2) secretAccessKey
 * 3) domainName（域名，例如 www.example.com）
 *
 * 环境变量：
 * - AWS_ACCESS_KEY_ID
 * - AWS_SECRET_ACCESS_KEY
 * - ROUTE53_DOMAIN_NAME
 *
 * 运行示例：
 * export AWS_ACCESS_KEY_ID="<YOUR_AK>"
 * export AWS_SECRET_ACCESS_KEY="<YOUR_SK>"
 * export ROUTE53_DOMAIN_NAME="www.example.com"
 * mvn -q exec:java -Dexec.mainClass=com.example.myapp.Route53Update
 */
public class Route53Update {

    private static final String DEFAULT_ACCESS_KEY_ID = "";
    private static final String DEFAULT_SECRET_ACCESS_KEY = "";

    private static final String ENV_AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
    private static final String ENV_AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
    private static final String ENV_DOMAIN_NAME = "ROUTE53_DOMAIN_NAME";

    private static final String AWS_GLOBAL = "aws-global";

    // ===== 更新后的加权路由配置（按需修改） =====

    private static final String IP_PRIMARY = "192.168.1.100";
    private static final String IP_SECONDARY = "192.168.1.200";

    private static final Long WEIGHT_PRIMARY = 80L;
    private static final Long WEIGHT_SECONDARY = 20L;

    private static final long TTL = 60L;

    private static final String SET_ID_PRIMARY = "server-primary";
    private static final String SET_ID_SECONDARY = "server-secondary";

    public static void main(String[] args) {
        String accessKeyId = resolveValue(args, 0, ENV_AWS_ACCESS_KEY_ID, DEFAULT_ACCESS_KEY_ID);
        String secretAccessKey = resolveValue(args, 1, ENV_AWS_SECRET_ACCESS_KEY, DEFAULT_SECRET_ACCESS_KEY);
        String domainName = resolveValue(args, 2, ENV_DOMAIN_NAME, "");

        if (accessKeyId.isEmpty() || secretAccessKey.isEmpty()) {
            System.err.println("AK/SK 未提供。请通过参数或环境变量提供：AWS_ACCESS_KEY_ID、AWS_SECRET_ACCESS_KEY");
            return;
        }

        if (domainName.isEmpty()) {
            System.err.println("域名未提供。请通过参数(第3个)或环境变量 ROUTE53_DOMAIN_NAME 提供");
            return;
        }

        String fqdn = ensureTrailingDot(domainName);

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

            // 先删除现有 A 记录。
            for (ResourceRecordSet record : existingRecords) {
                System.out.println("[删除] " + record.name()
                        + " setIdentifier=" + nullSafe(record.setIdentifier())
                        + " weight=" + record.weight()
                        + " values=" + formatRecordValues(record));
                changes.add(Change.builder()
                        .action(ChangeAction.DELETE)
                        .resourceRecordSet(record)
                        .build());
            }

            // 再创建新的加权 A 记录。
            ResourceRecordSet primaryRecord = ResourceRecordSet.builder()
                    .name(fqdn)
                    .type(RRType.A)
                    .ttl(TTL)
                    .weight(WEIGHT_PRIMARY)
                    .setIdentifier(SET_ID_PRIMARY)
                    .resourceRecords(ResourceRecord.builder().value(IP_PRIMARY).build())
                    .build();

            ResourceRecordSet secondaryRecord = ResourceRecordSet.builder()
                    .name(fqdn)
                    .type(RRType.A)
                    .ttl(TTL)
                    .weight(WEIGHT_SECONDARY)
                    .setIdentifier(SET_ID_SECONDARY)
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

            System.out.println("[新增] " + fqdn + " -> " + IP_PRIMARY + " (权重: " + WEIGHT_PRIMARY + ")");
            System.out.println("[新增] " + fqdn + " -> " + IP_SECONDARY + " (权重: " + WEIGHT_SECONDARY + ")");

            ChangeResourceRecordSetsRequest request = ChangeResourceRecordSetsRequest.builder()
                    .hostedZoneId(hostedZoneId)
                    .changeBatch(cb -> cb.changes(changes))
                    .build();

            ChangeResourceRecordSetsResponse response = route53Client.changeResourceRecordSets(request);

            System.out.println("=== Route53 加权路由 A 记录更新成功 ===");
            System.out.println("域名: " + fqdn);
            System.out.println("托管区域 ID: " + hostedZoneId);
            System.out.println("删除记录数: " + existingRecords.size());
            System.out.println("新增记录数: 2");
            System.out.println("主 IP: " + IP_PRIMARY + " (权重: " + WEIGHT_PRIMARY + ")");
            System.out.println("备 IP: " + IP_SECONDARY + " (权重: " + WEIGHT_SECONDARY + ")");
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

    private static String nullSafe(String value) {
        return value == null ? "-" : value;
    }

}
