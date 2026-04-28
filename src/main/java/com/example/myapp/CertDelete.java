package com.example.myapp;

/**
 * ACM 证书删除示例。
 *
 * 参数优先级：命令行参数 > 环境变量 > 代码默认值（空）。
 *
 * 命令行参数：
 * 1) accessKeyId
 * 2) secretAccessKey
 * 3) region（可选，默认 us-east-1）
 * 4) certificateArn（要删除的证书 ARN，必填）
 *
 * 环境变量：
 * - AWS_ACCESS_KEY_ID
 * - AWS_SECRET_ACCESS_KEY
 * - AWS_REGION
 * - ACM_CERT_ARN
 *
 * 运行示例（推荐：环境变量方式）：
 * export AWS_ACCESS_KEY_ID="<YOUR_AK>"
 * export AWS_SECRET_ACCESS_KEY="<YOUR_SK>"
 * export AWS_REGION="us-east-1"
 * export ACM_CERT_ARN="arn:aws:acm:us-east-1:123456789012:certificate/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
 * mvn -q exec:java -Dexec.mainClass=com.example.myapp.CertDelete
 *
 * 运行示例（命令行参数方式）：
 * mvn -q exec:java -Dexec.mainClass=com.example.myapp.CertDelete -Dexec.args="<AK> <SK> us-east-1 arn:aws:acm:us-east-1:123456789012:certificate/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
 *
 * 注意：如果证书正在被 CloudFront/ALB 等资源使用，删除会失败。
 */
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.acm.AcmClient;
import software.amazon.awssdk.services.acm.model.AcmException;
import software.amazon.awssdk.services.acm.model.DeleteCertificateRequest;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;

public class CertDelete {

	private static final String DEFAULT_ACCESS_KEY_ID = "";
	private static final String DEFAULT_SECRET_ACCESS_KEY = "";
	private static final String DEFAULT_CERT_ARN = "";
	private static final Region DEFAULT_REGION = Region.US_EAST_1;

	private static final String ENV_AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
	private static final String ENV_AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
	private static final String ENV_AWS_REGION = "AWS_REGION";
	private static final String ENV_ACM_CERT_ARN = "ACM_CERT_ARN";

	public static void main(String[] args) {
		String accessKeyId = resolveValue(args, 0, ENV_AWS_ACCESS_KEY_ID, DEFAULT_ACCESS_KEY_ID);
		String secretAccessKey = resolveValue(args, 1, ENV_AWS_SECRET_ACCESS_KEY, DEFAULT_SECRET_ACCESS_KEY);
		String regionArg = resolveValue(args, 2, ENV_AWS_REGION, "");
		String certificateArn = resolveValue(args, 3, ENV_ACM_CERT_ARN, DEFAULT_CERT_ARN);

		if (accessKeyId.isEmpty() || secretAccessKey.isEmpty()) {
			System.err.println("AK/SK 未提供。请通过参数或环境变量提供：AWS_ACCESS_KEY_ID、AWS_SECRET_ACCESS_KEY");
			return;
		}
		if (certificateArn.isEmpty()) {
			System.err.println("证书 ARN 未提供。请通过参数或环境变量提供：ACM_CERT_ARN");
			return;
		}

		Region region = resolveRegion(regionArg);
		AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);

		try (AcmClient acmClient = AcmClient.builder()
				.region(region)
				.credentialsProvider(StaticCredentialsProvider.create(credentials))
				.build()) {

			DeleteCertificateRequest request = DeleteCertificateRequest.builder()
					.certificateArn(certificateArn)
					.build();

			acmClient.deleteCertificate(request);

			System.out.println("删除成功");
			System.out.println("区域: " + region.id());
			System.out.println("证书 ARN(脱敏): " + maskArn(certificateArn));

		} catch (AcmException ex) {
			AwsErrorDetails details = ex.awsErrorDetails();
			if (details != null) {
				System.err.println("调用 ACM 删除失败: " + nullSafe(details.errorMessage()));
				System.err.println("错误码: " + nullSafe(details.errorCode()));
			} else {
				System.err.println("调用 ACM 删除失败: " + ex.getMessage());
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
		if (args != null && args.length > index && args[index] != null && !args[index].trim().isEmpty()) {
			return args[index].trim();
		}
		String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.trim().isEmpty()) {
			return envValue.trim();
		}
		return defaultValue;
	}

	private static String maskArn(String arn) {
		if (arn == null || arn.trim().isEmpty()) {
			return "-";
		}
		String trimmed = arn.trim();
		if (trimmed.length() <= 16) {
			return "****";
		}
		return trimmed.substring(0, 12) + "****" + trimmed.substring(trimmed.length() - 8);
	}

	private static String nullSafe(String value) {
		return value == null ? "-" : value;
	}

}
