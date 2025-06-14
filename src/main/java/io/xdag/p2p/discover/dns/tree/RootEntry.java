package io.xdag.p2p.discover.dns.tree;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.xdag.p2p.DnsException;
import io.xdag.p2p.DnsException.TypeEnum;
import io.xdag.p2p.proto.Discover.DnsRoot;
import io.xdag.p2p.utils.BytesUtils;
import io.xdag.p2p.utils.CryptoUtils;
import java.security.SignatureException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;

/**
 * Represents a root entry in a DNS tree. Contains the tree root information including sequence
 * number, hashes, and signature.
 */
@Getter
@Slf4j(topic = "net")
public class RootEntry implements Entry {

  /** The DNS root protobuf object */
  private DnsRoot dnsRoot;

  /**
   * Constructor for RootEntry with existing DnsRoot.
   *
   * @param dnsRoot the DNS root protobuf object
   */
  public RootEntry(DnsRoot dnsRoot) {
    this.dnsRoot = dnsRoot;
  }

  /**
   * Get the E-root hash (entries root).
   *
   * @return the E-root hash as string
   */
  public String getERoot() {
    return new String(dnsRoot.getTreeRoot().getERoot().toByteArray());
  }

  public String getLRoot() {
    return new String(dnsRoot.getTreeRoot().getLRoot().toByteArray());
  }

  public int getSeq() {
    return dnsRoot.getTreeRoot().getSeq();
  }

  public void setSeq(int seq) {
    DnsRoot.TreeRoot.Builder builder = dnsRoot.getTreeRoot().toBuilder();
    builder.setSeq(seq);

    DnsRoot.Builder dnsRootBuilder = dnsRoot.toBuilder();
    dnsRootBuilder.setTreeRoot(builder.build());

    this.dnsRoot = dnsRootBuilder.build();
  }

  public Bytes getSignature() {
    return CryptoUtils.decode64(new String(dnsRoot.getSignature().toByteArray()));
  }

  public void setSignature(Bytes signature) {
    DnsRoot.Builder dnsRootBuilder = dnsRoot.toBuilder();
    dnsRootBuilder.setSignature(ByteString.copyFrom(CryptoUtils.encode64(signature).getBytes()));
    this.dnsRoot = dnsRootBuilder.build();
  }

  public RootEntry(String eRoot, String lRoot, int seq) {
    DnsRoot.TreeRoot.Builder builder = DnsRoot.TreeRoot.newBuilder();
    builder.setERoot(ByteString.copyFrom(eRoot.getBytes()));
    builder.setLRoot(ByteString.copyFrom(lRoot.getBytes()));
    builder.setSeq(seq);

    DnsRoot.Builder dnsRootBuilder = DnsRoot.newBuilder();
    dnsRootBuilder.setTreeRoot(builder.build());
    this.dnsRoot = dnsRootBuilder.build();
  }

  public static RootEntry parseEntry(String e) throws DnsException {
    String value = e.substring(rootPrefix.length());
    DnsRoot dnsRoot1;
    try {
      dnsRoot1 = DnsRoot.parseFrom(CryptoUtils.decode64(value).toArray());
    } catch (InvalidProtocolBufferException ex) {
      throw new DnsException(TypeEnum.INVALID_ROOT, String.format("proto=[%s]", e), ex);
    }

    Bytes signature = CryptoUtils.decode64(new String(dnsRoot1.getSignature().toByteArray()));
    if (signature.size() != 65) {
      throw new DnsException(
          TypeEnum.INVALID_SIGNATURE,
          String.format(
              "signature's length(%d) != 65, signature: %s",
              signature.size(), BytesUtils.toHexString(signature)));
    }

    return new RootEntry(dnsRoot1);
  }

  public static RootEntry parseEntry(String e, String publicKey, String domain)
      throws SignatureException, DnsException {
    log.info("Domain:{}, public key:{}", domain, publicKey);
    RootEntry rootEntry = parseEntry(e);
    boolean verify =
        CryptoUtils.verifySignature(publicKey, rootEntry.toString(), rootEntry.getSignature());
    if (!verify) {
      throw new DnsException(
          TypeEnum.INVALID_SIGNATURE,
          String.format(
              "verify signature failed! data:[%s], publicKey:%s, domain:%s", e, publicKey, domain));
    }
    if (!CryptoUtils.isValidHash(rootEntry.getERoot())
        || !CryptoUtils.isValidHash(rootEntry.getLRoot())) {
      throw new DnsException(
          TypeEnum.INVALID_CHILD,
          "eroot:" + rootEntry.getERoot() + " lroot:" + rootEntry.getLRoot());
    }
    log.info("Get dnsRoot:[{}]", rootEntry.dnsRoot.toString());
    return rootEntry;
  }

  @Override
  public String toString() {
    return dnsRoot.getTreeRoot().toString();
  }

  public String toFormat() {
    return rootPrefix + CryptoUtils.encode64(Bytes.wrap(dnsRoot.toByteArray()));
  }
}
