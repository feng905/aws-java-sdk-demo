package com.example.myapp;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.route53.Route53Client;
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
 * Route53 托管区域与记录集查询示例。
 *
 * 参数优先级：命令行参数 > 环境变量 > 代码默认值（空）。
 *
 * 命令行参数：
 * 1) accessKeyId
 * 2) secretAccessKey
 *
 * 环境变量：
 * - AWS_ACCESS_KEY_ID
 * - AWS_SECRET_ACCESS_KEY
 *
 * 运行示例（推荐：环境变量方式）：
 * export AWS_ACCESS_KEY_ID="<YOUR_AK>"
 * export AWS_SECRET_ACCESS_KEY="<YOUR_SK>"
 * mvn -q exec:java -Dexec.mainClass=com.example.myapp.Route53Get
 *
 * 运行示例（命令行参数方式）：
 * mvn -q exec:java -Dexec.mainClass=com.example.myapp.Route53Get -Dexec.args="<AK> <SK>"
 */
public class Route53Get {

    private static final String DEFAULT_ACCESS_KEY_ID = "";
    private static final String DEFAULT_SECRET_ACCESS_KEY = "";

    private static final String ENV_AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
    private static final String ENV_AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";

    private static final String AWS_GLOBAL = "aws-global";

    public static void main(String[] args) {
        String accessKeyId = resolveValue(args, 0, ENV_AWS_ACCESS_KEY_ID, DEFAULT_ACCESS_KEY_ID);
        String secretAccessKey = resolveValue(args, 1, ENV_AWS_SECRET_ACCESS_KEY, DEFAULT_SECRET_ACCESS_KEY);

        if (accessKeyId.isEmpty() || secretAccessKey.isEmpty()) {
            System.err.println("AK/SK 未提供。请通过参数或环境变量提供：AWS_ACCESS_KEY_ID、AWS_SECRET_ACCESS_KEY");
            return;
        }

        AwsBasicCredentials basicCredentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);

        // Route53 是全局服务，使用 aws-global 区域。
        try (Route53Client route53Client = Route53Client.builder()
                .region(Region.of(AWS_GLOBAL))
                .credentialsProvider(StaticCredentialsProvider.create(basicCredentials))
                .build()) {

            List<HostedZone> hostedZones = listAllHostedZones(route53Client);

            System.out.println("=== Route53 托管区域列表 ===");
            System.out.println("托管区域总数: " + hostedZones.size());

            for (int i = 0; i < hostedZones.size(); i++) {
                HostedZone zone = hostedZones.get(i);
                System.out.println("----------------------------------------");
                System.out.println("序号: " + (i + 1));
                System.out.println("区域 ID: " + nullSafe(zone.id()));
                System.out.println("域名: " + nullSafe(zone.name()));
                System.out.println("记录集数量: " + zone.resourceRecordSetCount());
                System.out.println("说明: " + nullSafe(zone.config() != null ? zone.config().comment() : null));

                // 查询该托管区域下的记录集。
                List<ResourceRecordSet> records = listRecordSets(route53Client, zone.id());
                System.out.println("  --- 记录集 ---");
                for (ResourceRecordSet record : records) {
                    System.out.println("  类型: " + nullSafe(record.type() != null ? record.type().toString() : null)
                            + " | 名称: " + nullSafe(record.name())
                            + " | TTL: " + record.ttl());
                    if (record.resourceRecords() != null) {
                        for (ResourceRecord rr : record.resourceRecords()) {
                            System.out.println("    值: " + nullSafe(rr.value()));
                        }
                    }
                }
            }

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

    private static List<HostedZone> listAllHostedZones(Route53Client route53Client) {
        List<HostedZone> allZones = new ArrayList<>();
        String marker = null;

        do {
            ListHostedZonesRequest request = ListHostedZonesRequest.builder()
                    .marker(marker)
                    .build();

            ListHostedZonesResponse response = route53Client.listHostedZones(request);
            allZones.addAll(response.hostedZones());
            marker = response.nextMarker();

        } while (marker != null && !marker.isEmpty());

        return allZones;
    }

    private static List<ResourceRecordSet> listRecordSets(Route53Client route53Client, String hostedZoneId) {
        List<ResourceRecordSet> allRecords = new ArrayList<>();
        String name = null;
        RRType type = null;

        do {
            ListResourceRecordSetsRequest.Builder builder = ListResourceRecordSetsRequest.builder()
                    .hostedZoneId(hostedZoneId);
            if (name != null && type != null) {
                builder.startRecordName(name).startRecordType(type);
            }

            ListResourceRecordSetsResponse response = route53Client.listResourceRecordSets(builder.build());
            allRecords.addAll(response.resourceRecordSets());

            if (response.isTruncated()) {
                name = response.nextRecordName();
                type = response.nextRecordType();
            } else {
                name = null;
                type = null;
            }

        } while (name != null && type != null);

        return allRecords;
    }

    private static String nullSafe(String value) {
        return value == null ? "-" : value;
    }

}
