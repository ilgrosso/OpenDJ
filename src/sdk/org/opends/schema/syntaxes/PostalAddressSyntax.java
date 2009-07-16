package org.opends.schema.syntaxes;

import static org.opends.server.util.ServerConstants.SCHEMA_PROPERTY_ORIGIN;
import static org.opends.server.schema.SchemaConstants.SYNTAX_POSTAL_ADDRESS_OID;
import static org.opends.server.schema.SchemaConstants.SYNTAX_POSTAL_ADDRESS_NAME;
import static org.opends.server.schema.SchemaConstants.SYNTAX_POSTAL_ADDRESS_DESCRIPTION;
import org.opends.server.types.ByteSequence;
import org.opends.messages.MessageBuilder;

import java.util.List;
import java.util.Map;
import java.util.Collections;

/**
 * This class implements the postal address attribute syntax, which is a list of
 * UCS (Universal Character Set, as defined in the ISO 10646 specification and
 * includes UTF-8 and UTF-16) strings separated by dollar signs.  By default,
 * they will be treated in a case-insensitive manner, and equality and substring
 * matching will be allowed.
 */
public class PostalAddressSyntax extends SyntaxDescription
{
  public PostalAddressSyntax(Map<String, List<String>> extraProperties)
  {
    super(SYNTAX_POSTAL_ADDRESS_OID, SYNTAX_POSTAL_ADDRESS_NAME,
        SYNTAX_POSTAL_ADDRESS_DESCRIPTION, extraProperties);
  }

  public boolean isHumanReadable() {
    return true;
  }

  /**
   * Indicates whether the provided value is acceptable for use in an attribute
   * with this syntax.  If it is not, then the reason may be appended to the
   * provided buffer.
   *
   * @param  value          The value for which to make the determination.
   * @param  invalidReason  The buffer to which the invalid reason should be
   *                        appended.
   *
   * @return  <CODE>true</CODE> if the provided value is acceptable for use with
   *          this syntax, or <CODE>false</CODE> if not.
   */
  public boolean valueIsAcceptable(ByteSequence value,
                                   MessageBuilder invalidReason)
  {
    // We'll allow any value.
    return true;
  }
}
