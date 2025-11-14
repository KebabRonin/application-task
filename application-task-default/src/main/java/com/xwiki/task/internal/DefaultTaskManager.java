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
package com.xwiki.task.internal;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.task.TaskException;
import com.xwiki.task.TaskManager;
import com.xwiki.task.model.Task;

/**
 * Default implementation of {@link TaskManager}.
 *
 * @version $Id$
 * @since 3.0
 */
@Component
@Singleton
public class DefaultTaskManager implements TaskManager
{
    private static final LocalDocumentReference TASK_CLASS_REFERENCE =
        new LocalDocumentReference(Collections.singletonList("TaskManager"), "TaskManagerClass");

    @Inject
    private DocumentReferenceResolver<String> resolver;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private QueryManager queryManager;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Inject
    @Named("compactwiki")
    private EntityReferenceSerializer<String> compactserializer;

    @Override
    public Task getTask(DocumentReference reference) throws TaskException
    {
        return getTask((EntityReference) reference);
    }

    @Override
    public Task getTask(EntityReference reference) throws TaskException
    {
        XWikiContext context = contextProvider.get();
        try {
            XWikiDocument doc = context.getWiki().getDocument(reference, context);
            BaseObject obj = doc.getXObject(TASK_CLASS_REFERENCE);
            if (obj == null) {
                throw new TaskException(String.format("The page [%s] does not have a Task Object.", reference));
            }
            return getTaskFromXObject(obj);
        } catch (XWikiException e) {
            throw new TaskException(String.format("Failed to retrieve the task from the page [%s]", reference));
        }
    }

    @Override
    public Task getTask(int id) throws TaskException
    {
        try {
            XWikiContext context = contextProvider.get();
            String statement = ", BaseObject as taskObj, IntegerProperty as idProp "
                + "WHERE taskObj.name = doc.fullName "
                + "AND doc.space != 'TaskManager.TaskManagerTemplates' "
                + "AND taskObj.className = 'TaskManager.TaskManagerClass' "
                + "AND taskObj.id = idProp.id.id AND idProp.id.name = 'number' "
                + "AND idProp.value = :id";

            List<String> results =
                queryManager.createQuery(statement, Query.HQL).setWiki(context.getWikiId()).bindValue("id", id)
                    .execute();
            if (results.size() > 0) {
                DocumentReference documentReference = resolver.resolve(results.get(0), context.getWikiReference());
                XWikiDocument document = context.getWiki().getDocument(documentReference, context);
                BaseObject taskObject = document.getXObject(TASK_CLASS_REFERENCE);
                if (taskObject == null) {
                    throw new TaskException(
                        String.format("Could not retrieve the task object [%s] associated with the task with id [%d]",
                            documentReference, id));
                }
                return getTaskFromXObject(taskObject);
            }
            throw new TaskException(String.format("There is no task with the id [%d].", id));
        } catch (QueryException | XWikiException e) {
            throw new TaskException(String.format("Failed to retrieve the task with id [%s].", id), e);
        }
    }

    @Override
    public void deleteTasksByOwner(DocumentReference documentReference) throws TaskException
    {
        try {
            XWikiContext context = contextProvider.get();
            String statement =
                "FROM doc.object(TaskManager.TaskManagerClass) as task "
                    + "WHERE task.owner = :absoluteOwnerRef "
                    + "OR task.owner = :compactOwnerRef "
                    + "OR (task.owner = :relativeOwnerRef AND doc.space = :ownerSpaceRef)";
            Query query = queryManager.createQuery(statement, Query.XWQL);

            query
                .bindValue("absoluteOwnerRef", serializer.serialize(documentReference))
                .bindValue("compactOwnerRef", compactserializer.serialize(documentReference))
                .bindValue("relativeOwnerRef", documentReference.getName())
                .bindValue("ownerSpaceRef", compactserializer.serialize(documentReference.getLastSpaceReference()));

            List<String> results = query.execute();
            for (String result : results) {
                DocumentReference taskRef = resolver.resolve(result, context.getWikiReference());
                XWikiDocument document = context.getWiki().getDocument(taskRef, context);
                BaseObject taskObject = document.getXObject(TASK_CLASS_REFERENCE);
                if (taskObject == null || !resolver.resolve(taskObject.getLargeStringValue(Task.OWNER), taskRef)
                    .equals(documentReference))
                {
                    continue;
                }
                context.getWiki().deleteDocument(document, context);
            }
        } catch (QueryException | XWikiException e) {
            throw new TaskException(String.format("Failed to delete the task documents that had [%s] as owner.",
                documentReference), e);
        }
    }

    private Task getTaskFromXObject(BaseObject obj)
    {
        Task task = new Task();
        task.setReference(obj.getDocumentReference());
        task.setName(obj.getStringValue(Task.NAME));
        task.setNumber(obj.getIntValue(Task.NUMBER));
        task.setOwner(resolver.resolve(obj.getLargeStringValue(Task.OWNER), obj.getDocumentReference()));
        String assignees = obj.getLargeStringValue(Task.ASSIGNEE);
        task.setAssignees(assignees.trim().isEmpty() ? null
            : Arrays.stream(assignees.split(",")).map(user -> resolver.resolve(user))
            .collect(Collectors.toList()));
        task.setStatus(obj.getStringValue(Task.STATUS));
        task.setReporter(resolver.resolve(obj.getLargeStringValue(Task.REPORTER)));
        task.setDuedate(obj.getDateValue(Task.DUE_DATE));
        task.setCreateDate(obj.getDateValue(Task.CREATE_DATE));
        task.setCompleteDate(obj.getDateValue(Task.COMPLETE_DATE));
        task.setDescription(obj.getLargeStringValue(Task.DESCRIPTION));
        return task;
    }
}
