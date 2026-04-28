package com.example.myapp;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.acm.AcmClient;
import software.amazon.awssdk.services.acm.model.AcmException;
import software.amazon.awssdk.services.acm.model.ImportCertificateRequest;
import software.amazon.awssdk.services.acm.model.ImportCertificateResponse;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * ACM 证书上传示例。
 *
 * 参数优先级：
 * - 命令行参数 > 环境变量 > 代码默认值（空）
 *
 * 命令行参数：
 * 1) accessKeyId
 * 2) secretAccessKey
 * 3) region（可选，默认 us-east-1）
 * 4) certFilePath（证书文件绝对路径，必填）
 * 5) privateKeyFilePath（私钥文件绝对路径，必填）
 * 6) chainFilePath（链证书文件绝对路径，可选，不传或传 - 表示无链证书）
 *
 * 环境变量：
 * - AWS_ACCESS_KEY_ID
 * - AWS_SECRET_ACCESS_KEY
 * - AWS_REGION
 * - ACM_CERT_FILE
 * - ACM_PRIVATE_KEY_FILE
 * - ACM_CERT_CHAIN_FILE
 *
 * 运行示例（推荐：AK/SK/region 用环境变量，文件路径用参数）：
 * export AWS_ACCESS_KEY_ID="<YOUR_AK>"
 * export AWS_SECRET_ACCESS_KEY="<YOUR_SK>"
 * export AWS_REGION="us-east-1"
 * mvn -q exec:java -Dexec.mainClass=com.example.myapp.CertUpload -Dexec.args="_ _ us-east-1 /ABS/PATH/cert.pem /ABS/PATH/privkey.pem -"
 *
 * 运行示例（全部命令行参数方式）：
 * mvn -q exec:java -Dexec.mainClass=com.example.myapp.CertUpload -Dexec.args="<AK> <SK> us-east-1 /ABS/PATH/cert.pem /ABS/PATH/privkey.pem -"
 */
public class CertUpload {

    private static final String DEFAULT_ACCESS_KEY_ID = "";
    private static final String DEFAULT_SECRET_ACCESS_KEY = "";
    private static final String DEFAULT_CERT_FILE_PATH = "";
    private static final String DEFAULT_PRIVATE_KEY_FILE_PATH = "";

    private static final Region DEFAULT_REGION = Region.US_EAST_1;

    private static final String ENV_AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
    private static final String ENV_AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
    private static final String ENV_AWS_REGION = "AWS_REGION";
    private static final String ENV_ACM_CERT_FILE = "ACM_CERT_FILE";
    private static final String ENV_ACM_PRIVATE_KEY_FILE = "ACM_PRIVATE_KEY_FILE";
    private static final String ENV_ACM_CERT_CHAIN_FILE = "ACM_CERT_CHAIN_FILE";

    public static void main(String[] args) {
        String accessKeyId = resolveValue(args, 0, ENV_AWS_ACCESS_KEY_ID, DEFAULT_ACCESS_KEY_ID);
        String secretAccessKey = resolveValue(args, 1, ENV_AWS_SECRET_ACCESS_KEY, DEFAULT_SECRET_ACCESS_KEY);
        String regionArg = resolveValue(args, 2, ENV_AWS_REGION, "");
        String certFilePathArg = resolveValue(args, 3, ENV_ACM_CERT_FILE, DEFAULT_CERT_FILE_PATH);
        String privateKeyFilePathArg = resolveValue(args, 4, ENV_ACM_PRIVATE_KEY_FILE, DEFAULT_PRIVATE_KEY_FILE_PATH);
        String chainFilePathArg = resolveOptionalValue(args, 5, ENV_ACM_CERT_CHAIN_FILE, "");

        if (accessKeyId.isEmpty() || secretAccessKey.isEmpty()) {
            System.err.println("AK/SK 未提供。请通过参数或环境变量提供：AWS_ACCESS_KEY_ID、AWS_SECRET_ACCESS_KEY");
            return;
        }
        if (certFilePathArg.isEmpty()) {
            System.err.println("证书文件路径未提供。请通过参数或环境变量 ACM_CERT_FILE 传入绝对路径");
            return;
        }
        if (privateKeyFilePathArg.isEmpty()) {
            System.err.println("私钥文件路径未提供。请通过参数或环境变量 ACM_PRIVATE_KEY_FILE 传入绝对路径");
            return;
        }
        if (!Paths.get(certFilePathArg).isAbsolute()) {
            System.err.println("证书文件路径必须是绝对路径: " + certFilePathArg);
            return;
        }
        if (!Paths.get(privateKeyFilePathArg).isAbsolute()) {
            System.err.println("私钥文件路径必须是绝对路径: " + privateKeyFilePathArg);
            return;
        }
        if (!chainFilePathArg.isEmpty() && !Paths.get(chainFilePathArg).isAbsolute()) {
            System.err.println("链证书文件路径必须是绝对路径: " + chainFilePathArg);
            return;
        }

        Region region = resolveRegion(regionArg);
        Path certPath = Paths.get(certFilePathArg).toAbsolutePath().normalize();
        Path privateKeyPath = Paths.get(privateKeyFilePathArg).toAbsolutePath().normalize();
        Path chainPath = chainFilePathArg.isEmpty() ? null : Paths.get(chainFilePathArg).toAbsolutePath().normalize();

        if (!Files.exists(certPath) || !Files.isRegularFile(certPath)) {
            System.err.println("证书文件不存在: " + maskPath(certPath));
            return;
        }
        if (!Files.exists(privateKeyPath) || !Files.isRegularFile(privateKeyPath)) {
            System.err.println("私钥文件不存在: " + maskPath(privateKeyPath));
            return;
        }
        if (chainPath != null && (!Files.exists(chainPath) || !Files.isRegularFile(chainPath))) {
            System.err.println("链证书文件不存在: " + maskPath(chainPath));
            return;
        }

        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);

        try (AcmClient acmClient = AcmClient.builder()
                .region(region)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build()) {

            byte[] certBytes = Files.readAllBytes(certPath);
            byte[] privateKeyBytes = Files.readAllBytes(privateKeyPath);

            ImportCertificateRequest.Builder requestBuilder = ImportCertificateRequest.builder()
                    .certificate(SdkBytes.fromByteArray(certBytes))
                    .privateKey(SdkBytes.fromByteArray(privateKeyBytes));

            if (chainPath != null) {
                byte[] chainBytes = Files.readAllBytes(chainPath);
                requestBuilder.certificateChain(SdkBytes.fromByteArray(chainBytes));
            }

            ImportCertificateResponse response = acmClient.importCertificate(requestBuilder.build());

            System.out.println("上传成功");
            System.out.println("区域: " + region.id());
            System.out.println("证书文件: " + maskPath(certPath));
            System.out.println("私钥文件: " + maskPath(privateKeyPath));
            if (chainPath != null) {
                System.out.println("链证书文件: " + maskPath(chainPath));
            }
            System.out.println("证书 ARN: " + nullSafe(response.certificateArn()));

        } catch (AcmException ex) {
            AwsErrorDetails details = ex.awsErrorDetails();
            if (details != null) {
                System.err.println("调用 ACM 上传失败: " + nullSafe(details.errorMessage()));
                System.err.println("错误码: " + nullSafe(details.errorCode()));
            } else {
                System.err.println("调用 ACM 上传失败: " + ex.getMessage());
            }
        } catch (IllegalArgumentException ex) {
            System.err.println("参数错误: " + ex.getMessage());
        } catch (IOException ex) {
            System.err.println("读取证书文件失败: " + ex.getMessage());
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

    private static String resolveOptionalValue(String[] args, int index, String envKey, String defaultValue) {
        String value = resolveValue(args, index, envKey, defaultValue);
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return "";
        }
        return value.trim();
    }

    private static String maskPath(Path path) {
        if (path == null) {
            return "-";
        }
        Path fileName = path.getFileName();
        if (fileName == null) {
            return "-";
        }
        return ".../" + fileName.toString();
    }

    private static String nullSafe(String value) {
        return value == null ? "-" : value;
    }

}
