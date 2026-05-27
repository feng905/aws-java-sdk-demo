# AWS Infrastructure Management Tools

Standalone Java CLI tools for managing AWS infrastructure — ACM certificates, CloudFront distributions and policies, and Route53 DNS records.

## Prerequisites

- Java 8+
- Apache Maven
- [AWS SAM CLI](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-install.html) (for deployment)
- Docker (for local SAM testing)

## Build

```bash
mvn clean install
```

## Available Commands

<!-- AUTO-GENERATED -->
| Command | Description |
|---------|-------------|
| `mvn clean install` | Build with tests |
| `mvn clean package` | Build, skip tests |
| `mvn test` | Run tests |
| `mvn -q exec:java -Dexec.mainClass=com.example.myapp.<Class>` | Run a specific tool |
| `sam local invoke` | Test Lambda locally (requires Docker) |
| `sam deploy --guided` | First-time SAM deployment |
| `sam deploy` | Subsequent deployments |
<!-- /AUTO-GENERATED -->

## Tools

### ACM Certificate Management

```bash
# Import certificate
mvn -q exec:java -Dexec.mainClass=com.example.myapp.CertUpload \
  -Dexec.args="<accessKey> <secretKey> <region> <certPath> <keyPath> [chainPath]"

# List certificates
mvn -q exec:java -Dexec.mainClass=com.example.myapp.CertGet \
  -Dexec.args="<accessKey> <secretKey> [region]"

# Delete certificate
mvn -q exec:java -Dexec.mainClass=com.example.myapp.CertDelete \
  -Dexec.args="<accessKey> <secretKey> [region] <certArn>"
```

### CloudFront Distribution Management

```bash
# Create distribution
mvn -q exec:java -Dexec.mainClass=com.example.myapp.DomainCreate \
  -Dexec.args="<accessKey> <secretKey> <region> <domainName> <acmCertArn> <originDomain> <originId>"

# List distributions
mvn -q exec:java -Dexec.mainClass=com.example.myapp.DomainGet \
  -Dexec.args="<accessKey> <secretKey> [region]"

# Update distribution (enable/disable, comment)
mvn -q exec:java -Dexec.mainClass=com.example.myapp.DomainUpdate \
  -Dexec.args="<accessKey> <secretKey> <region> <distributionId> [enabled] [comment]"

# Delete distribution (must be disabled first)
mvn -q exec:java -Dexec.mainClass=com.example.myapp.DomainDelete \
  -Dexec.args="<accessKey> <secretKey> <region> <distributionId>"
```

### AWS WAF For CloudFront

```bash
# Create an IP blacklist Web ACL and attach it to a CloudFront distribution
mvn -q exec:java -Dexec.mainClass=com.example.myapp.WafCreate \
  -Dexec.args="<accessKey> <secretKey> [region] <distributionId> [webAclName] [ipSetName] [blockedIps]"
```

Note: CloudFront 使用的 AWS WAF 必须部署在 `us-east-1`。`blockedIps` 默认示例值为 `10.0.0.0/32,192.168.0.0/32`。

### CloudFront Cache Policy

```bash
# Create / Get / Update / Delete
mvn -q exec:java -Dexec.mainClass=com.example.myapp.CacheCustomPolicyCreate \
  -Dexec.args="<accessKey> <secretKey> [region]"
mvn -q exec:java -Dexec.mainClass=com.example.myapp.CacheCustomPolicyGet \
  -Dexec.args="<accessKey> <secretKey> [region]"
mvn -q exec:java -Dexec.mainClass=com.example.myapp.CacheCustomPolicyUpdate \
  -Dexec.args="<accessKey> <secretKey> [region]"
mvn -q exec:java -Dexec.mainClass=com.example.myapp.CacheCustomPolicyDelete \
  -Dexec.args="<accessKey> <secretKey> [region]"
```

### CloudFront Origin Request Policy

```bash
# Create / Get / Update / Delete
mvn -q exec:java -Dexec.mainClass=com.example.myapp.OriginRequestCustomPolicyCreate \
  -Dexec.args="<accessKey> <secretKey> [region]"
mvn -q exec:java -Dexec.mainClass=com.example.myapp.OriginRequestCustomPolicyGet \
  -Dexec.args="<accessKey> <secretKey> [region]"
mvn -q exec:java -Dexec.mainClass=com.example.myapp.OriginRequestCustomPolicyUpdate \
  -Dexec.args="<accessKey> <secretKey> [region]"
mvn -q exec:java -Dexec.mainClass=com.example.myapp.OriginRequestCustomPolicyDelete \
  -Dexec.args="<accessKey> <secretKey> [region]"
```

### CloudFront Response Headers Policy

```bash
# Create / Get / Update / Delete
mvn -q exec:java -Dexec.mainClass=com.example.myapp.ResponseHeaderCustomPolicyCreate \
  -Dexec.args="<accessKey> <secretKey> [region]"
mvn -q exec:java -Dexec.mainClass=com.example.myapp.ResponseHeaderCustomPolicyGet \
  -Dexec.args="<accessKey> <secretKey> [region]"
mvn -q exec:java -Dexec.mainClass=com.example.myapp.ResponseHeaderCustomPolicyUpdate \
  -Dexec.args="<accessKey> <secretKey> [region]"
mvn -q exec:java -Dexec.mainClass=com.example.myapp.ResponseHeaderCustomPolicyDelete \
  -Dexec.args="<accessKey> <secretKey> [region]"
```

### Route53 DNS Management

```bash
# List hosted zones and records
mvn -q exec:java -Dexec.mainClass=com.example.myapp.Route53Get \
  -Dexec.args="<accessKey> <secretKey>"

# Create weighted A records (70/30 split)
mvn -q exec:java -Dexec.mainClass=com.example.myapp.Route53Create \
  -Dexec.args="<accessKey> <secretKey> <domainName>"

# Update weighted A records (80/20 split)
mvn -q exec:java -Dexec.mainClass=com.example.myapp.Route53Update \
  -Dexec.args="<accessKey> <secretKey> <domainName>"

# Delete A records for a domain
mvn -q exec:java -Dexec.mainClass=com.example.myapp.Route53Delete \
  -Dexec.args="<accessKey> <secretKey> <domainName>"
```

## Environment Variables

<!-- AUTO-GENERATED -->
### Shared (all tools)

| Variable | Required | Description | Default |
|----------|----------|-------------|---------|
| `AWS_ACCESS_KEY_ID` | Yes | AWS access key | — |
| `AWS_SECRET_ACCESS_KEY` | Yes | AWS secret key | — |
| `AWS_REGION` | No | AWS region | `us-east-1` |

### ACM — CertUpload

| Variable | Required | Description |
|----------|----------|-------------|
| `ACM_CERT_FILE` | Yes | Certificate PEM file path (absolute) |
| `ACM_PRIVATE_KEY_FILE` | Yes | Private key PEM file path (absolute) |
| `ACM_CERT_CHAIN_FILE` | No | Certificate chain PEM file path |

### ACM — CertDelete

| Variable | Required | Description |
|----------|----------|-------------|
| `ACM_CERT_ARN` | Yes | Certificate ARN to delete |

### CloudFront Distribution — DomainCreate

| Variable | Required | Description |
|----------|----------|-------------|
| `CF_DOMAIN_NAME` | Yes | Custom domain name for the distribution |
| `CF_ACM_CERT_ARN` | Yes | ACM certificate ARN for HTTPS |
| `CF_ORIGIN_DOMAIN_NAME` | Yes | Origin server domain |
| `CF_ORIGIN_ID` | Yes | Origin identifier |

### CloudFront Distribution — DomainDelete / DomainUpdate

| Variable | Required | Description |
|----------|----------|-------------|
| `CF_DISTRIBUTION_ID` | Yes | CloudFront distribution ID |

### AWS WAF For CloudFront — WafCreate

| Variable | Required | Description | Default |
|----------|----------|-------------|---------|
| `CF_DISTRIBUTION_ID` | Yes | Target CloudFront distribution ID | — |
| `WAF_WEB_ACL_NAME` | No | Web ACL name | `<distributionId>-BlacklistWebAcl` |
| `WAF_IP_SET_NAME` | No | IPSet name | `<distributionId>-BlockedIpSet` |
| `WAF_BLOCKED_IPS` | No | Comma-separated IPv4 CIDRs | `10.0.0.0/32,192.168.0.0/32` |

### CloudFront Distribution — DomainUpdate (additional)

| Variable | Required | Description |
|----------|----------|-------------|
| `CF_ENABLED` | No | `true` or `false` to enable/disable |
| `CF_COMMENT` | No | Distribution comment |
| `CF_ENABLE_IP_BLACKLIST` | No | `true` to enable CloudFront Function based IP blacklist (default `false`) |

### Route53 — Create / Update / Delete

| Variable | Required | Description |
|----------|----------|-------------|
| `ROUTE53_DOMAIN_NAME` | Yes | Domain name for A record operations |
<!-- /AUTO-GENERATED -->

## Deployment

The SAM template (`template.yaml`) deploys a Lambda function. Adjust `Runtime`, `Handler`, `MemorySize`, and `Timeout` as needed.

```bash
sam deploy --guided    # First deploy
sam deploy             # Subsequent deploys
```

## Notes

- All tools support both CLI arguments and environment variables; CLI args take priority
- Certificate file paths must be absolute
- Certificate chain is optional for `CertUpload` (omit or use `-`)
- `DomainDelete` refuses to delete an enabled distribution
- Route53 tools automatically handle trailing dots on FQDNs
- Default policy names: `ExampleCustomCachePolicy`, `ExampleCustomOriginRequestPolicy`, `ExampleCustomResponseHeadersPolicy`
- Sensitive values (paths, ARNs) are masked in output
