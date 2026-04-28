package com.example.myapp;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.*;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;

/**
 * CloudFront Distribution 创建示例。
 *
 * 参数优先级：命令行参数 > 环境变量 > 代码默认值（空）。
 *
 * 命令行参数：
 * 1) accessKeyId
 * 2) secretAccessKey
 * 3) region（可选，默认 us-east-1）
 * 4) domainName（自定义域名，必填）
 * 5) acmCertArn（ACM 证书 ARN，必填）
 * 6) originDomainName（源站域名，必填）
 * 7) originId（源站 ID，必填）
 *
 * 环境变量：
 * - AWS_ACCESS_KEY_ID
 * - AWS_SECRET_ACCESS_KEY
 * - AWS_REGION
 * - CF_DOMAIN_NAME
 * - CF_ACM_CERT_ARN
 * - CF_ORIGIN_DOMAIN_NAME
 * - CF_ORIGIN_ID
 *
 * 运行示例（推荐：环境变量方式）：
 * export AWS_ACCESS_KEY_ID="<YOUR_AK>"
 * export AWS_SECRET_ACCESS_KEY="<YOUR_SK>"
 * export AWS_REGION="us-east-1"
 * export CF_DOMAIN_NAME="cdn.example.com"
 * export CF_ACM_CERT_ARN="arn:aws:acm:us-east-1:123456789012:certificate/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
 * export CF_ORIGIN_DOMAIN_NAME="origin.example.com"
 * export CF_ORIGIN_ID="my-origin"
 * mvn -q exec:java -Dexec.mainClass=com.example.myapp.DomainCreate
 */
public class DomainCreate {

    private static final String DEFAULT_ACCESS_KEY_ID = "";
    private static final String DEFAULT_SECRET_ACCESS_KEY = "";
    private static final Region DEFAULT_REGION = Region.US_EAST_1;

    private static final String ENV_AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
    private static final String ENV_AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
    private static final String ENV_AWS_REGION = "AWS_REGION";
    private static final String ENV_CF_DOMAIN_NAME = "CF_DOMAIN_NAME";
    private static final String ENV_CF_ACM_CERT_ARN = "CF_ACM_CERT_ARN";
    private static final String ENV_CF_ORIGIN_DOMAIN_NAME = "CF_ORIGIN_DOMAIN_NAME";
    private static final String ENV_CF_ORIGIN_ID = "CF_ORIGIN_ID";

    public static void main(String[] args) {
        String accessKeyId = resolveValue(args, 0, ENV_AWS_ACCESS_KEY_ID, DEFAULT_ACCESS_KEY_ID);
        String secretAccessKey = resolveValue(args, 1, ENV_AWS_SECRET_ACCESS_KEY, DEFAULT_SECRET_ACCESS_KEY);
        String regionArg = resolveValue(args, 2, ENV_AWS_REGION, "");
        String domainName = resolveValue(args, 3, ENV_CF_DOMAIN_NAME, "");
        String acmCertArn = resolveValue(args, 4, ENV_CF_ACM_CERT_ARN, "");
        String originDomainName = resolveValue(args, 5, ENV_CF_ORIGIN_DOMAIN_NAME, "");
        String originId = resolveValue(args, 6, ENV_CF_ORIGIN_ID, "");

        if (accessKeyId.isEmpty() || secretAccessKey.isEmpty()) {
            System.err.println("AK/SK 未提供。请通过参数或环境变量提供：AWS_ACCESS_KEY_ID、AWS_SECRET_ACCESS_KEY");
            return;
        }
        if (domainName.isEmpty() || acmCertArn.isEmpty() || originDomainName.isEmpty() || originId.isEmpty()) {
            System.err.println("参数不完整。请提供域名、ACM 证书 ARN、源站域名和源站 ID");
            return;
        }

        Region region = resolveRegion(regionArg);
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);

        try (CloudFrontClient cloudFrontClient = CloudFrontClient.builder()
                .region(region)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build()) {

            Origin origin = Origin.builder()
                    .domainName(originDomainName)
                    .id(originId)
                    .customOriginConfig(CustomOriginConfig.builder()
                            .httpPort(80)
                            .httpsPort(443)
                            .originProtocolPolicy(OriginProtocolPolicy.HTTPS_ONLY)
                            .originSslProtocols(OriginSslProtocols.builder()
                                    .quantity(1)
                                    .items(SslProtocol.TLS_V1_2)
                                    .build())
                            .build())
                    .build();

            DefaultCacheBehavior defaultCacheBehavior = DefaultCacheBehavior.builder()
                    .targetOriginId(originId)
                    .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                    .allowedMethods(AllowedMethods.builder()
                            .quantity(3)
                            .items(Method.HEAD, Method.GET, Method.OPTIONS)
                            .build())
                    .cachePolicyId("658327ea-f89d-4fab-a63d-7e88639e58f6")
                    .compress(true)
                    .build();

            ViewerCertificate viewerCertificate = ViewerCertificate.builder()
                    .acmCertificateArn(acmCertArn)
                    .sslSupportMethod(SSLSupportMethod.SNI_ONLY)
                    .minimumProtocolVersion(MinimumProtocolVersion.TLS_V1_2_2021)
                    .build();

            Aliases aliases = Aliases.builder()
                    .quantity(1)
                    .items(domainName)
                    .build();

            CreateDistributionRequest request = CreateDistributionRequest.builder()
                    .distributionConfig(DistributionConfig.builder()
                            .callerReference(System.currentTimeMillis() + "")
                            .comment("Created by DomainCreate")
                            .enabled(true)
                            .httpVersion(HttpVersion.HTTP2)
                            .origins(Origins.builder()
                                    .quantity(1)
                                    .items(origin)
                                    .build())
                            .defaultCacheBehavior(defaultCacheBehavior)
                            .viewerCertificate(viewerCertificate)
                            .aliases(aliases)
                            .priceClass(PriceClass.PRICE_CLASS_200)
                            .build())
                    .build();

            CreateDistributionResponse response = cloudFrontClient.createDistribution(request);
            Distribution distribution = response.distribution();

            System.out.println("创建成功");
            System.out.println("区域: " + region.id());
            System.out.println("自定义域名: " + nullSafe(domainName));
            System.out.println("源站域名: " + nullSafe(originDomainName));
            System.out.println("Distribution ID: " + nullSafe(distribution.id()));
            System.out.println("Distribution 域名: " + nullSafe(distribution.domainName()));
            System.out.println("状态: " + nullSafe(distribution.status()));
            System.out.println("提示：Distribution 完全部署可能需要 15-30 分钟");

        } catch (CloudFrontException ex) {
            AwsErrorDetails details = ex.awsErrorDetails();
            if (details != null) {
                System.err.println("调用 CloudFront 创建失败: " + nullSafe(details.errorMessage()));
                System.err.println("错误码: " + nullSafe(details.errorCode()));
            } else {
                System.err.println("调用 CloudFront 创建失败: " + ex.getMessage());
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
