/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions copyright 2012-2015 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;

/**
 * This class attempts to abstract the generation and comparison of keys
 * for an index. It is subclassed for the specific type of indexing.
 */
abstract class Indexer
{
  /**
   * Generate the set of index keys for an entry.
   *
   * @param entry The entry.
   * @param keys The set into which the generated keys will be inserted.
   * @param options The indexing options to use
   */
  public abstract void indexEntry(Entry entry, Set<ByteString> keys, IndexingOptions options);

  /**
   * Generate the set of index keys to be added and the set of index keys
   * to be deleted for an entry that was modified.
   *
   * @param oldEntry The original entry contents.
   * @param newEntry The new entry contents.
   * @param mods The set of modifications that were applied to the entry.
   * @param modifiedKeys The map into which the modified keys will be inserted.
   * @param options The indexing options to use
   */
  public abstract void modifyEntry(Entry oldEntry, Entry newEntry,
      List<Modification> mods, Map<ByteString, Boolean> modifiedKeys,
      IndexingOptions options);

  /**
   * Get a string representation of this object.  The returned value is
   * used to name an index created using this object.
   * @return A string representation of this object.
   */
  @Override
  public abstract String toString();
}
