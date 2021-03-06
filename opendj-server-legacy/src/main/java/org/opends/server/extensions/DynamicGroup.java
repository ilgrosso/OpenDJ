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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.extensions;



import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.admin.std.server.DynamicGroupImplementationCfg;
import org.opends.server.api.Group;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDAPURL;
import org.opends.server.types.MemberList;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.SearchFilter;
import org.forgerock.opendj.ldap.SearchScope;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.forgerock.util.Reject.*;



/**
 * This class provides a dynamic group implementation, in which
 * membership is determined dynamically based on criteria provided
 * in the form of one or more LDAP URLs.  All dynamic groups should
 * contain the groupOfURLs object class, with the memberURL attribute
 * specifying the membership criteria.
 */
public class DynamicGroup
       extends Group<DynamicGroupImplementationCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The DN of the entry that holds the definition for this group. */
  private DN groupEntryDN;

  /** The set of the LDAP URLs that define the membership criteria. */
  private LinkedHashSet<LDAPURL> memberURLs;



  /**
   * Creates a new, uninitialized dynamic group instance.  This is intended for
   * internal use only.
   */
  public DynamicGroup()
  {
    super();

    // No initialization is required here.
  }



  /**
   * Creates a new dynamic group instance with the provided information.
   *
   * @param  groupEntryDN  The DN of the entry that holds the definition for
   *                       this group.  It must not be {@code null}.
   * @param  memberURLs    The set of LDAP URLs that define the membership
   *                       criteria for this group.  It must not be
   *                       {@code null}.
   */
  public DynamicGroup(DN groupEntryDN, LinkedHashSet<LDAPURL> memberURLs)
  {
    super();

    ifNull(groupEntryDN, memberURLs);

    this.groupEntryDN = groupEntryDN;
    this.memberURLs   = memberURLs;
  }



  /** {@inheritDoc} */
  @Override
  public void initializeGroupImplementation(
                   DynamicGroupImplementationCfg configuration)
         throws ConfigException, InitializationException
  {
    // No additional initialization is required.
  }




  /** {@inheritDoc} */
  @Override
  public DynamicGroup newInstance(ServerContext serverContext, Entry groupEntry)
         throws DirectoryException
  {
    ifNull(groupEntry);


    // Get the memberURL attribute from the entry, if there is one, and parse
    // out the LDAP URLs that it contains.
    LinkedHashSet<LDAPURL> memberURLs = new LinkedHashSet<>();
    AttributeType memberURLType = DirectoryServer.getAttributeTypeOrDefault(ATTR_MEMBER_URL_LC);
    List<Attribute> attrList = groupEntry.getAttribute(memberURLType);
    if (attrList != null)
    {
      for (Attribute a : attrList)
      {
        for (ByteString v : a)
        {
          try
          {
            memberURLs.add(LDAPURL.decode(v.toString(), true));
          }
          catch (DirectoryException de)
          {
            logger.traceException(de);
            logger.error(ERR_DYNAMICGROUP_CANNOT_DECODE_MEMBERURL, v,
                    groupEntry.getName(), de.getMessageObject());
          }
        }
      }
    }

    return new DynamicGroup(groupEntry.getName(), memberURLs);
  }



  /** {@inheritDoc} */
  @Override
  public SearchFilter getGroupDefinitionFilter()
         throws DirectoryException
  {
    // FIXME -- This needs to exclude enhanced groups once we have support for
    // them.
    return SearchFilter.createFilterFromString("(" + ATTR_OBJECTCLASS + "=" +
                                               OC_GROUP_OF_URLS + ")");
  }



  /** {@inheritDoc} */
  @Override
  public boolean isGroupDefinition(Entry entry)
  {
    ifNull(entry);

    // FIXME -- This needs to exclude enhanced groups once we have support for
    //them.
    ObjectClass groupOfURLsClass =
         DirectoryConfig.getObjectClass(OC_GROUP_OF_URLS_LC, true);
    return entry.hasObjectClass(groupOfURLsClass);
  }



  /** {@inheritDoc} */
  @Override
  public DN getGroupDN()
  {
    return groupEntryDN;
  }



  /** {@inheritDoc} */
  @Override
  public void setGroupDN(DN groupDN)
  {
    groupEntryDN = groupDN;
  }



  /**
   * Retrieves the set of member URLs for this dynamic group.  The returned set
   * must not be altered by the caller.
   *
   * @return  The set of member URLs for this dynamic group.
   */
  public Set<LDAPURL> getMemberURLs()
  {
    return memberURLs;
  }



  /** {@inheritDoc} */
  @Override
  public boolean supportsNestedGroups()
  {
    // Dynamic groups don't support nesting.
    return false;
  }



  /** {@inheritDoc} */
  @Override
  public List<DN> getNestedGroupDNs()
  {
    // Dynamic groups don't support nesting.
    return Collections.<DN>emptyList();
  }



  /** {@inheritDoc} */
  @Override
  public void addNestedGroup(DN nestedGroupDN)
         throws UnsupportedOperationException, DirectoryException
  {
    // Dynamic groups don't support nesting.
    LocalizableMessage message = ERR_DYNAMICGROUP_NESTING_NOT_SUPPORTED.get();
    throw new UnsupportedOperationException(message.toString());
  }



  /** {@inheritDoc} */
  @Override
  public void removeNestedGroup(DN nestedGroupDN)
         throws UnsupportedOperationException, DirectoryException
  {
    // Dynamic groups don't support nesting.
    LocalizableMessage message = ERR_DYNAMICGROUP_NESTING_NOT_SUPPORTED.get();
    throw new UnsupportedOperationException(message.toString());
  }



  /** {@inheritDoc} */
  @Override
  public boolean isMember(DN userDN, Set<DN> examinedGroups)
         throws DirectoryException
  {
    if (! examinedGroups.add(getGroupDN()))
    {
      return false;
    }

    Entry entry = DirectoryConfig.getEntry(userDN);
    return entry != null && isMember(entry);
  }



  /** {@inheritDoc} */
  @Override
  public boolean isMember(Entry userEntry, Set<DN> examinedGroups)
         throws DirectoryException
  {
    if (! examinedGroups.add(getGroupDN()))
    {
      return false;
    }

    for (LDAPURL memberURL : memberURLs)
    {
      if (memberURL.matchesEntry(userEntry))
      {
        return true;
      }
    }

    return false;
  }



  /** {@inheritDoc} */
  @Override
  public MemberList getMembers()
         throws DirectoryException
  {
    return new DynamicGroupMemberList(groupEntryDN, memberURLs);
  }



  /** {@inheritDoc} */
  @Override
  public MemberList getMembers(DN baseDN, SearchScope scope,
                               SearchFilter filter)
         throws DirectoryException
  {
    if (baseDN == null && filter == null)
    {
      return new DynamicGroupMemberList(groupEntryDN, memberURLs);
    }
    else
    {
      return new DynamicGroupMemberList(groupEntryDN, memberURLs, baseDN, scope,
                                        filter);
    }
  }



  /** {@inheritDoc} */
  @Override
  public boolean mayAlterMemberList()
  {
    return false;
  }



  /** {@inheritDoc} */
  @Override
  public void addMember(Entry userEntry)
         throws UnsupportedOperationException, DirectoryException
  {
    // Dynamic groups don't support altering the member list.
    LocalizableMessage message = ERR_DYNAMICGROUP_ALTERING_MEMBERS_NOT_SUPPORTED.get();
    throw new UnsupportedOperationException(message.toString());
  }



  /** {@inheritDoc} */
  @Override
  public void removeMember(DN userDN)
         throws UnsupportedOperationException, DirectoryException
  {
    // Dynamic groups don't support altering the member list.
    LocalizableMessage message = ERR_DYNAMICGROUP_ALTERING_MEMBERS_NOT_SUPPORTED.get();
    throw new UnsupportedOperationException(message.toString());
  }



  /** {@inheritDoc} */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("DynamicGroup(dn=");
    buffer.append(groupEntryDN);
    buffer.append(",urls={");

    if (! memberURLs.isEmpty())
    {
      Iterator<LDAPURL> iterator = memberURLs.iterator();
      buffer.append("\"");
      iterator.next().toString(buffer, false);

      while (iterator.hasNext())
      {
        buffer.append("\", ");
        iterator.next().toString(buffer, false);
      }

      buffer.append("\"");
    }

    buffer.append("})");
  }
}

