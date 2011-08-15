/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 *
 */

package com.xpn.xwiki.user.impl.LDAP;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.securityfilter.realm.SimplePrincipal;

import com.novell.ldap.LDAPConnection;
import com.novell.ldap.LDAPException;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.plugin.ldap.XWikiLDAPConfig;
import com.xpn.xwiki.plugin.ldap.XWikiLDAPConnection;
import com.xpn.xwiki.plugin.ldap.XWikiLDAPSearchAttribute;
import com.xpn.xwiki.plugin.ldap.XWikiLDAPUtils;
import com.xpn.xwiki.user.impl.xwiki.XWikiAuthServiceImpl;

import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.HashMap;

/**
 * This class provides an authentication method that validates a user trough LDAP against a directory. It gives LDAP
 * users access if they belong to a particular group, creates XWiki users if they have never logged in before and
 * synchronizes membership to XWiki groups based on membership to LDAP groups.
 * 
 * @version $Id$
 * @since 1.3 M2
 */
public class XWikiLDAPAuthServiceImpl extends XWikiAuthServiceImpl
{
    /**
     * The XWiki space where users are stored.
     */
    private static final String XWIKI_USER_SPACE = "XWiki";

    /**
     * The name of the XWiki group member field.
     */
    private static final String XWIKI_GROUP_MEMBERFIELD = "member";

    /**
     * Separator between space name and document name in document full name.
     */
    private static final String XWIKI_SPACE_NAME_SEP = ".";

    /**
     * Default unique user field name.
     */
    private static final String LDAP_DEFAULT_UID = "cn";

    /**
     * Logging tool.
     */
    private static final Log LOG = LogFactory.getLog(XWikiLDAPAuthServiceImpl.class);

    /**
     * {@inheritDoc}
     * 
     * @see com.xpn.xwiki.user.impl.xwiki.XWikiAuthServiceImpl#authenticate(java.lang.String, java.lang.String,
     *      com.xpn.xwiki.XWikiContext)
     */
    public Principal authenticate(String login, String password, XWikiContext context) throws XWikiException
    {
        /*
         * TODO: Put the next 4 following "if" in common with XWikiAuthService to ensure coherence This method was
         * returning null on failure so I preserved that behaviour, while adding the exact error messages to the context
         * given as argument. However, the right way to do this would probably be to throw XWikiException-s.
         */

        if (login == null) {
            // If we can't find the username field then we are probably on the login screen
            return null;
        }

        // Check for empty usernames
        if (login.equals("")) {
            context.put("message", "nousername");

            return null;
        }

        // Check for empty passwords
        if ((password == null) || (password.trim().equals(""))) {
            context.put("message", "nopassword");
            return null;
        }

        // Check for superadmin
        if (isSuperAdmin(login)) {
            return authenticateSuperAdmin(password, context);
        }

        // If we have the context then we are using direct mode
        // then we should specify the database
        // This is needed for virtual mode to work
        Principal principal = null;

        // Try authentication against ldap
        principal = ldapAuthenticate(login, password, context);

        if (principal == null) {
            // Fallback to local DB only if trylocal is true
            principal = xwikiAuthenticate(login, password, context);
        }

        return principal;
    }

    /**
     * @param name the name to convert.
     * @return a valid XWiki user name:
     *         <ul>
     *         <li>Remove '.'</li>
     *         </ul>
     */
    private String getValidXWikiUserName(String name)
    {
        return name.replace(XWIKI_SPACE_NAME_SEP, "");
    }

    /**
     * Try both local and global ldap login and return {@link Principal}.
     * 
     * @param cannonicalUsername the cleaned name of the user to log in.
     * @param password the password of the user to log in.
     * @param context the XWiki context.
     * @return the {@link Principal}.
     */
    protected Principal ldapAuthenticate(String cannonicalUsername, String password, XWikiContext context)
    {
        Principal principal = null;

        // Remove XWiki. prefix - not sure this is really a good idea or the right way to do it
        String ldapUserName = cannonicalUsername;
        int i = cannonicalUsername.indexOf(XWIKI_SPACE_NAME_SEP);
        if (i != -1) {
            ldapUserName = cannonicalUsername.substring(i + 1);
        }

        String validXWikiUserName = getValidXWikiUserName(ldapUserName);

        // First we check in the local context for a valid ldap user
        try {
            principal = ldapAuthenticateInContext(ldapUserName, validXWikiUserName, password, context);
        } catch (Exception e) {
            // continue
            if (LOG.isDebugEnabled()) {
                LOG.debug("Local LDAP authentication failed.");
            }
        }

        // If local ldap failed, try global ldap
        if (principal == null && !context.isMainWiki()) {
            // Then we check in the main database
            String db = context.getDatabase();
            try {
                context.setDatabase(context.getMainXWiki());
                try {
                    principal = ldapAuthenticateInContext(ldapUserName, validXWikiUserName, password, context);
                } catch (Exception e) {
                    // continue
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Global LDAP authentication failed.");
                    }
                }
            } finally {
                context.setDatabase(db);
            }
        }

        return principal;
    }

    /**
     * Try both local and global DB login if trylocal is true {@link Principal}.
     * 
     * @param login the name of the user to log in.
     * @param password the password of the user to log in.
     * @param context the XWiki context.
     * @return the {@link Principal}.
     * @throws XWikiException error when checking user name and password.
     */
    protected Principal xwikiAuthenticate(String login, String password, XWikiContext context) throws XWikiException
    {
        Principal principal = null;

        XWikiLDAPConfig config = XWikiLDAPConfig.getInstance();

        String trylocal = config.getLDAPParam("ldap_trylocal", "0", context);

        if ("1".equals(trylocal)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Trying authentication against XWiki DB");
            }

            principal = super.authenticate(login, password, context);
        }

        return principal;
    }

    /**
     * Try LDAP login for given context and return {@link Principal}.
     * 
     * @param ldapUserName the name of the ldap user to log in.
     * @param validXWikiUserName the name of the XWiki user to log in.
     * @param password the password of the user to log in.
     * @param context the XWiki context.
     * @return the {@link Principal}.
     * @throws XWikiException error when login.
     * @throws UnsupportedEncodingException error when login.
     * @throws LDAPException error when login.
     */
    protected Principal ldapAuthenticateInContext(String ldapUserName, String validXWikiUserName, String password,
        XWikiContext context) throws XWikiException, UnsupportedEncodingException, LDAPException
    {
        Principal principal = null;

        XWikiLDAPConfig config = XWikiLDAPConfig.getInstance();

        XWikiLDAPConnection connector = new XWikiLDAPConnection();
        XWikiLDAPUtils ldapUtils = new XWikiLDAPUtils(connector);

        ldapUtils.setUidAttributeName(config.getLDAPParam(XWikiLDAPConfig.PREF_LDAP_UID, LDAP_DEFAULT_UID, context));
        ldapUtils.setGroupClasses(config.getGroupClasses(context));
        ldapUtils.setGroupMemberFields(config.getGroupMemberFields(context));

        // ////////////////////////////////////////////////////////////////////
        // 1. check if ldap authentication is off => authenticate against db
        // ////////////////////////////////////////////////////////////////////

        if (!config.isLDAPEnabled(context)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("LDAP authentication failed: LDAP not activ");
            }

            return principal;
        }

        // ////////////////////////////////////////////////////////////////////
        // 2. bind to LDAP => if failed try db
        // ////////////////////////////////////////////////////////////////////

        if (!connector.open(ldapUserName, password, context)) {
            throw new XWikiException(XWikiException.MODULE_XWIKI_USER, XWikiException.ERROR_XWIKI_USER_INIT,
                "Bind to LDAP server failed.");
        }

        // ////////////////////////////////////////////////////////////////////
        // 3. if group param, verify group membership (& get DN)
        // ////////////////////////////////////////////////////////////////////

        String userDN = null;
        String filterGroupDN = config.getLDAPParam("ldap_user_group", "", context);

        if (filterGroupDN.length() > 0) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Checking if the user belongs to the user group: " + filterGroupDN);
            }

            userDN = ldapUtils.isUserInGroup(ldapUserName, filterGroupDN, context);

            if (userDN == null) {
                throw new XWikiException(XWikiException.MODULE_XWIKI_USER, XWikiException.ERROR_XWIKI_USER_INIT,
                    "LDAP user {0} does not belong to LDAP group {1}.", null,
                    new Object[] {ldapUserName, filterGroupDN});
            }
        }

        // ////////////////////////////////////////////////////////////////////
        // 4. if exclude group param, verify group membership
        // ////////////////////////////////////////////////////////////////////

        String excludeGroupDN = config.getLDAPParam("ldap_exclude_group", "", context);

        if (excludeGroupDN.length() > 0) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Checking if the user does not belongs to the exclude group: " + excludeGroupDN);
            }

            if (ldapUtils.isUserInGroup(ldapUserName, excludeGroupDN, context) != null) {
                throw new XWikiException(XWikiException.MODULE_XWIKI_USER, XWikiException.ERROR_XWIKI_USER_INIT,
                    "LDAP user {0} should not belong to LDAP group {1}.", null, new Object[] {ldapUserName,
                    filterGroupDN});
            }
        }

        // ////////////////////////////////////////////////////////////////////
        // 5. if no dn search for user
        // ////////////////////////////////////////////////////////////////////

        if (userDN == null) {
            // get DN from existing XWiki user
            userDN = getUserDNFromXWiki(validXWikiUserName, context);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Found user dn with the user object: " + userDN);
            }
        }

        List<XWikiLDAPSearchAttribute> searchAttributes = null;

        // if we still don't have a dn, search for it. Also get the attributes, we might need
        // them
        if (userDN == null) {
            String uidAttributeName = config.getLDAPParam(XWikiLDAPConfig.PREF_LDAP_UID, LDAP_DEFAULT_UID, context);

            // search for the user in LDAP
            String query = MessageFormat.format("({0}={1})", new Object[] {uidAttributeName, ldapUserName});
            String baseDN = config.getLDAPParam("ldap_base_DN", "", context);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Searching for the user in LDAP: user:" + ldapUserName + " base:" + baseDN + " query:"
                    + query + " uid:" + uidAttributeName);
            }

            searchAttributes =
                connector.searchLDAP(baseDN, query, getAttributeNameTable(context), LDAPConnection.SCOPE_SUB);

            for (XWikiLDAPSearchAttribute searchAttribute : searchAttributes) {
                if ("dn".equals(searchAttribute.name)) {
                    userDN = searchAttribute.value;

                    break;
                }
            }
        }

        if (userDN == null) {
            throw new XWikiException(XWikiException.MODULE_XWIKI_USER, XWikiException.ERROR_XWIKI_USER_INIT,
                "Can't find LDAP user DN.");
        }

        // ////////////////////////////////////////////////////////////////////
        // 6. apply validate_password property or if user used for LDAP connection is not the one
        // authenticated try to bind
        // ////////////////////////////////////////////////////////////////////

        if ("1".equals(config.getLDAPParam("ldap_validate_password", "0", context))) {
            String passwordField = config.getLDAPParam("ldap_password_field", "userPassword", context);
            if (!connector.checkPassword(userDN, password, passwordField)) {
                throw new XWikiException(XWikiException.MODULE_XWIKI_USER, XWikiException.ERROR_XWIKI_USER_INIT,
                    "LDAP authentication failed:" + " could not validate the password: wrong password for " + userDN);
            }
        } else {
            String bindDNFormat = config.getLDAPParam("ldap_bind_DN", "{0}", context);
            String bindDN = MessageFormat.format(bindDNFormat, new Object[] {ldapUserName});

            if (!userDN.equals(bindDN)) {
                connector.getConnection().bind(LDAPConnection.LDAP_V3, userDN, password.getBytes("UTF8"));
            }
        }

        // ////////////////////////////////////////////////////////////////////
        // 6. sync user
        // ////////////////////////////////////////////////////////////////////

        boolean createuser = syncUser(validXWikiUserName, userDN, searchAttributes, ldapUtils, context);

        // from now on we can enter the application
        principal = getUserPrincipal(validXWikiUserName, context);
        if (principal == null) {
            throw new XWikiException(XWikiException.MODULE_XWIKI_USER, XWikiException.ERROR_XWIKI_USER_INIT,
                "Could not create authenticated principal.");
        }

        // ////////////////////////////////////////////////////////////////////
        // 7. sync groups membership
        // ////////////////////////////////////////////////////////////////////

        try {
            syncGroupsMembership(validXWikiUserName, userDN, createuser, ldapUtils, context);
        } catch (XWikiException e) {
            LOG.error("Failed to synchronise user's groups membership", e);
        }

        return principal;
    }

    /**
     * @param context the XWiki context.
     * @return the LDAP user attributes names.
     */
    protected String[] getAttributeNameTable(XWikiContext context)
    {
        String[] attributeNameTable = null;

        XWikiLDAPConfig config = XWikiLDAPConfig.getInstance();

        List<String> attributeNameList = new ArrayList<String>();
        config.getUserMappings(attributeNameList, context);

        int lsize = attributeNameList.size();
        if (lsize > 0) {
            attributeNameTable = (String[]) attributeNameList.toArray(new String[lsize]);
        }

        return attributeNameTable;
    }

    /**
     * Update or create XWiki user base on LDAP.
     * 
     * @param userName the name of the user.
     * @param userDN the LDAP user DN.
     * @param searchAttributeListIn the attributes.
     * @param ldapUtils the LDAP communication tool.
     * @param context the XWiki context.
     * @return indicate if XWiki user is created or update.
     * @throws XWikiException error when updating or creating XWiki user.
     */
    protected boolean syncUser(String userName, String userDN, List<XWikiLDAPSearchAttribute> searchAttributeListIn,
        XWikiLDAPUtils ldapUtils, XWikiContext context) throws XWikiException
    {
        // check if we have to create the user
        String xwikiUserName = findUser(userName, context);

        boolean createuser = xwikiUserName == null;

        XWikiLDAPConfig config = XWikiLDAPConfig.getInstance();

        if (createuser || config.getLDAPParam("ldap_update_user", "0", context).equals("1")) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("LDAP attributes will be used to update XWiki attributes.");
            }

            List<XWikiLDAPSearchAttribute> searchAttributeList = searchAttributeListIn;

            // get attributes from LDAP if we don't already have them
            if (searchAttributeList == null) {
                // didn't get attributes before, so do it now
                searchAttributeList =
                    ldapUtils.getConnection().searchLDAP(userDN, null, getAttributeNameTable(context),
                        LDAPConnection.SCOPE_BASE);
            }

            if (createuser) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Creating new XWiki user based on LDAP attribues located at " + userDN);
                }

                createUserFromLDAP(userName, searchAttributeList, context);

                if (LOG.isDebugEnabled()) {
                    LOG.debug("New XWiki user created: " + xwikiUserName);
                }
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Updating existing user with LDAP attribues located at " + userDN);
                }

                try {
                    updateUserFromLDAP(xwikiUserName, searchAttributeList, context);
                } catch (XWikiException e) {
                    LOG.error("Failed to synchronise user's informations", e);
                }
            }
        }

        return createuser;
    }

    /**
     * Synchronize user XWiki membership with it's LDAP membership.
     * 
     * @param xwikiUserName the name of the user.
     * @param userDN the LDAP DN of the user.
     * @param createuser indicate if the user is created or updated.
     * @param ldapUtils the LDAP communication tool.
     * @param context the XWiki context.
     * @throws XWikiException error when synchronizing user membership.
     */
    protected void syncGroupsMembership(String xwikiUserName, String userDN, boolean createuser,
        XWikiLDAPUtils ldapUtils, XWikiContext context) throws XWikiException
    {
        XWikiLDAPConfig config = XWikiLDAPConfig.getInstance();

        // got valid group mappings
        Map<String, Set<String>> groupMappings = config.getGroupMappings(context);

        // update group membership, join and remove from given groups
        // sync group membership for this user
        if (groupMappings.size() > 0) {
            // flag if always sync or just on create of the user
            String syncmode = config.getLDAPParam("ldap_mode_group_sync", "", context);

            if ((syncmode.equalsIgnoreCase("create") && createuser) || syncmode.equalsIgnoreCase("always")) {
                syncGroupsMembership(xwikiUserName, userDN, groupMappings, ldapUtils, context);
            }

        }
    }

    /**
     * Synchronize user XWiki membership with it's LDAP membership.
     * 
     * @param xwikiUserName the name of the user.
     * @param userDN the LDAP DN of the user.
     * @param groupMappings the mapping between XWiki groups names and LDAP groups names.
     * @param ldapUtils the LDAP communication tool.
     * @param context the XWiki context.
     * @throws XWikiException error when synchronizing user membership.
     */
    protected void syncGroupsMembership(String xwikiUserName, String userDN, Map<String, Set<String>> groupMappings,
        XWikiLDAPUtils ldapUtils, XWikiContext context) throws XWikiException
    {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Updating group membership for the user: " + xwikiUserName);
        }

        // ASSUMING the implementation still returns the actual list. In this case
        // manipulations to the list are for real.
        // get the list of groups the user already belongs to
        Collection<String> userGroups =
            context.getWiki().getGroupService(context).getAllGroupsNamesForMember(xwikiUserName, 0, 0, context);

        if (LOG.isDebugEnabled()) {
            LOG.debug("The user belongs to following XWiki groups: ");
            for (String userGroupName : userGroups) {
                LOG.debug(userGroupName);
            }
        }

        // retrieve list of all groups
        List<String> allxwikigroups =
            (List<String>) context.getWiki().getGroupService(context).getAllMatchedGroups(null, false, 0, 0, null,
                context);

        if (LOG.isDebugEnabled()) {
            LOG.debug("All defined XWiki groups: ");

            for (String xwikiGroupName : allxwikigroups) {
                LOG.debug(xwikiGroupName);
            }
        }

        // go through mapped groups to locate the user
        for (Map.Entry<String, Set<String>> entry : groupMappings.entrySet()) {
            String groupDN = entry.getKey();
            Set<String> xwikiGroupNameSet = entry.getValue();

            for (String xwikiGroupName : xwikiGroupNameSet) {
                // check if group is in list of all groups
                if (!allxwikigroups.contains(xwikiGroupName)) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("XWiki group not found:" + xwikiGroupName);
                    }

                    continue;
                }

                Map<String, String> groupMembers = ldapUtils.getGroupMembers(groupDN, context);

                if (groupMembers != null) {
                    syncGroupMembership(xwikiUserName, userDN, xwikiGroupName, userGroups, groupMembers, context);
                }
            }
        }
    }

    /**
     * Synchronize user XWiki membership with it's LDAP membership for provided group.
     * 
     * @param xwikiUserName the name of the user.
     * @param userDN the LDAP DN of the user.
     * @param xwikiGroupName the name of the XWiki group.
     * @param userGroups the XWiki groups of user.
     * @param groupMembers the members of LDAP group.
     * @param context the XWiki context.
     */
    protected void syncGroupMembership(String xwikiUserName, String userDN, String xwikiGroupName,
        Collection<String> userGroups, Map<String, String> groupMembers, XWikiContext context)
    {
        if (groupMembers.containsKey(userDN)) {
            // add to group if not there
            if (!userGroups.contains(xwikiGroupName)) {
                addUserToXWikiGroup(xwikiUserName, xwikiGroupName, context);
            }
        } else {
            // remove from group if there
            if (userGroups.contains(xwikiGroupName)) {
                removeUserFromXWikiGroup(xwikiUserName, xwikiGroupName, context);

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Finished removing xwiki group " + xwikiGroupName + " from user " + xwikiUserName);
                }
            }
        }
    }

    /**
     * Add user name to provided XWiki group.
     * 
     * @param userName the name of the user.
     * @param groupName the name of the group.
     * @param context the XWiki context.
     */
    // TODO move this methods in a toolkit for all platform.
    protected void addUserToXWikiGroup(String userName, String groupName, XWikiContext context)
    {
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format("Adding user {0} to xwiki group {1}", new Object[] {userName, groupName}));
            }

            String fullWikiUserName = XWIKI_USER_SPACE + XWIKI_SPACE_NAME_SEP + userName;

            BaseClass groupClass = context.getWiki().getGroupClass(context);

            // Get document representing group
            XWikiDocument groupDoc = context.getWiki().getDocument(groupName, context);

            // Add a member object to document
            BaseObject memberObj = groupDoc.newObject(groupClass.getName(), context);
            Map<String, String> map = new HashMap<String, String>();
            map.put(XWIKI_GROUP_MEMBERFIELD, fullWikiUserName);
            groupClass.fromMap(map, memberObj);

            // Save modifications
            context.getWiki().saveDocument(groupDoc, context);

            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format("Finished adding user {0} to xwiki group {1}", new Object[] {userName,
                groupName}));
            }

        } catch (Exception e) {
            LOG.error(String.format("Failed to add a user [{0}] to a group [{1}]", new Object[] {userName, groupName}),
                e);
        }
    }

    /**
     * Remove user name from provided XWiki group.
     * 
     * @param userName the name of the user.
     * @param groupName the name of the group.
     * @param context the XWiki context.
     */
    // TODO move this methods in a toolkit for all platform.
    protected void removeUserFromXWikiGroup(String userName, String groupName, XWikiContext context)
    {
        try {
            String fullWikiUserName = XWIKI_USER_SPACE + XWIKI_SPACE_NAME_SEP + userName;

            String groupClassName = context.getWiki().getGroupClass(context).getName();

            // Get the XWiki document holding the objects comprising the group membership list
            XWikiDocument groupDoc = context.getWiki().getDocument(groupName, context);

            // Get and remove the specific group membership object for the user
            BaseObject groupObj = groupDoc.getObject(groupClassName, XWIKI_GROUP_MEMBERFIELD, fullWikiUserName);
            groupDoc.removeObject(groupObj);

            // Save modifications
            context.getWiki().saveDocument(groupDoc, context);
        } catch (Exception e) {
            LOG.error("Failed to remove a user from a group " + userName + " group: " + groupName, e);
        }
    }

    /**
     * Create a {@link Principal} object for provided user.
     * 
     * @param userName the user name.
     * @param context the XWiki context.
     * @return the {@link Principal}.
     */
    protected Principal getUserPrincipal(String userName, XWikiContext context)
    {
        Principal principal = null;

        try {
            String xWikiUserName = findUser(userName, context);
            if (xWikiUserName != null) {
                principal = new SimplePrincipal(context.getDatabase() + ":" + xWikiUserName);
            }
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Failed creating a Principal for user " + userName, e);
            }
        }

        return principal;
    }

    /**
     * Sets attributes on the user object based on attribute values provided by the LDAP.
     * 
     * @param xwikiUserName the XWiki user name.
     * @param searchAttributes the attributes.
     * @param context the XWiki context.
     * @throws XWikiException error when updating XWiki user.
     */
    protected void updateUserFromLDAP(String xwikiUserName, List<XWikiLDAPSearchAttribute> searchAttributes,
        XWikiContext context) throws XWikiException
    {
        XWikiLDAPConfig config = XWikiLDAPConfig.getInstance();

        Map<String, String> userMappings = config.getUserMappings(null, context);

        BaseClass userClass = context.getWiki().getUserClass(context);

        XWikiDocument userDoc = context.getWiki().getDocument(xwikiUserName, context);
        BaseObject userObj = userDoc.getObject(userClass.getName());

        Map<String, String> map = new HashMap<String, String>();
        for (XWikiLDAPSearchAttribute lattr : searchAttributes) {
            String key = (String) userMappings.get(lattr.name);
            if (key == null || userClass.get(key) == null) {
                continue;
            }
            String value = lattr.value;

            String objValue = userObj.getStringValue(key);
            if (objValue == null || !objValue.equals(value)) {
                map.put(key, value);
            }
        }

        if (!map.isEmpty()) {
            userClass.fromMap(map, userObj);

            context.getWiki().saveDocument(userDoc, context);
        }
    }

    /**
     * Create an XWiki user and set all mapped attributes from LDAP to XWiki attributes.
     * 
     * @param xwikiUserName the XWiki user name.
     * @param searchAttributes the attributes.
     * @param context the XWiki context.
     * @throws XWikiException error when creating XWiki user.
     */
    protected void createUserFromLDAP(String xwikiUserName, List<XWikiLDAPSearchAttribute> searchAttributes,
        XWikiContext context) throws XWikiException
    {
        XWikiLDAPConfig config = XWikiLDAPConfig.getInstance();

        Map<String, String> userMappings = config.getUserMappings(null, context);

        BaseClass userClass = context.getWiki().getUserClass(context);

        Map<String, String> map = new HashMap<String, String>();
        for (XWikiLDAPSearchAttribute lattr : searchAttributes) {
            String lval = lattr.value;
            String xattr = (String) userMappings.get(lattr.name);
            if (xattr == null) {
                continue;
            }

            map.put(xattr, lval);
        }

        // Mark user active
        map.put("active", "1");

        context.getWiki().createUser(xwikiUserName, map, userClass.getName(),
            "#includeForm(\"XWiki.XWikiUserTemplate\")", "edit", context);
    }

    /**
     * Tries to retrieve the DN from the users object.
     * 
     * @param userName the user name.
     * @param context the XWiki context.
     * @return the DN.
     */
    protected String getUserDNFromXWiki(String userName, XWikiContext context)
    {
        String dn = null;

        try {
            String user = findUser(userName, context);
            if (user != null && user.length() != 0) {
                XWikiDocument doc = context.getWiki().getDocument(userName, context);

                BaseClass userClass = context.getWiki().getUserClass(context);

                // We only allow empty password from users having a XWikiUsers object.
                if (doc.getObject(userClass.getName()) != null) {
                    dn = doc.getStringValue(userClass.getName(), "ldap_dn");
                }
            }
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Faild finding LDAP DN stored in the user object (virtual).", e);
            }
            // ignore
        }

        return dn;
    }
}