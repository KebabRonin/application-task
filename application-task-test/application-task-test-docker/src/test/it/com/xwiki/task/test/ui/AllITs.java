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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.ClassOrderer;
import org.xwiki.test.docker.junit5.UITest;

/**
 * All UI tests for the Task Manager application.
 *
 * @version $Id$
 * @since 2.6
 */
@UITest(properties = {"xwikiCfgPlugins=com.xpn.xwiki.plugin.jodatime.JodaTimePlugin"}, extraJARs = {
    "org.xwiki.platform:xwiki-platform-jodatime:14.10"}, resolveExtraJARs = true)
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
class AllITs
{
    @Nested
    @Order(1)
    @DisplayName("Overall Task Manager UI")
    class NestedTaskManagerIT extends TaskManagerIT
    {
    }

    @Nested
    @Order(2)
    @DisplayName("Gantt Task Manager UI")
    class NestedGanttIT extends GanttIT
    {
    }

    @Nested
    @Order(3)
    @DisplayName("Task Manager Notifications")
    class NestedNotificationIT extends NotificationIT
    {
    }

    @Nested
    @Order(4)
    @DisplayName("Task Manager Template Creation")
    class NestedTaskTemplatesIT extends TaskTemplatesIT
    {
    }

}
