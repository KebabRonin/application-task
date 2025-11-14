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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.xwiki.bridge.event.DocumentCreatingEvent;
import org.xwiki.bridge.event.DocumentDeletingEvent;
import org.xwiki.bridge.event.DocumentUpdatingEvent;
import org.xwiki.component.annotation.Component;
import org.xwiki.job.Job;
import org.xwiki.job.JobGroupPath;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.observation.event.Event;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.task.TaskCounter;
import com.xwiki.task.TaskException;
import com.xwiki.task.model.Task;

/**
 * Listener that will modify the task macro associated with the updated task object.
 *
 * @version $Id$
 * @since 3.0
 */
@Component
@Named("TaskObjectUpdateEventListener")
@Singleton
public class TaskObjectUpdateEventListener extends AbstractTaskEventListener
{
    private static final List<String> TEMPLATE_SPACE_REFERENCES = List.of("TaskManager", "TaskManagerTemplates");

    @Inject
    private TaskCounter taskCounter;

    @Inject
    @Named("document")
    private UserReferenceResolver<DocumentReference> userRefResolver;

    /**
     * Constructor.
     */
    public TaskObjectUpdateEventListener()
    {
        super(TaskObjectUpdateEventListener.class.getName(), Arrays.asList(new DocumentUpdatingEvent(),
            new DocumentCreatingEvent(), new DocumentDeletingEvent()));
    }

    @Override
    protected void processEvent(XWikiDocument document, XWikiContext context, Event event, boolean inFoldEvent)
    {
        // Handle delete event before checking for the existence of task object because it does not exist on
        // the given document when it is being deleted and need to call XWiki#getDocument(DocumentReference).
        if (handleDeleteEvent(document, context, event)) {
            return;
        }

        BaseObject taskObj = document.getXObject(TASK_CLASS_REFERENCE);
        if (taskObj == null) {
            return;
        }

        if (document.getDocumentReference().getSpaceReferences().stream().map(SpaceReference::getName)
            .collect(Collectors.toList()).equals(TEMPLATE_SPACE_REFERENCES)) {
            return;
        }

        maybeSetTaskNumber(context, taskObj);

        // If the flag is set, the listener was triggered as a result of a save made by TaskMacroUpdateEventListener
        // which updated some task objects. Skip the execution.
        if (context.get(TASK_UPDATE_FLAG) != null) {
            return;
        }

        if (taskObj.getStringValue(Task.OWNER).isEmpty()) {
            return;
        }

        String taskOwner = taskObj.getStringValue(Task.OWNER);

        DocumentReference taskOwnerRef = resolver.resolve(taskOwner, document.getDocumentReference());

        try {
            context.put(TASK_UPDATE_FLAG, true);
            if (!taskOwner.isEmpty()) {
                XWikiDocument ownerDocument = context.getWiki().getDocument(taskOwnerRef, context).clone();
                if (!ownerDocument.isNew()) {
                    ownerDocument.setContent(
                        taskXDOMProcessor.updateTaskMacroCall(taskOwnerRef, taskObj, ownerDocument.getXDOM(),
                            ownerDocument.getSyntax()));
                    UserReference currentUserReference = userRefResolver.resolve(context.getUserReference());
                    ownerDocument.getAuthors().setOriginalMetadataAuthor(currentUserReference);
                    context.getWiki().saveDocument(ownerDocument,
                        String.format("Task [%s] has been updated!", taskObj.getDocumentReference()), context);
                }
            }
        } catch (XWikiException e) {
            logger.warn("Failed to process the owner document of the task [{}]: [{}].", taskOwnerRef,
                ExceptionUtils.getRootCauseMessage(e));
        } finally {
            context.put(TASK_UPDATE_FLAG, null);
        }
    }

    private boolean handleDeleteEvent(XWikiDocument document, XWikiContext context, Event event)
    {
        if (event instanceof DocumentDeletingEvent) {
            if (context.get(TASK_UPDATE_FLAG) != null) {
                return true;
            }
            try {
                // We can't use the doc provided to the listener because it does not contain the xwiki objects.
                XWikiDocument actualDoc = context.getWiki().getDocument(document.getDocumentReference(), context);
                BaseObject object = actualDoc.getXObject(TASK_CLASS_REFERENCE);
                if (object == null || object.getStringValue(Task.OWNER).isEmpty()) {
                    return true;
                }
                DocumentReference ownerDocumentReference = resolver.resolve(object.getStringValue(Task.OWNER),
                    document.getDocumentReference());
                // If we are inside a refactoring job that also deletes the owner, no need to update the macro call.
                Job deletingJob = getDeletingJob(document);
                if (deletingJob != null && deletingJob.getRequest()
                    .getProperty("entityReferences", Collections.emptyList()).stream().anyMatch(
                        e -> e instanceof EntityReference && isParentOrEqual((EntityReference) e,
                            ownerDocumentReference)))
                {
                    return true;
                }

                context.put(TASK_UPDATE_FLAG, true);
                XWikiDocument ownerDocument = context.getWiki().getDocument(ownerDocumentReference, context);
                ownerDocument.setContent(taskXDOMProcessor.removeTaskMacroCall(document.getDocumentReference(),
                    ownerDocument.getDocumentReference(), ownerDocument.getXDOM(), ownerDocument.getSyntax()));
                context.getWiki().saveDocument(ownerDocument,
                    String.format("Removed the task with the reference of [%s]", document.getDocumentReference()),
                    context);
            } catch (XWikiException e) {
                logger.warn("Failed to remove the macro call from the owner document of the task [{}]: [{}].",
                    document.getDocumentReference(), ExceptionUtils.getRootCauseMessage(e));
            } finally {
                context.put(TASK_UPDATE_FLAG, null);
            }
            return true;
        }
        return false;
    }

    private Job getDeletingJob(XWikiDocument document)
    {
        List<String> deleteJobGroup = new ArrayList<>();
        deleteJobGroup.add("refactoring");
        for (EntityReference entityReference : document.getDocumentReference().getLastSpaceReference()
            .getReversedReferenceChain()) {
            deleteJobGroup.add(entityReference.getName());
            Job possibleJob = executor.getCurrentJob(new JobGroupPath(deleteJobGroup));
            if (possibleJob != null) {
                return possibleJob;
            }
        }
        return null;
    }

    private boolean isParentOrEqual(EntityReference entity1, EntityReference entity2)
    {
        return entity1.equals(entity2) || entity2.hasParent(entity1);
    }

    private void maybeSetTaskNumber(XWikiContext context, BaseObject taskObj)
    {
        if (taskObj.getIntValue(Task.NUMBER, -1) == -1) {
            try {
                taskObj.set(Task.NUMBER, taskCounter.getNextNumber(), context);
            } catch (TaskException e) {
                logger.warn("Failed to set a number to the task [{}]. Reason: [{}].", taskObj.getDocumentReference(),
                    ExceptionUtils.getRootCauseMessage(e));
            }
        }
    }
}
