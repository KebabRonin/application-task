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
package org.xwiki.contrib.application.task.test.po;

import java.util.List;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.Select;
import org.xwiki.test.ui.po.InlinePage;
import org.xwiki.test.ui.po.SuggestInputElement;

/**
 * Represents a Task entry page being added (inline mode).
 *
 * @version $Id: 1c600858c8ae7475c179e785904841a65520ab02 $
 * @since 4.3M2
 */
public class TaskManagerInlinePage extends InlinePage
{
    private static final String CLASS_PREFIX = "TaskManager.TaskManagerClass_0_";

    @FindBy(id = CLASS_PREFIX + "name")
    private WebElement nameElement;

    @FindBy(id = CLASS_PREFIX + "project")
    private WebElement projectElement;

    @FindBy(id = CLASS_PREFIX + "creationdate")
    private WebElement creationDateElement;

    @FindBy(id = CLASS_PREFIX + "duedate")
    private WebElement dueDateElement;

    @FindBy(id = CLASS_PREFIX + "startDate")
    private WebElement startDateElement;

    @FindBy(id = CLASS_PREFIX + "severity")
    private WebElement severityElement;

    @FindBy(name = CLASS_PREFIX + "reporter")
    private WebElement reporterElement;

    @FindBy(id = CLASS_PREFIX + "assignee")
    private WebElement assigneeElement;

    @FindBy(id = CLASS_PREFIX + "status")
    private WebElement statusElement;

    @FindBy(id = CLASS_PREFIX + "progress")
    private WebElement progressElement;

    private static final String DEPENDENCIES_ID = "TaskManager.TaskManagerClass_dependencies";

    /**
     * @param name the name of the Task entry
     */
    public void setName(String name)
    {
        this.nameElement.clear();
        this.nameElement.sendKeys(name);
    }

    /**
     * @param project the name of the project for the task entry
     */
    public void setProject(String project)
    {
        Select projectSelect = new Select(this.projectElement);
        projectSelect.selectByValue(project);
    }

    /**
     * @return The creation date of the task (automatically set)
     */
    public String getCreationDate()
    {
        return this.creationDateElement.getAttribute("id");
    }

    /**
     * @param dueDate the due date for the task entry
     */
    public void setDueDate(String dueDate)
    {
        // WebElement#clear does not send the right keyboard events, it's better to use a key combination
        // to replace the actual content of the input.
        this.dueDateElement.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        this.dueDateElement.sendKeys(dueDate);
        this.dueDateElement.sendKeys(Keys.ENTER);
    }

    /**
     * @param startDate the start date for the task entry
     * @since 3.7.2
     */
    public void setStartDate(String startDate)
    {
        // WebElement#clear does not send the right keyboard events, it's better to use a key combination
        // to replace the actual content of the input.
        this.startDateElement.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        this.startDateElement.sendKeys(startDate);
        this.startDateElement.sendKeys(Keys.ENTER);
    }

    public void clearStartDate()
    {
        this.startDateElement.clear();
    }

    /**
     * @param severity the severity/priority for the task entry
     */
    public void setSeverity(String severity)
    {
        Select severitySelect = new Select(this.severityElement);
        severitySelect.selectByValue(severity);
    }

    public String getReporter()
    {
        return this.reporterElement.getAttribute("value");
    }

    /**
     * @param assignee the assignee for the task entry
     */
    public void setAssignee(String assignee)
    {
        if (assignee.isEmpty()) {
            new SuggestInputElement(this.assigneeElement).clearSelectedSuggestions();
        } else {
            new SuggestInputElement(this.assigneeElement).clearSelectedSuggestions().sendKeys(assignee)
                .selectTypedText();
        }
    }

    /**
     * @param status the status for the task entry
     */
    public void setStatus(String status)
    {
        Select statusSelect = new Select(this.statusElement);
        statusSelect.selectByValue(status);
    }

    /**
     * @return the progress of the task entry
     */
    public String getProgress()
    {
        return this.progressElement.getAttribute("value");
    }

    /**
     * @param progress the progress for the task entry
     */
    public void setProgress(String progress)
    {
        this.progressElement.clear();
        this.progressElement.sendKeys(progress);
    }

    /**
     * @param userReference the reference of the user that you want to assign.
     */
    public void appendAssignee(String userReference)
    {
        WebElement input =
            getDriver().findElement(By.cssSelector("#TaskManager\\.TaskManagerClass_0_assignee" + "-selectized"));
        input.sendKeys(userReference);
        input.click();
        WebElement select = getDriver().findElement(By.cssSelector(".selectize-dropdown-content > div"));
        select.click();
    }

    /**
     * @return the name of the Task entry
     */
    public List<String> getDependencies()
    {
        List<WebElement> dependenciesElements = getUtil().getDriver()
            .findElements(By.cssSelector("input[name='TaskManager.TaskManagerClass_0_dependencies']:not([value=''])"));
        return dependenciesElements.stream().map(element -> assigneeElement.getAttribute("value"))
            .collect(Collectors.toList());
    }

    /**
     * @param dependencyPage the page reference of the task to depend on
     */
    public void setDependency(String dependencyPage)
    {
        List<WebElement> dependency = getUtil().getDriver().findElements(
                By.cssSelector("input[name='TaskManager.TaskManagerClass_0_dependencies'][value='" + dependencyPage + "']"));
        if (!dependency.isEmpty()) {
            dependency.get(0).click();
        }
    }
}
