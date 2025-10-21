# DNS Discovery Configuration Guide

This guide explains how to configure DNS-based node discovery using EIP-1459 with AWS Route53.

## Overview

XDAGJ-P2P supports two DNS discovery modes:

1. **DNS Publishing** - Publish your node list to DNS for others to discover
2. **DNS Sync** - Discover nodes from published DNS records

## DNS Publishing Setup

### Prerequisites

- AWS Route53 hosted zone
- Private key for signing DNS records (EIP-1459 requirement)
- AWS IAM credentials with Route53 write permissions

### Step 1: Generate Private Key

The DNS private key is used to sign your DNS records according to EIP-1459:

```bash
# Use your preferred method to generate a secp256k1 private key
# The key should be in hexadecimal format (64 characters)
# Example: b71c71a67e1177ad4e901695e1b4b9ee17ae16c6668d313eac2f96dbcda3f291
```

**Security Note**: Keep this private key secure and never commit it to version control.

### Step 2: Configure AWS IAM Permissions

Create an IAM user with Route53 permissions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "route53:GetHostedZone",
        "route53:ListHostedZones",
        "route53:ChangeResourceRecordSets",
        "route53:ListResourceRecordSets"
      ],
      "Resource": "arn:aws:route53:::hostedzone/YOUR_ZONE_ID"
    }
  ]
}
```

### Step 3: Set Environment Variables

```bash
# DNS signing key (required)
export DNS_PRIVATE_KEY="your-private-key-in-hex-format"

# DNS domain (required - use mainnet.xdag.io or testnet.xdag.io)
export DNS_DOMAIN="mainnet.xdag.io"

# AWS credentials (required)
export AWS_ACCESS_KEY_ID="AKIAIOSFODNN7EXAMPLE"
export AWS_SECRET_ACCESS_KEY="wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"

# Optional: AWS region (defaults to us-east-1)
export AWS_REGION="us-east-1"
```

**For production deployments**, use AWS IAM roles or AWS Secrets Manager instead of environment variables.

### Step 4: Run DNS Publishing Example

```bash
# Build the project
mvn clean package -DskipTests

# Run DNS publishing mode
java -cp target/xdagj-p2p-*-jar-with-dependencies.jar \
     io.xdag.p2p.example.DnsExample PUBLISH
```

The application will:
1. Connect to the P2P network
2. Collect active node information
3. Sign the node list with your private key
4. Publish to AWS Route53

### Step 5: Verify DNS Records

Check your DNS records:

```bash
# For mainnet
dig TXT _dnsaddr.mainnet.xdag.io

# For testnet
dig TXT _dnsaddr.testnet.xdag.io

# The output should show EIP-1459 formatted records
```

---

## DNS Sync Setup (Discovering Nodes)

Much simpler - just provide the DNS tree URL:

### Configuration

```java
P2pConfig config = new P2pConfig();
config.setDiscoverEnable(true);

// For mainnet
config.setTreeUrls(Arrays.asList(
    "tree://APFGGTFOBVE2ZNAB3CSMNNX6RRK3ODIRLP2AA5U4YFAA6MSYZUYTQ@mainnet.xdag.io"
));

// For testnet
config.setTreeUrls(Arrays.asList(
    "tree://BQHGGTFOBVE2ZNAB3CSMNNX6RRK3ODIRLP2AA5U4YFAA6MSYZUYTQ@testnet.xdag.io"
));
```

### Run DNS Sync Example

```bash
java -cp target/xdagj-p2p-*-jar-with-dependencies.jar \
     io.xdag.p2p.example.DnsExample SYNC
```

No environment variables needed for sync mode!

---

## EIP-1459 DNS Tree Format

The published DNS records follow EIP-1459 standard:

```
enrtree-root:v1 e=ENRTREE l=LINK seq=1 sig=SIGNATURE
enrtree-branch:V4,NODE1,NODE2,NODE3
enr:-KG4QOtcP9X1FbIMOe17QNMKqDxCpm14jcX5tiOE4_TyMrFqbmhPZHK_ZPG2Gxb1GE2xdtodOfx9-cgvNtxnRyHEmC0Ghtt...
```

### Record Types

1. **enrtree-root**: Root record with signature
2. **enrtree-branch**: Branch pointing to more records
3. **enrtree-link**: Link to other DNS trees
4. **enr**: Actual node record (Ethereum Node Record format)

---

## Security Best Practices

### Do NOT:
- ❌ Commit private keys to git
- ❌ Use production credentials in examples
- ❌ Share AWS secret keys publicly
- ❌ Store credentials in code

### DO:
- ✅ Use environment variables
- ✅ Use AWS IAM roles in production
- ✅ Rotate keys regularly
- ✅ Restrict IAM permissions to minimum required
- ✅ Use AWS Secrets Manager for production
- ✅ Enable CloudTrail logging for Route53 changes

---

## Troubleshooting

### Error: "DNS publishing configuration not found"

**Cause**: Missing environment variables

**Solution**: Ensure all 4 required variables are set:
```bash
echo $DNS_PRIVATE_KEY
echo $DNS_DOMAIN
echo $AWS_ACCESS_KEY_ID
echo $AWS_SECRET_ACCESS_KEY
```

### Error: "Access Denied" from AWS

**Cause**: Insufficient IAM permissions

**Solution**: 
1. Verify IAM policy includes Route53 permissions
2. Check the hosted zone ID matches
3. Ensure credentials are not expired

### Error: "Invalid signature"

**Cause**: Private key format issue

**Solution**:
1. Verify key is in hex format (64 characters)
2. Ensure no spaces or newlines in the key
3. Test key with signing test utility

---

## Production Deployment

### AWS IAM Role (Recommended)

Instead of environment variables, use IAM roles:

```java
// Application will automatically use IAM role credentials
// No environment variables needed
```

### AWS Secrets Manager

```bash
# Store credentials in Secrets Manager
aws secretsmanager create-secret \
    --name xdagj-p2p/dns-config \
    --secret-string '{"private_key":"...","domain":"..."}'

# Application retrieves from Secrets Manager
```

### Docker Deployment

```dockerfile
FROM openjdk:21-slim

# Pass secrets via Docker secrets
RUN --mount=type=secret,id=dns_key \
    DNS_PRIVATE_KEY=$(cat /run/secrets/dns_key) \
    java -jar xdagj-p2p.jar
```

---

## References

- [EIP-1459: Node Discovery via DNS](https://eips.ethereum.org/EIPS/eip-1459)
- [AWS Route53 API Reference](https://docs.aws.amazon.com/route53/latest/APIReference/)
- [XDAGJ-P2P User Guide](USER_GUIDE.md)

---

**Last Updated**: 2025-10-19  
**Version**: 1.0

