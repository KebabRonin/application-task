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
package com.xwiki.task.test.ui;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.xwiki.contrib.application.task.test.po.TaskManagerHomePage;
import org.xwiki.contrib.application.task.test.po.TaskManagerInlinePage;
import org.xwiki.contrib.application.task.test.po.TaskTemplateListPage;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.CreatePagePage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the Task Manager Template creation.
 *
 * @version $Id$
 * @since 4.0.0
 */
@UITest
public class TaskTemplatesIT
{
    private static final String TEST_USERNAME = "TaskTemplatesTestUser";

    private static final String TEST_PASSWORD = "pass";

    @BeforeAll
    void setup(TestUtils setup)
    {
        setup.createUserAndLogin(TEST_USERNAME, TEST_PASSWORD);
        setup.loginAsSuperAdmin();
        setup.setGlobalRights(null, "XWiki." + TEST_USERNAME, "edit", false);
    }

    @Test
    @Order(1)
    void noEditRights(TestUtils setup)
    {
        setup.login(TEST_USERNAME, TEST_PASSWORD);
        setup.gotoPage("TaskManager", "WebHome");
        setup.getDriver().findElement(By.id("task-manager-view-task-list")).click();
        assertEquals(setup.getDriver().getCurrentUrl(), TaskTemplateListPage.getURL());
        TaskTemplateListPage page = new TaskTemplateListPage();
        page.setTemplateName("TestNoEdit");
        page.submit();
        assertNotNull(setup.getDriver().findElement(By.className("xnotification-error")));
        page = TaskTemplateListPage.gotoPage();
        assertFalse(page.getListedTemplates().contains("TaskManager.TaskManagerTemplates.TestNoEditTemplateProvider"),
            "Expected " + page.getListedTemplates());
    }

    @Test
    @Order(2)
    void createTemplate(TestUtils setup)
    {
        String testTemplate = "Test";
        String templateName = "TaskManager.TaskManagerTemplates." + testTemplate + "Template";
        String testProperty = "15";
        setup.createUserAndLogin("TestUser", "password");

        TaskTemplateListPage page = TaskTemplateListPage.gotoPage();
        assertFalse(page.getListedTemplates().contains(templateName + "Provider"));
        page.setTemplateName(testTemplate);
        setup.getDriver().addPageNotYetReloadedMarker();
        page.submit();
        assertNotNull(setup.getDriver().findElement(By.className("xnotification-done")));
        setup.getDriver().waitUntilPageIsReloaded();
        // See that the user is redirected to the template page, ignoring the query string, which contains some tokens.
        assertEquals(getTemplateSpaceUrl("edit", testTemplate + "Template", setup), setup.getDriver().getCurrentUrl());

        TaskManagerInlinePage taskEditPage = new TaskManagerInlinePage();
        taskEditPage.setProgress(testProperty);
        taskEditPage.clickSaveAndView();
        createPageFromTemplate("TestTaskPage", templateName + "Provider");
        taskEditPage = new TaskManagerInlinePage();
        // See that the right template is applied.
        assertEquals(testProperty, taskEditPage.getProgress());
        // Also test that the templates don't show up as dependencies.
        assertFalse(taskEditPage.getDependencies().contains(templateName));

        // Check that the new template is listed.
        page = TaskTemplateListPage.gotoPage();
        assertTrue(
            page.getListedTemplates().contains(getTemplateSpaceUrl("view", testTemplate + "TemplateProvider", setup)),
            "Listed templates: " + page.getListedTemplates());

        logout(setup);
    }

    private String getTemplateSpaceUrl(String action, String templateName, TestUtils setup)
    {
        return setup.getURL(List.of("TaskManager", "TaskManagerTemplates"), templateName, action, "").split("\\?")[0];
    }

    private void createPageFromTemplate(String title, String template)
    {
        TaskManagerHomePage taskManagerHomePage = TaskManagerHomePage.gotoPage();
        CreatePagePage createPage = taskManagerHomePage.createPage();

        createPage.getDocumentPicker().setTitle(title);
        createPage.setTemplate(template);
        createPage.clickCreate();
    }

    private void logout(TestUtils setup)
    {
        setup.setSession(null);
        setup.getDriver().navigate().refresh();
    }
}
