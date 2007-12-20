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
 */
package org.xwiki.plugin.invitationmanager.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.velocity.VelocityContext;
import org.xwiki.plugin.invitationmanager.api.Invitation;
import org.xwiki.plugin.invitationmanager.api.InvitationManager;
import org.xwiki.plugin.invitationmanager.api.JoinRequest;
import org.xwiki.plugin.invitationmanager.api.JoinRequestStatus;
import org.xwiki.plugin.invitationmanager.api.MembershipRequest;
import org.xwiki.plugin.spacemanager.api.Space;
import org.xwiki.plugin.spacemanager.api.SpaceManager;
import org.xwiki.plugin.spacemanager.api.SpaceManagers;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.plugin.mailsender.MailSenderPlugin;

/**
 * The default implementation for {@link InvitationManager}
 */
public class InvitationManagerImpl implements InvitationManager
{
    public static interface JoinRequestAction
    {
        String SEND = "Send";

        String ACCEPT = "Accept";

        String REJECT = "Reject";
    }

    public static final String SPACE_VELOCITY_KEY = "space";

    public static final String INVITATION_VELOCITY_KEY = "invitation";

    public static final String MEMBERSHIP_REQUEST_VELOCITY_KEY = "request";

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#acceptInvitation(String, String, String, XWikiContext)
     */
    public void acceptInvitation(String space, String email, String code, XWikiContext context)
    {
        try {
            Invitation invitation = getInvitation(space, encodeEmailAddress(email), context);
            if (code.equals(invitation.getCode())
                && invitation.getStatus() == JoinRequestStatus.SENT) {
                if (!invitation.isOpen()) {
                    invitation.setStatus(JoinRequestStatus.ACCEPTED);
                    invitation.setResponseDate(new Date());
                    invitation.setInvitee(context.getUser());
                    invitation.save();
                }
                // create a custom invitation for the currently logged-in user
                customizeInvitation(invitation, JoinRequestStatus.ACCEPTED, context);
                // update the list of space members and their roles
                addMember(space, context.getUser(), invitation.getRoles(), context);
            }
        } catch (XWikiException e) {
            e.printStackTrace();
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#acceptInvitation(String, XWikiContext)
     */
    public void acceptInvitation(String space, XWikiContext context)
    {
        try {
            Invitation invitation = getInvitation(space, context.getUser(), context);
            int status = invitation.getStatus();
            if (status == JoinRequestStatus.SENT || status == JoinRequestStatus.REFUSED) {
                // update the invitation object
                invitation.setResponseDate(new Date());
                invitation.setStatus(JoinRequestStatus.ACCEPTED);
                invitation.save();
                // update the list of members and their roles
                addMember(space, context.getUser(), invitation.getRoles(), context);
            }
        } catch (XWikiException e) {
            e.printStackTrace();
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#acceptMembership(String, String, String, XWikiContext)
     */
    public void acceptMembership(String space, String userName, String templateMail,
        XWikiContext context)
    {
        try {
            MembershipRequest request = getMembershipRequest(space, userName, context);
            int status = request.getStatus();
            if (status == JoinRequestStatus.SENT || status == JoinRequestStatus.REFUSED) {
                // update the membership request object
                request.setResponseDate(new Date());
                request.setResponder(context.getUser());
                request.setStatus(JoinRequestStatus.ACCEPTED);
                request.save();
                // update the list of members and their roles
                addMember(space, userName, request.getRoles(), context);
                // send notification mail
                sendMail(JoinRequestAction.ACCEPT, request, templateMail, context);
            }
        } catch (XWikiException e) {
            e.printStackTrace();
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#acceptMembership(String, String, XWikiContext)
     */
    public void acceptMembership(String space, String userName, XWikiContext context)
    {
        String templateMail =
            getDefaultTemplateMailDocumentName(space, MembershipRequest.class,
                JoinRequestAction.ACCEPT, context);
        acceptMembership(space, userName, templateMail, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#cancelInvitation(String, String, XWikiContext)
     */
    public void cancelInvitation(String user, String space, XWikiContext context)
    {
        try {
            Invitation invitation = getInvitation(space, user, context);
            if (invitation.getStatus() == JoinRequestStatus.SENT) {
                invitation.setStatus(JoinRequestStatus.CANCELLED);
                invitation.save();
            }
        } catch (XWikiException e) {
            e.printStackTrace();
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#cancelMembershipRequest(String, XWikiContext)
     */
    public void cancelMembershipRequest(String space, XWikiContext context)
    {
        try {
            MembershipRequest request = getMembershipRequest(space, context.getUser(), context);
            if (request.getStatus() == JoinRequestStatus.SENT) {
                request.setStatus(JoinRequestStatus.CANCELLED);
                request.save();
            }
        } catch (XWikiException e) {
            e.printStackTrace();
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getInvitations(int, int, int, XWikiContext)
     */
    public List getInvitations(int status, int start, int count, XWikiContext context)
    {
        try {
            Invitation prototype = createInvitation(context.getUser(), null, context);
            prototype.setStatus(status);
            return getInvitations(prototype, start, count, context);
        } catch (XWikiException e) {
            e.printStackTrace();
            return Collections.EMPTY_LIST;
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getInvitations(int, int, XWikiContext)
     */
    public List getInvitations(int start, int count, XWikiContext context)
    {
        return getInvitations(JoinRequestStatus.ANY, start, count, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getInvitations(int, XWikiContext)
     */
    public List getInvitations(int status, XWikiContext context)
    {
        return getInvitations(status, 0, 0, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getInvitations(Invitation, int, int, XWikiContext)
     */
    public List getInvitations(Invitation prototype, int start, int count, XWikiContext context)
    {
        try {
            String className = getJoinRequestClassName(Invitation.class);
            StringBuffer fromClause =
                new StringBuffer("from XWikiDocument as doc, BaseObject as obj");
            StringBuffer whereClause =
                new StringBuffer("where doc.fullName=obj.name and obj.className = '" + className
                    + "'");

            String space = prototype.getSpace();
            String invitee = prototype.getInvitee();
            if (space != null && invitee != null) {
                Invitation invitation = getInvitation(space, invitee, context);
                // find a better way of testing if the invitation is new
                if (((XWikiDocument) invitation).isNew()) {
                    return Collections.EMPTY_LIST;
                }
                List list = new ArrayList();
                list.add(invitation);
                return list;
            } else if (space != null) {
                fromClause.append(", StringProperty as space");
                whereClause
                    .append(" and obj.id=space.id.id and space.id.name='"
                        + InvitationImpl.InvitationFields.SPACE + "' and space.value='" + space
                        + "'");
            } else if (invitee != null) {
                fromClause.append(" StringProperty as invitee");
                whereClause.append(" and obj.id=invitee.id.id and invitee.id.name='"
                    + InvitationImpl.InvitationFields.INVITEE + "' and invitee.value='" + invitee
                    + "'");
            }

            int status = prototype.getStatus();
            if (status != JoinRequestStatus.ANY) {
                fromClause.append(", IntegerProperty as status");
                whereClause.append(" and obj.id=status.id.id and status.id.name='"
                    + InvitationImpl.InvitationFields.STATUS + "' and status.value=" + status);
            }

            List roles = prototype.getRoles();
            if (roles != null && roles.size() > 0) {
                String role = (String) prototype.getRoles().get(0);
                fromClause.append(", DBStringListProperty as roles join roles.list as role");
                whereClause.append(" and obj.id=roles.id.id and roles.id.name='"
                    + InvitationImpl.InvitationFields.ROLES + "' and instr(role.id, '" + role
                    + "')>0");
            }

            String inviter = prototype.getInviter();
            if (inviter != null) {
                fromClause.append(" StringProperty as inviter");
                whereClause.append(" and obj.id=inviter.id.id and inviter.id.name='"
                    + InvitationImpl.InvitationFields.INVITER + "' and inviter.value='" + inviter
                    + "'");
            }

            String sql =
                "select distinct doc " + fromClause.toString() + " " + whereClause.toString();
            return context.getWiki().getStore().search(sql, count, start, context);
        } catch (XWikiException e) {
            e.printStackTrace();
            return Collections.EMPTY_LIST;
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getInvitations(Invitation, XWikiContext)
     */
    public List getInvitations(Invitation prototype, XWikiContext context)
    {
        return getInvitations(prototype, 0, 0, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getInvitations(String, int, int, int, XWikiContext)
     */
    public List getInvitations(String space, int status, int start, int count,
        XWikiContext context)
    {
        return getInvitations(space, status, null, start, count, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getInvitations(String, int, int, XWikiContext)
     */
    public List getInvitations(String space, int start, int count, XWikiContext context)
    {
        return getInvitations(space, JoinRequestStatus.ANY, start, count, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getInvitations(String, int, String, int, int, XWikiContext)
     */
    public List getInvitations(String space, int status, String role, int start, int count,
        XWikiContext context)
    {
        try {
            List roles = new ArrayList();
            roles.add(role);
            Invitation prototype = createInvitation(null, space, context);
            prototype.setStatus(status);
            prototype.setRoles(roles);
            return getInvitations(prototype, start, count, context);
        } catch (XWikiException e) {
            e.printStackTrace();
            return Collections.EMPTY_LIST;
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getInvitations(String, int, String, XWikiContext)
     */
    public List getInvitations(String space, int status, String role, XWikiContext context)
    {
        return getInvitations(space, status, role, 0, 0, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getInvitations(String, int, XWikiContext)
     */
    public List getInvitations(String space, int status, XWikiContext context)
    {
        return getInvitations(space, status, 0, 0, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getInvitations(String, String, int, int, XWikiContext)
     */
    public List getInvitations(String space, String role, int start, int count,
        XWikiContext context)
    {
        return getInvitations(space, JoinRequestStatus.ANY, role, start, count, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getInvitations(String, String, XWikiContext)
     */
    public List getInvitations(String space, String role, XWikiContext context)
    {
        return getInvitations(space, role, 0, 0, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getInvitations(String, XWikiContext)
     */
    public List getInvitations(String space, XWikiContext context)
    {
        return getInvitations(space, 0, 0, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getInvitations(XWikiContext)
     */
    public List getInvitations(XWikiContext context)
    {
        return getInvitations(0, 0, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getMembershipRequests(int, int, int, XWikiContext)
     */
    public List getMembershipRequests(int status, int start, int count, XWikiContext context)
    {
        try {
            MembershipRequest prototype =
                createMembershipRequest(context.getUser(), null, context);
            prototype.setStatus(status);
            return getMembershipRequests(prototype, start, count, context);
        } catch (XWikiException e) {
            e.printStackTrace();
            return Collections.EMPTY_LIST;
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getMembershipRequests(int, int, XWikiContext)
     */
    public List getMembershipRequests(int start, int count, XWikiContext context)
    {
        return getMembershipRequests(JoinRequestStatus.ANY, start, count, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getMembershipRequests(int, XWikiContext)
     */
    public List getMembershipRequests(int status, XWikiContext context)
    {
        return getMembershipRequests(status, 0, 0, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getMembershipRequests(MembershipRequest, int, int, XWikiContext)
     */
    public List getMembershipRequests(MembershipRequest prototype, int start, int count,
        XWikiContext context)
    {
        try {
            String className = getJoinRequestClassName(MembershipRequest.class);
            StringBuffer fromClause =
                new StringBuffer("from XWikiDocument as doc, BaseObject as obj");
            StringBuffer whereClause =
                new StringBuffer("where doc.fullName=obj.name and obj.className = '" + className
                    + "'");

            String space = prototype.getSpace();
            String requester = prototype.getRequester();
            if (space != null && requester != null) {
                MembershipRequest request = getMembershipRequest(space, requester, context);
                // find a better way of testing if the request is new
                if (((XWikiDocument) request).isNew()) {
                    return Collections.EMPTY_LIST;
                }
                List list = new ArrayList();
                list.add(request);
                return list;
            } else if (space != null) {
                fromClause.append(", StringProperty as space");
                whereClause.append(" and obj.id=space.id.id and space.id.name='"
                    + MembershipRequestImpl.MembershipRequestFields.SPACE + "' and space.value='"
                    + space + "'");
            } else if (requester != null) {
                fromClause.append(" StringProperty as requester");
                whereClause.append(" and obj.id=requester.id.id and requester.id.name='"
                    + MembershipRequestImpl.MembershipRequestFields.REQUESTER
                    + "' and requester.value='" + requester + "'");
            }

            int status = prototype.getStatus();
            if (status != JoinRequestStatus.ANY) {
                fromClause.append(", IntegerProperty as status");
                whereClause.append(" and obj.id=status.id.id and status.id.name='"
                    + MembershipRequestImpl.MembershipRequestFields.STATUS
                    + "' and status.value=" + status);
            }

            List roles = prototype.getRoles();
            if (roles != null && roles.size() > 0) {
                String role = (String) prototype.getRoles().get(0);
                fromClause.append(", DBStringListProperty as roles join roles.list as role");
                whereClause.append(" and obj.id=roles.id.id and roles.id.name='"
                    + MembershipRequestImpl.MembershipRequestFields.ROLES
                    + "' and instr(role.id, '" + role + "')>0");
            }

            String responder = prototype.getResponder();
            if (responder != null) {
                fromClause.append(" StringProperty as responder");
                whereClause.append(" and obj.id=responder.id.id and responder.id.name='"
                    + MembershipRequestImpl.MembershipRequestFields.RESPONDER
                    + "' and responder.value='" + responder + "'");
            }

            String sql =
                "select distinct doc " + fromClause.toString() + " " + whereClause.toString();
            return context.getWiki().getStore().search(sql, count, start, context);
        } catch (XWikiException e) {
            e.printStackTrace();
            return Collections.EMPTY_LIST;
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getMembershipRequests(MembershipRequest, XWikiContext)
     */
    public List getMembershipRequests(MembershipRequest prototype, XWikiContext context)
    {
        return getMembershipRequests(prototype, 0, 0, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getMembershipRequests(String, int, int, int, XWikiContext)
     */
    public List getMembershipRequests(String space, int status, int start, int count,
        XWikiContext context)
    {
        return getMembershipRequests(space, status, null, start, count, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getMembershipRequests(String, int, int, XWikiContext)
     */
    public List getMembershipRequests(String space, int start, int count, XWikiContext context)
    {
        return getMembershipRequests(space, JoinRequestStatus.ANY, start, count, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getMembershipRequests(String, int, String, int, int, XWikiContext)
     */
    public List getMembershipRequests(String space, int status, String role, int start,
        int count, XWikiContext context)
    {
        try {
            List roles = new ArrayList();
            roles.add(role);
            MembershipRequest prototype = createMembershipRequest(null, space, context);
            prototype.setStatus(status);
            prototype.setRoles(roles);
            return getMembershipRequests(prototype, start, count, context);
        } catch (XWikiException e) {
            e.printStackTrace();
            return Collections.EMPTY_LIST;
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getMembershipRequests(String, int, String, XWikiContext)
     */
    public List getMembershipRequests(String space, int status, String role, XWikiContext context)
    {
        return getMembershipRequests(space, status, role, 0, 0, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getMembershipRequests(String, int, XWikiContext)
     */
    public List getMembershipRequests(String space, int status, XWikiContext context)
    {
        return getMembershipRequests(space, status, 0, 0, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getMembershipRequests(String, String, int, int, XWikiContext)
     */
    public List getMembershipRequests(String space, String role, int start, int count,
        XWikiContext context)
    {
        return getMembershipRequests(space, JoinRequestStatus.ANY, role, start, count, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getMembershipRequests(String, String, XWikiContext)
     */
    public List getMembershipRequests(String space, String role, XWikiContext context)
    {
        return getMembershipRequests(space, role, 0, 0, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getMembershipRequests(String, XWikiContext)
     */
    public List getMembershipRequests(String space, XWikiContext context)
    {
        return getMembershipRequests(space, 0, 0, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#getMembershipRequests(XWikiContext)
     */
    public List getMembershipRequests(XWikiContext context)
    {
        return getMembershipRequests(0, 0, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#inviteUser(String, String, List, String, Map, XWikiContext)
     */
    public void inviteUser(String wikiNameOrMailAddress, String space, boolean open, List roles,
        String templateMail, Map map, XWikiContext context)
    {
        try {
            String invitee;
            String registeredUser = getRegisteredUser(wikiNameOrMailAddress, context);
            if (registeredUser == null) {
                // hide the e-mail address (only for invitation document name)
                invitee = encodeEmailAddress(wikiNameOrMailAddress);
            } else {
                invitee = registeredUser;
            }
            // create the invitation object
            Invitation invitation = createInvitation(invitee, space, context);
            invitation.setInviter(context.getUser());
            invitation.setMap(map);
            invitation.setOpen(open);
            invitation.setRequestDate(new Date());
            invitation.setRoles(roles);
            invitation.setStatus(JoinRequestStatus.SENT);
            if (registeredUser == null) {
                invitation.setCode(generateInvitationCode());
                // make the e-mail address available in the invitee field
                invitation.setInvitee(wikiNameOrMailAddress);
            }
            invitation.save();
            // send a notification mail
            sendMail(JoinRequestAction.SEND, invitation, templateMail, context);
        } catch (XWikiException e) {
            e.printStackTrace();
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#inviteUser(String, String, List, String, XWikiContext)
     */
    public void inviteUser(String user, String space, boolean open, List roles,
        String templateMail, XWikiContext context)
    {
        inviteUser(user, space, open, roles, templateMail, Collections.EMPTY_MAP, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#inviteUser(String, String, List, XWikiContext)
     */
    public void inviteUser(String user, String space, boolean open, List roles,
        XWikiContext context)
    {
        String templateMail =
            getDefaultTemplateMailDocumentName(space, Invitation.class, JoinRequestAction.SEND,
                context);
        inviteUser(user, space, open, roles, templateMail, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#inviteUser(String, String, String, XWikiContext)
     */
    public void inviteUser(String user, String space, boolean open, String role,
        XWikiContext context)
    {
        List roles = new ArrayList();
        roles.add(role);
        inviteUser(user, space, open, roles, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#inviteUser(String, String, XWikiContext)
     */
    public void inviteUser(String user, String space, boolean open, XWikiContext context)
    {
        inviteUser(user, space, open, Collections.EMPTY_LIST, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#rejectInvitation(String, String, String, XWikiContext)
     */
    public void rejectInvitation(String space, String email, String code, XWikiContext context)
    {
        try {
            Invitation invitation = getInvitation(space, encodeEmailAddress(email), context);
            if (code.equals(invitation.getCode())
                && invitation.getStatus() == JoinRequestStatus.SENT) {
                if (!invitation.isOpen()) {
                    invitation.setStatus(JoinRequestStatus.REFUSED);
                    invitation.setResponseDate(new Date());
                    invitation.setInvitee(context.getUser());
                    invitation.save();
                }
                // create a custom invitation for the currently logged-in user
                customizeInvitation(invitation, JoinRequestStatus.REFUSED, context);
            }
        } catch (XWikiException e) {
            e.printStackTrace();
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#rejectInvitation(String, XWikiContext)
     */
    public void rejectInvitation(String space, XWikiContext context)
    {
        try {
            Invitation invitation = getInvitation(space, context.getUser(), context);
            if (invitation.getStatus() == JoinRequestStatus.SENT) {
                invitation.setStatus(JoinRequestStatus.REFUSED);
                invitation.setResponseDate(new Date());
                invitation.save();
            }
        } catch (XWikiException e) {
            e.printStackTrace();
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#rejectMembership(String, String, String, XWikiContext)
     */
    public void rejectMembership(String space, String userName, String templateMail,
        XWikiContext context)
    {
        try {
            MembershipRequest request = getMembershipRequest(space, userName, context);
            if (request.getStatus() == JoinRequestStatus.SENT) {
                request.setStatus(JoinRequestStatus.REFUSED);
                request.setResponseDate(new Date());
                request.setResponder(context.getUser());
                request.save();
                sendMail(JoinRequestAction.REJECT, request, templateMail, context);
            }
        } catch (XWikiException e) {
            e.printStackTrace();
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#rejectMembership(String, String, XWikiContext)
     */
    public void rejectMembership(String space, String userName, XWikiContext context)
    {
        String templateMail =
            getDefaultTemplateMailDocumentName(space, MembershipRequest.class,
                JoinRequestAction.REJECT, context);
        rejectMembership(space, userName, templateMail, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#requestMembership(String, String, List, Map, XWikiContext)
     */
    public void requestMembership(String space, String message, List roles, Map map,
        XWikiContext context)
    {
        try {
            MembershipRequest request =
                createMembershipRequest(context.getUser(), space, context);
            request.setMap(map);
            request.setRequestDate(new Date());
            request.setRoles(roles);
            request.setStatus(JoinRequestStatus.SENT);
            request.setText(message);
            request.save();
        } catch (XWikiException e) {
            e.printStackTrace();
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#requestMembership(String, String, List, XWikiContext)
     */
    public void requestMembership(String space, String message, List roles, XWikiContext context)
    {
        requestMembership(space, message, roles, Collections.EMPTY_MAP, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#requestMembership(String, String, String, XWikiContext)
     */
    public void requestMembership(String space, String message, String role, XWikiContext context)
    {
        List roles = new ArrayList();
        roles.add(role);
        requestMembership(space, message, roles, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see InvitationManager#requestMembership(String, String, XWikiContext)
     */
    public void requestMembership(String space, String message, XWikiContext context)
    {
        requestMembership(space, message, Collections.EMPTY_LIST, context);
    }

    /**
     * @return the name of the document (wiki page) that is the default mail template to be used for
     *         notifying the execution of the specified action on a request of class
     *         <code>joinRequestClass</code> related to the given space.
     */
    private String getDefaultTemplateMailDocumentName(String space, Class joinRequestClass,
        String action, XWikiContext context)
    {
        String docName = space + "." + "MailTemplate" + action + joinRequestClass.getName();
        try {
            context.getWiki().getDocument(docName, context);
        } catch (XWikiException e) {
            docName =
                InvitationManager.class.getName() + "." + "MailTemplate" + action
                    + joinRequestClass.getName();
        }
        return docName;
    }

    /**
     * @return the name of the document (wiki page) to be used for storing a request of class
     *         <code>joinRequestClass</code> that would allow the specified user to join the
     *         specified space.
     */
    public String getJoinRequestDocumentName(Class joinRequestClass, String space, String user)
    {
        if (space == null) {
            space = System.currentTimeMillis() + "";
        }
        if (user == null) {
            user = System.currentTimeMillis() + "";
        }
        return space + "_" + InvitationManager.class.getName() + "." + joinRequestClass.getName()
            + "_" + user;
    }

    /**
     * @return the xwiki class associated with the given join request type
     */
    public String getJoinRequestClassName(Class joinRequestType)
    {
        return "XWiki." + joinRequestType.getName() + "Class";
    }

    /**
     * @return the stored invitation uniquely identified by the given space and invitee
     */
    private Invitation getInvitation(String space, String invitee, XWikiContext context)
        throws XWikiException
    {
        return new InvitationImpl(invitee, space, false, this, context);
    }

    /**
     * @return a newly created invitation to be sent to the <code>invitee</code> in order to join
     *         the <code>space</code>
     */
    private Invitation createInvitation(String invitee, String space, XWikiContext context)
        throws XWikiException
    {
        return new InvitationImpl(invitee, space, true, this, context);
    }

    /**
     * Creates a custom invitation for the currently logged-in user, from the given one, and then
     * saves it.
     */
    private void customizeInvitation(Invitation invitation, int status, XWikiContext context)
        throws XWikiException
    {
        Invitation customInvitation =
            createInvitation(context.getUser(), invitation.getSpace(), context);
        customInvitation.setInviter(invitation.getInviter());
        customInvitation.setMap(invitation.getMap());
        customInvitation.setRequestDate(invitation.getRequestDate());
        customInvitation.setResponseDate(new Date());
        customInvitation.setRoles(invitation.getRoles());
        customInvitation.setStatus(status);
        customInvitation.setOpen(false);
        customInvitation.setText(invitation.getText());
        customInvitation.save();
    }

    /**
     * @return the stored membership request uniquely identified by the given space and requester
     */
    private MembershipRequest getMembershipRequest(String space, String requester,
        XWikiContext context) throws XWikiException
    {
        return new MembershipRequestImpl(requester, space, false, this, context);
    }

    /**
     * @return a newly created membership request to be sent by the <code>requester</code> in
     *         order to join the <code>space</code>
     */
    private MembershipRequest createMembershipRequest(String requester, String space,
        XWikiContext context) throws XWikiException
    {
        return new MembershipRequestImpl(requester, space, true, this, context);
    }

    /**
     * @return the encoded email address to be used when naming the document (wiki page) storing the
     *         invitation to the unregistered user with the given email address
     */
    private String encodeEmailAddress(String emailAddress)
    {
        return emailAddress.hashCode() + "";
    }

    /**
     * @return a new random code for an invitation
     */
    private String generateInvitationCode()
    {
        return RandomStringUtils.randomAlphabetic(8).toLowerCase();
    }

    /**
     * Wrapper method for adding a user to a space and to the given roles using the space manager
     */
    private void addMember(String space, String user, List roles, XWikiContext context)
        throws XWikiException
    {
        SpaceManager spaceManager = SpaceManagers.findSpaceManagerForSpace(space, context);
        if (!spaceManager.userIsMember(space, user, context)) {
            spaceManager.addMember(space, user, context);
            if (roles != null && roles.size() > 0) {
                spaceManager.addUserToRoles(space, user, roles, context);
            }
        }
    }

    /**
     * Wrapper method for sending a mail using the mail sender plug-in
     */
    private void sendMail(String action, JoinRequest request, String templateDocFullName,
        XWikiContext context) throws XWikiException
    {
        VelocityContext vContext = new VelocityContext();
        String spaceName = request.getSpace();
        SpaceManager spaceManager = SpaceManagers.findSpaceManagerForSpace(spaceName, context);
        Space space = spaceManager.getSpace(spaceName, context);
        vContext.put(SPACE_VELOCITY_KEY, space);
        String fromUser = null, toUser = null;
        if (request instanceof Invitation) {
            Invitation invitation = (Invitation) request;
            vContext.put(INVITATION_VELOCITY_KEY, invitation);
            if (JoinRequestAction.SEND.equals(action)) {
                // invitation notification mail
                fromUser = invitation.getInviter();
                toUser = invitation.getInvitee();
            } else {
                // accept or reject invitation mail
                fromUser = invitation.getInvitee();
                toUser = invitation.getInviter();
            }
        } else if (request instanceof MembershipRequest) {
            MembershipRequest membershipRequest = (MembershipRequest) request;
            vContext.put(MEMBERSHIP_REQUEST_VELOCITY_KEY, membershipRequest);
            if (JoinRequestAction.SEND.equals(action)) {
                // membership request notification mail
                fromUser = membershipRequest.getRequester();
                toUser = membershipRequest.getResponder();
            } else {
                // accept or reject membership request mail
                fromUser = membershipRequest.getResponder();
                toUser = membershipRequest.getRequester();
            }
        }
        if (!isEmailAddress(fromUser)) {
            fromUser = getEmailAddress(fromUser, context);
        }
        if (!isEmailAddress(toUser)) {
            toUser = getEmailAddress(toUser, context);
        }
        MailSenderPlugin mailSender =
            (MailSenderPlugin) context.getWiki().getPlugin("mailsender", context);
        mailSender.sendMailFromTemplate(templateDocFullName, fromUser, toUser, "", "", context
            .getLanguage(), vContext, context);
    }

    /**
     * @return the wiki name of the registered user with the given
     *         <code>wikiNameOrMailAddress</code>
     * @throws XWikiException if there is no registered user with the given wiki name or email
     *             address
     */
    private String getRegisteredUser(String wikiNameOrMailAddress, XWikiContext context)
        throws XWikiException
    {
        if (isEmailAddress(wikiNameOrMailAddress)) {
            String email = wikiNameOrMailAddress;
            String sql =
                "select distinct doc.name from XWikiDocument as doc, BaseObject as obj, StringProperty as prop where doc.fullName=obj.name and obj.className = 'XWiki.XWikiUsers' and obj.id=prop.id.id and prop.id.name='email' and prop.value='"
                    + email + "'";
            List userList = context.getWiki().getStore().search(sql, 1, 0, context);
            if (userList.size() > 0) {
                return (String) userList.get(0);
            } else {
                return email;
            }
        } else {
            String user = findUser(wikiNameOrMailAddress, context);
            if (user == null) {
                throw new XWikiException(-1, -1, "Unregistered user!");
            } else {
                return user;
            }
        }
    }

    /**
     * Helper method for testing if a given string is an email address.
     */
    private boolean isEmailAddress(String str)
    {
        return str.contains("@");
    }

    /**
     * @return the email address of the given user, provided he is registered
     */
    private String getEmailAddress(String user, XWikiContext context) throws XWikiException
    {
        user = findUser(user, context);
        XWikiDocument userDoc = context.getWiki().getDocument(user, context);
        return userDoc.getObject("XWiki.XWikiUsers").getStringValue("email");
    }

    // copy & paste from XWikiAuthServiceImpl#findUser(String, XWikiContext)
    private String findUser(String username, XWikiContext context) throws XWikiException
    {
        String user;

        // First let's look in the cache
        if (context.getWiki().exists("XWiki." + username, context)) {
            user = "XWiki." + username;
        } else {
            // Note: The result of this search depends on the Database. If the database is
            // case-insensitive (like MySQL) then users will be able to log in by entering their
            // username in any case. For case-sensitive databases (like HSQLDB) they'll need to
            // enter it exactly as they've created it.
            String sql = "select distinct doc.fullName from XWikiDocument as doc";
            Object[][] whereParameters =
                new Object[][] { {"doc.web", "XWiki"}, {"doc.name", username}};

            List list = context.getWiki().search(sql, whereParameters, context);
            if (list.size() == 0) {
                user = null;
            } else {
                user = (String) list.get(0);
            }
        }

        return user;
    }
}
