package com.example.myapp;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.acm.AcmClient;
import software.amazon.awssdk.services.acm.model.CertificateStatus;
import software.amazon.awssdk.services.acm.model.CertificateSummary;
import software.amazon.awssdk.services.acm.model.ListCertificatesRequest;
import software.amazon.awssdk.services.acm.model.ListCertificatesResponse;
import software.amazon.awssdk.services.acm.model.AcmException;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ACM 证书查询示例。
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
 * export AWS_REGION="us-east-1"
 * mvn -q exec:java -Dexec.mainClass=com.example.myapp.CertGet
 *
 * 运行示例（命令行参数方式）：
 * mvn -q exec:java -Dexec.mainClass=com.example.myapp.CertGet -Dexec.args="<AK> <SK> us-east-1"
 */
public class CertGet {

    private static final String DEFAULT_ACCESS_KEY_ID = "";
    private static final String DEFAULT_SECRET_ACCESS_KEY = "";
    private static final Region DEFAULT_REGION = Region.US_EAST_1;

    private static final String ENV_AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
    private static final String ENV_AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
    private static final String ENV_AWS_REGION = "AWS_REGION";
    
    /**
     * 每次分页请求的数量。
     */
    private static final int PAGE_SIZE = 100;

    public static void main(String[] args) {
        String accessKeyId = resolveValue(args, 0, ENV_AWS_ACCESS_KEY_ID, DEFAULT_ACCESS_KEY_ID);
        String secretAccessKey = resolveValue(args, 1, ENV_AWS_SECRET_ACCESS_KEY, DEFAULT_SECRET_ACCESS_KEY);
        String regionArg = resolveValue(args, 2, ENV_AWS_REGION, "");

        if (accessKeyId.isEmpty() || secretAccessKey.isEmpty()) {
            System.err.println("AK/SK 未提供。请通过参数或环境变量提供：AWS_ACCESS_KEY_ID、AWS_SECRET_ACCESS_KEY");
            return;
        }

        Region region = resolveRegion(regionArg);

        AwsBasicCredentials basicCredentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);

        // try-with-resources 会在代码结束时自动关闭客户端，避免资源泄漏。
        try (AcmClient acmClient = AcmClient.builder()
                .region(region)
                .credentialsProvider(StaticCredentialsProvider.create(basicCredentials))
                .build()) {

            List<CertificateSummary> certificates = listAllCertificates(acmClient);

            System.out.println("=== ACM 证书列表（区域: " + region.id() + "）===");
            System.out.println("证书总数: " + certificates.size());

            // 逐条打印关键信息，便于教学演示。
            for (int i = 0; i < certificates.size(); i++) {
                CertificateSummary cert = certificates.get(i);
                System.out.println("----------------------------------------");
                System.out.println("序号: " + (i + 1));
                System.out.println("域名: " + nullSafe(cert.domainName()));
                System.out.println("证书 ARN: " + nullSafe(cert.certificateArn()));
                System.out.println("状态: " + nullSafe(cert.statusAsString()));
                System.out.println("类型: " + nullSafe(cert.typeAsString()));
            }

        } catch (AcmException ex) {
            AwsErrorDetails details = ex.awsErrorDetails();
            if (details != null) {
                System.err.println("调用 ACM 接口失败: " + nullSafe(details.errorMessage()));
                System.err.println("错误码: " + nullSafe(details.errorCode()));
            } else {
                System.err.println("调用 ACM 接口失败: " + ex.getMessage());
            }
        } catch (Exception ex) {
            System.err.println("程序执行失败: " + ex.getMessage());
        }
    }

    private static Region resolveRegion(String regionArg) {
        if (regionArg != null && !regionArg.trim().isEmpty()) {
            return Region.of(regionArg.trim());
        }
        return DEFAULT_REGION;
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

    /**
     * 分页获取当前区域内全部 ACM 证书。
     */
    private static List<CertificateSummary> listAllCertificates(AcmClient acmClient) {
        List<CertificateSummary> allCertificates = new ArrayList<>();
        String nextToken = null;

        // 教学说明：通过 do-while 实现“至少请求一次”，并用 nextToken 控制翻页。
        do {
            ListCertificatesRequest request = ListCertificatesRequest.builder()
                    .maxItems(PAGE_SIZE)
                // 显式列出 ACM 有效状态，确保“所有证书”都能被拉取。
                .certificateStatuses(Arrays.asList(
                    CertificateStatus.PENDING_VALIDATION,
                    CertificateStatus.ISSUED,
                    CertificateStatus.INACTIVE,
                    CertificateStatus.EXPIRED,
                    CertificateStatus.VALIDATION_TIMED_OUT,
                    CertificateStatus.REVOKED,
                    CertificateStatus.FAILED))
                    .nextToken(nextToken)
                    .build();

            ListCertificatesResponse response = acmClient.listCertificates(request);
            allCertificates.addAll(response.certificateSummaryList());
            nextToken = response.nextToken();

        } while (nextToken != null && !nextToken.isEmpty());

        return allCertificates;
    }

    /**
     * 避免输出 null，提升演示可读性。
     */
    private static String nullSafe(String value) {
        return value == null ? "-" : value;
    }

}
