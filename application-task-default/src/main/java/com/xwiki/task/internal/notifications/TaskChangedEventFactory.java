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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.xwiki.model.reference.LocalDocumentReference;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.DateProperty;
import com.xpn.xwiki.objects.LargeStringProperty;
import com.xpn.xwiki.objects.PropertyInterface;
import com.xpn.xwiki.objects.StringProperty;

/**
 * Create the necessary {@link TaskChangedEvent}s for a task update.
 * 
 * @version $Id$
 * @since 3.7
 */
public final class TaskChangedEventFactory
{
    private static final LocalDocumentReference TASK_CLASS =
        new LocalDocumentReference("TaskManager", "TaskManagerClass");


    private TaskChangedEventFactory()
    {
        // Forbidden.
    }

    protected static List<TaskChangedEvent> getEvents(XWikiDocument sourceDoc, List<String> watchedFields)
    {
        XWikiDocument previousDoc = sourceDoc.getOriginalDocument();

        BaseObject currentObject = sourceDoc.getXObject(TASK_CLASS);
        BaseObject previousObject = previousDoc == null ? null : previousDoc.getXObject(TASK_CLASS);

        List<TaskChangedEvent> events = new ArrayList<>();

        watchedFields.forEach((String field) -> {
            Optional<TaskChangedEvent> taskChangedEvent =
                getFieldChangedEvent(currentObject, previousObject, field, sourceDoc);
            taskChangedEvent.ifPresent(events::add);
        });
        return events;
    }

    /**
     * Get the value of an XObject property, depending on its type.
     *
     * @param obj the base object
     * @param propertyName the property to retrieve
     * @return the value of the desired property, or null if the property doesn't exist or has unsupported type.
     */
    protected static Optional<Object> getPropertyValue(BaseObject obj, String propertyName)
    {
        if (null == obj) {
            return Optional.empty();
        }
        // Method getValue() always returns null from the Property interface implementation, so an `if` is needed for
        // each property type.
        PropertyInterface property = obj.safeget(propertyName);
        if (property instanceof StringProperty) {
            return Optional.ofNullable(((StringProperty) property).getValue());
        } else if (property instanceof LargeStringProperty) {
            return Optional.ofNullable(((LargeStringProperty) property).getValue());
        } else if (property instanceof DateProperty) {
            return Optional.ofNullable(((DateProperty) property).getValue());
        } else {
            return Optional.empty();
        }
    }

    /**
     * Get a {@link TaskChangedEvent} reflecting the changes made to a certain property, if a change happened.
     *
     * @param currentObject the current version of the object
     * @param previousObject the previous version of the object
     * @param propertyName the property to check for changes
     * @param taskPage the parent page of the modified object
     * @return the event describing the changes done to the specified field, null when no change occurred
     */
    protected static Optional<TaskChangedEvent> getFieldChangedEvent(BaseObject currentObject,
        BaseObject previousObject, String propertyName, XWikiDocument taskPage)
    {
        Object currentValue = getPropertyValue(currentObject, propertyName).orElse(null);
        Object previousValue = getPropertyValue(previousObject, propertyName).orElse(null);

        boolean valuesAreEqual;
        if (currentValue != null) {
            valuesAreEqual = currentValue.equals(previousValue);
        } else {
            valuesAreEqual = (previousValue == null);
        }

        if (valuesAreEqual) {
            return Optional.empty();
        }

        TaskChangedEvent event = new TaskChangedEvent(taskPage);

        event.setType(propertyName);

        if (currentValue != null) {
            event.setCurrentValue(currentValue);
        }
        if (previousValue != null) {
            event.setPreviousValue(previousValue);
        }

        return Optional.of(event);
    }
}
