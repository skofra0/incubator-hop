/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.projects.xp;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.extension.ExtensionPoint;
import org.apache.hop.core.extension.IExtensionPoint;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.projects.config.ProjectsConfigSingleton;
import org.apache.hop.projects.project.ProjectConfig;

@ExtensionPoint(id = "ProjectHomeExtensionPoint", description = "Calculates the home folder for a given project name", extensionPointId = "ProjectHome")
public class ProjectHomeExtensionPoint implements IExtensionPoint<Object[]> {
  @Override
  public void callExtensionPoint(ILogChannel log, IVariables variables, Object[] objects) throws HopException {
    String projectName = variables.resolve((String) objects[0]);
    ProjectConfig projectConfig = ProjectsConfigSingleton.getConfig().findProjectConfig(projectName);
    if (projectConfig == null) {
      throw new HopException("Unable to find the project configuration for '" + projectName + "'");
    }
    objects[1] = variables.resolve(projectConfig.getProjectHome());
  }
}
