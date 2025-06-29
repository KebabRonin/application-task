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

package com.xwiki.task.internal.macro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.MetaDataBlock;
import org.xwiki.rendering.block.RawBlock;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.macro.descriptor.DefaultContentDescriptor;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.skinx.SkinExtension;
import org.xwiki.xml.XMLUtils;

import com.xwiki.task.MacroUtils;
import com.xwiki.task.TaskConfiguration;
import com.xwiki.task.TaskException;
import com.xwiki.task.TaskManager;
import com.xwiki.task.internal.TaskBlockProcessor;
import com.xwiki.task.internal.TaskReferenceUtils;
import com.xwiki.task.macro.TaskMacroParameters;
import com.xwiki.task.model.Task;

/**
 * Task macro that will facilitate the creation of content-stored tasks inside a xwiki document. Upon saving the
 * document, a task page will be created. The macro and the page will be synced.
 *
 * @version $Id$
 * @since 3.0
 */
@Component
@Named("task")
@Singleton
public class TaskMacro extends AbstractMacro<TaskMacroParameters>
{
    /**
     * The reference to the document that contains the necessary CSS for TaskManager macros.
     */
    public static final String SKIN_RESOURCES_DOCUMENT_REFERENCE = "TaskManager.SkinExtensions";

    private static final String HTML_CLASS = "class";

    @Inject
    private TaskBlockProcessor taskBlockProcessor;

    @Inject
    @Named("ssx")
    private SkinExtension ssx;

    @Inject
    @Named("jsx")
    private SkinExtension jsx;

    @Inject
    private TaskManager taskManager;

    @Inject
    private MacroUtils macroUtils;

    @Inject
    @Named("macro")
    private DocumentReferenceResolver<String> macroDocumentReferenceResolver;

    @Inject
    private TaskReferenceUtils taskReferenceUtils;

    @Inject
    private TaskConfiguration taskConfiguration;

    /**
     * Default constructor.
     */
    public TaskMacro()
    {
        super("name", "description", new DefaultContentDescriptor("Content of the task.", false, Block.LIST_BLOCK_TYPE),
            TaskMacroParameters.class);
        setDefaultCategories(Collections.singleton(DEFAULT_CATEGORY_CONTENT));
    }

    @Override
    public boolean supportsInlineMode()
    {
        return false;
    }

    @Override
    public List<Block> execute(TaskMacroParameters parameters, String content, MacroTransformationContext context)
        throws MacroExecutionException
    {
        this.ssx.use(SKIN_RESOURCES_DOCUMENT_REFERENCE);
        this.jsx.use(SKIN_RESOURCES_DOCUMENT_REFERENCE);

        List<Block> contentBlocks = new ArrayList<>();

        if (content != null && !content.trim().isEmpty()) {
            List<Block> macroContent =
                this.macroUtils.getMacroContentXDOM(context.getCurrentMacroBlock(), context.getSyntax()).getChildren();

            contentBlocks =
                Collections.singletonList(new MetaDataBlock(macroContent, this.getNonGeneratedContentMetaData()));
        }

        return createTaskStructure(parameters, context, contentBlocks);
    }

    private List<Block> createTaskStructure(TaskMacroParameters parameters, MacroTransformationContext context,
        List<Block> contentBlocks)
    {
        Block ret = new GroupBlock();
        Map<String, String> blockParameters = new HashMap<>();

        String className = "task-macro";
        className = parameters.getClassName() == null || parameters.getClassName().isEmpty() ? className
            : XMLUtils.escape(String.join(" ", className, parameters.getClassName()));
        blockParameters.put(HTML_CLASS, className);
        ret.setParameters(blockParameters);
        String checked = "";
        if ((parameters.getStatus() != null && parameters.getStatus().equals(Task.STATUS_DONE))) {
            checked = "checked";
        }
        // We need to serialize the reference as DocumentReference because the javascript api doesn't handle
        // PageReference resolving.
        EntityReference ownerDocRef = getOwnerDocument(context.getCurrentMacroBlock());
        EntityReference taskRef = taskReferenceUtils.resolve(parameters.getReference(), ownerDocRef);
        String taskId = taskReferenceUtils.serializeAsDocumentReference(taskRef, ownerDocRef);
        String htmlCheckbox =
            String.format("<input type=\"checkbox\" data-taskId=\"%s\" data-rawid=\"%s\" %s class=\"task-status\">",
                XMLUtils.escape(taskId), XMLUtils.escape(parameters.getReference()), checked);
        Block checkBoxBlock = new RawBlock(htmlCheckbox, Syntax.HTML_5_0);

        Block taskInfoBlock = new GroupBlock(Collections.singletonMap(HTML_CLASS, "task-info"));

        taskInfoBlock.addChild(new FormatBlock(Collections.singletonList(checkBoxBlock), Format.NONE));

        if (TaskMacroParameters.IdDisplay.TRUE.equals(parameters.isIdDisplayed())
            || (parameters.isIdDisplayed() == null && taskConfiguration.isIdDisplayed()))
        {
            try {
                Task task = taskManager.getTask(taskRef);
                taskInfoBlock.addChild(taskBlockProcessor.createTaskLinkBlock(taskId, task.getNumber()));
            } catch (TaskException ignored) {
                // The task page not existing is a valid scenario (when the user just added the task macro in the
                // WYSIWYG).
            }
        }
        ret.addChild(taskInfoBlock);
        ret.addChild(new GroupBlock(contentBlocks, Collections.singletonMap(HTML_CLASS, "task-content")));
        return Collections.singletonList(ret);
    }

    private EntityReference getOwnerDocument(MacroBlock block)
    {
        return this.macroDocumentReferenceResolver.resolve("", block);
    }
}
