package org.curriki.xwiki.servlet.restlet.router;

import org.restlet.Context;
import org.restlet.Router;
import org.restlet.util.Template;
import org.curriki.xwiki.servlet.restlet.resource.users.UserCollectionsResource;
import org.curriki.xwiki.servlet.restlet.resource.users.UserGroupsResource;
import org.curriki.xwiki.servlet.restlet.resource.users.UserResource;

/**
 */
public class UsersRouter extends Router {
    public UsersRouter(Context context) {
        super(context);
        attach("/me", UserResource.class).getTemplate().setMatchingMode(Template.MODE_EQUALS);
        attach("/{userName}/groups", UserGroupsResource.class).getTemplate().setMatchingMode(Template.MODE_EQUALS);
        attach("/{userName}/collections", UserCollectionsResource.class).getTemplate().setMatchingMode(Template.MODE_EQUALS);
    }
}
