package io.xdag.p2p.discover.dns.tree;

import io.xdag.p2p.DnsException;
import io.xdag.p2p.DnsException.TypeEnum;
import io.xdag.p2p.utils.BytesUtils;
import io.xdag.p2p.utils.CryptoUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a link entry in a DNS tree. Link entries contain references to other DNS trees with
 * their public keys and domains.
 *
 * @param represent String representation of the link entry
 * @param domain Domain name referenced by this link
 * @param unCompressHexPublicKey Uncompressed hexadecimal public key
 */
@Slf4j(topic = "net")
public record LinkEntry(String represent, String domain, String unCompressHexPublicKey)
    implements Entry {

  /**
   * Constructor for LinkEntry.
   *
   * @param represent the string representation of the link
   * @param domain the domain name
   * @param unCompressHexPublicKey the uncompressed hexadecimal public key
   */
  public LinkEntry {}

  /**
   * Parse a link entry from its tree representation.
   *
   * @param treeRepresent the tree representation string
   * @return the parsed LinkEntry object
   * @throws DnsException if parsing fails due to invalid format or bad public key
   */
  public static LinkEntry parseEntry(String treeRepresent) throws DnsException {
    if (!treeRepresent.startsWith(linkPrefix)) {
      throw new DnsException(
          TypeEnum.INVALID_SCHEME_URL,
          "scheme url must starts with :[" + Entry.linkPrefix + "], but get " + treeRepresent);
    }
    String[] items = treeRepresent.substring(linkPrefix.length()).split("@");
    if (items.length != 2) {
      throw new DnsException(TypeEnum.NO_PUBLIC_KEY, "scheme url:" + treeRepresent);
    }
    String base32PublicKey = items[0];

    try {
      Bytes data = CryptoUtils.decode32(base32PublicKey);
      String unCompressPublicKey = CryptoUtils.decompressPubKey(BytesUtils.toHexString(data));
      return new LinkEntry(treeRepresent, items[1], unCompressPublicKey);
    } catch (RuntimeException exception) {
      throw new DnsException(TypeEnum.BAD_PUBLIC_KEY, "bad public key:" + base32PublicKey);
    }
  }

  /**
   * Build a link representation string from public key and domain.
   *
   * @param base32PubKey the base32-encoded public key
   * @param domain the domain name
   * @return the link representation string
   */
  public static String buildRepresent(String base32PubKey, String domain) {
    return linkPrefix + base32PubKey + "@" + domain;
  }

  /**
   * Convert the link entry to its string representation.
   *
   * @return the string representation of the link entry
   */
  @NotNull
  @Override
  public String toString() {
    return represent;
  }
}
