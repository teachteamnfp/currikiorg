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
package com.xpn.xwiki.store.migration.hibernate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Session;
import org.hibernate.HibernateException;
import org.hibernate.Transaction;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.store.migration.XWikiDBVersion;
import com.xpn.xwiki.store.XWikiHibernateBaseStore;
import com.xpn.xwiki.store.XWikiHibernateVersioningStore;

import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;

/**
 * Migration for XWIKI1878: Fix xwikircs table isdiff data not matching RCS state of some revisions (when the state
 * says "full" the isdiff column in the database should be false).
 *
 * Note: This migrator should only be executed if the R4359XWIKI1459 one has already been executed (i.e. if the
 * database is in version < 4360). This is because this current migrator is because of a bug in R4359XWIKI1459 which
 * has now been fixed.
 *
 * @version $Id: $
 */
public class R6079XWIKI1878Migrator extends AbstractXWikiHibernateMigrator
{
    /** logger. */
    private static final Log LOG = LogFactory.getLog(R6079XWIKI1878Migrator.class);

    /**
     * {@inheritDoc}
     * @see com.xpn.xwiki.store.migration.hibernate.AbstractXWikiHibernateMigrator#getName()
     */
    public String getName()
    {
        return "R6079XWIKI1878";
    }

    /**
     * {@inheritDoc}
     * @see AbstractXWikiHibernateMigrator#getDescription()
     */
    public String getDescription()
    {
        return "See http://jira.xwiki.org/jira/browse/XWIKI-1878";
    }

    /** {@inheritDoc} */
    public XWikiDBVersion getVersion()
    {
        return new XWikiDBVersion(6079);
    }

    /**
     * {@inheritDoc}
     * @see AbstractXWikiHibernateMigrator#shouldExecute(com.xpn.xwiki.store.migration.XWikiDBVersion)
     */
    public boolean shouldExecute(XWikiDBVersion startupVersion)
    {
        return (startupVersion.getVersion() >= 4360);
    }

    /** {@inheritDoc} */
    public void migrate(XWikiHibernateMigrationManager manager, final XWikiContext context)
        throws XWikiException
    {
        // migrate data
        manager.getStore(context).executeWrite(context, true, new XWikiHibernateBaseStore.HibernateCallback() {
            public Object doInHibernate(Session session) throws HibernateException, XWikiException
            {
                try {
                    Statement stmt = session.connection().createStatement();
                    ResultSet rs;
                    try {
                        rs = stmt.executeQuery("select xwikircs.XWR_DOCID, xwikircs.XWR_VERSION1, xwikircs.XWR_VERSION2, xwikidoc.XWD_FULLNAME from xwikircs, xwikidoc where xwikidoc.XWD_ID = xwikircs.XWR_DOCID and XWR_ISDIFF = b'1' and XWR_PATCH like '<?xml%'");
                    } catch (SQLException e) {
                        // Means the xwikircs table doesn't exist which isn't normal.
                        throw new XWikiException(XWikiException.MODULE_XWIKI_STORE,
                            XWikiException.ERROR_XWIKI_STORE_MIGRATION, "Failed to get RCS data", e);
                    }
                    Transaction originalTransaction = ((XWikiHibernateVersioningStore)context.getWiki().getVersioningStore()).getTransaction(context);
                    ((XWikiHibernateVersioningStore)context.getWiki().getVersioningStore()).setSession(null, context);
                    ((XWikiHibernateVersioningStore)context.getWiki().getVersioningStore()).setTransaction(null, context);
                    PreparedStatement updateStatement = session.connection().prepareStatement("update xwikircs set XWR_ISDIFF = b'0' where XWR_DOCID=? and XWR_VERSION1=? and XWR_VERSION2=?");

                    while (rs.next()) {
                        if (LOG.isInfoEnabled()) {
                            LOG.info("Fixing document [" + rs.getString(4) + "]...");
                        }
                        updateStatement.setLong(1, rs.getLong(1));
                        updateStatement.setInt(2, rs.getInt(2));
                        updateStatement.setInt(3, rs.getInt(3));
                        updateStatement.executeUpdate();
                    }
                    updateStatement.close();
                    stmt.close();
                    ((XWikiHibernateVersioningStore)context.getWiki().getVersioningStore()).setSession(session, context);
                    ((XWikiHibernateVersioningStore)context.getWiki().getVersioningStore()).setTransaction(originalTransaction, context);
                } catch (SQLException e) {
                    throw new XWikiException(XWikiException.MODULE_XWIKI_STORE,
                        XWikiException.ERROR_XWIKI_STORE_MIGRATION, getName() + " migration failed", e);
                }
                return Boolean.TRUE;
            }
        });
    }
}
