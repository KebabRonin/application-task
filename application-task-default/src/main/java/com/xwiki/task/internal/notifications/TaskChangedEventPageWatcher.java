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

package com.xwiki.task.internal.notifications;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.notifications.NotificationException;
import org.xwiki.notifications.NotificationFormat;
import org.xwiki.notifications.filters.NotificationFilterPreference;
import org.xwiki.notifications.filters.NotificationFilterPreferenceManager;
import org.xwiki.notifications.filters.NotificationFilterType;
import org.xwiki.notifications.filters.internal.DefaultNotificationFilterPreference;
import org.xwiki.notifications.filters.internal.scope.ScopeNotificationFilter;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Class which handles notification filters for {@link TaskChangedEventListener}.
 * 
 * @version $Id$
 * @since 3.7
 */
@Component
@Singleton
@Named("com.xwiki.task.internal.notifications.TaskChangedEventPageWatcher")
public class TaskChangedEventPageWatcher
{

    @Inject
    private NotificationFilterPreferenceManager notificationFilterPreferenceManager;

    @Inject
    private EntityReferenceSerializer<String> entityReferenceSerializer;

    @Inject
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Inject
    private Logger logger;
    
    /**
     * Taken from {@link org.xwiki.notifications.filters.internal.DefaultFilterPreferencesModelBridge}.
     */
    private NotificationFilterPreference getScopeFilterPreference(String userFullName, EntityReference pageReference)
        throws NotificationException
    {
        DefaultNotificationFilterPreference preference = new DefaultNotificationFilterPreference();
        preference.setFilterType(NotificationFilterType.INCLUSIVE);
        preference.setNotificationFormats(
            // Sure buddy, but you'll have to get this from the user profile. Now how do you plan to do that?
            // Maybe putting it on all will still respect the global TaskChangedEvent preference?
            new HashSet<NotificationFormat>(Arrays.asList(NotificationFormat.ALERT, NotificationFormat.EMAIL))
        );
        preference.setEventTypes(new HashSet<String>(Arrays.asList(TaskChangedEvent.class.getName())));
        preference.setEnabled(true);
        preference.setUser(userFullName);
        preference.setFilterName(ScopeNotificationFilter.FILTER_NAME);
        preference.setStartingDate(new Date());
        // Watch page without children.
        preference.setPageOnly(entityReferenceSerializer.serialize(pageReference));
        // ScopeNotificationFilterPreference(preference, EntityReferenceResolver<String>)
        return preference;
    }

    /**
     * Get the NotificationFilterPreferences which are concerning {@link TaskChangedEvent} for a page.
     * 
     * @param userFullName the path to the user page, used for getting the user document.
     * @param page the page to check if it has a filter for TaskChangedEvent.
     * @return The Id of the first such filter, if one exists.
     * @throws NotificationException
     */
    private Optional<String> getExistingNotificationFilterPreference(String userFullName, XWikiDocument page)
        throws NotificationException
    {
        DocumentReference userRef = documentReferenceResolver.resolve(userFullName);
        List<NotificationFilterPreference> matchingNotificationFilterPreferences =
            notificationFilterPreferenceManager.getFilterPreferences(userRef).stream()
            .filter(pref ->
                pref.getFilterName().equals(ScopeNotificationFilter.FILTER_NAME)
                && pref.getPage().equals(page.getDocumentReference().toString())
            ).collect(Collectors.toList());
        if (matchingNotificationFilterPreferences.size() == 0) {
            return Optional.empty();
        } else {
            // if (matchingNotificationFilterPreferences > 1) just delete the ones which are defined twice?
            // /\/\/\/\/\/\/\ Can this even happen? 
            return Optional.of(matchingNotificationFilterPreferences.get(0).getId());
        }
    }

    /**
     * Watch the task page for the given user.
     * 
     * @param taskDoc the document containing a TaskManagerClass object
     * @param userFullName the user to watch the task for
     */
    protected void watchTask(XWikiDocument taskDoc, String userFullName)
    {
        if (!userFullName.equals("")) {
            try {
                NotificationFilterPreference n = getScopeFilterPreference(
                    userFullName,
                    (EntityReference) taskDoc.getDocumentReference()
                );
                DocumentReference userRef = documentReferenceResolver.resolve(userFullName);
                notificationFilterPreferenceManager.saveFilterPreferences(userRef, new HashSet<>(Arrays.asList(n)));
            } catch (NotificationException e) {
                logger.error("Failed to watch task page [{}] for user [{}]. Cause:", taskDoc, userFullName, e);
            }
        }
    }

    /**
     * Unwatch the task page for the given user.
     * 
     * @param taskDoc the document containing a TaskManagerClass object
     * @param userFullName the user to unwatch the task for
     */
    protected void unwatchTask(XWikiDocument taskDoc, String userFullName)
    {
        if (!userFullName.equals("")) {
            DocumentReference userRef = documentReferenceResolver.resolve(userFullName);
            try {
                notificationFilterPreferenceManager.deleteFilterPreference(
                    userRef, 
                    getExistingNotificationFilterPreference(userFullName, taskDoc).get()
                );
            } catch (NotificationException e) {
                logger.error("Failed to unwatch task page [{}] for user [{}]. Cause:", taskDoc, userFullName, e);
            }
        }
    }
}
