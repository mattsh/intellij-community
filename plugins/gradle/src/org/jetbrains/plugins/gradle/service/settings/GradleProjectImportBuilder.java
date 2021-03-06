/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.service.settings;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.JavaProjectData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.manage.*;
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportBuilder;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import icons.GradleIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import java.io.File;

/**
 * @author Denis Zhdanov
 * @since 4/15/13 2:29 PM
 */
public class GradleProjectImportBuilder extends AbstractExternalProjectImportBuilder<GradleConfigurable> {

  public GradleProjectImportBuilder(@NotNull ExternalSystemSettingsManager settingsManager, @NotNull ProjectDataManager dataManager) {
    super(settingsManager, dataManager, new GradleConfigurable(null), GradleConstants.SYSTEM_ID);
  }

  @NotNull
  @Override
  public String getName() {
    return GradleBundle.message("gradle.name");
  }

  @Override
  public Icon getIcon() {
    return GradleIcons.Gradle;
  }

  @Override
  protected void doPrepare(@NotNull WizardContext context) {
    String pathToUse = context.getProjectFileDirectory();
    if (!pathToUse.endsWith(GradleConstants.DEFAULT_SCRIPT_NAME)) {
      pathToUse = new File(pathToUse, GradleConstants.DEFAULT_SCRIPT_NAME).getAbsolutePath();
    }
    getConfigurable().setLinkedExternalProjectPath(pathToUse);
  }

  @Override
  protected void beforeCommit(@NotNull DataNode<ProjectData> dataNode, @NotNull Project project) {
    DataNode<JavaProjectData> javaProjectNode = ExternalSystemUtil.find(dataNode, ProjectKeys.JAVA_PROJECT);
    if (javaProjectNode == null) {
      return;
    }

    final LanguageLevel externalLanguageLevel = javaProjectNode.getData().getLanguageLevel();
    final LanguageLevelProjectExtension languageLevelExtension = LanguageLevelProjectExtension.getInstance(project);
    if (externalLanguageLevel != languageLevelExtension.getLanguageLevel()) {
      languageLevelExtension.setLanguageLevel(externalLanguageLevel);
    }
  }

  @Override
  protected void applyExtraSettings(@NotNull WizardContext context) {
    DataNode<ProjectData> node = getExternalProjectNode();
    if (node == null) {
      return;
    }

    DataNode<JavaProjectData> javaProjectNode = ExternalSystemUtil.find(node, ProjectKeys.JAVA_PROJECT);
    if (javaProjectNode != null) {
      JavaProjectData data = javaProjectNode.getData();
      context.setCompilerOutputDirectory(data.getCompileOutputPath());
      JavaSdkVersion version = data.getJdkVersion();
      Sdk jdk = ExternalSystemUtil.findJdk(version);
      if (jdk != null) {
        context.setProjectJdk(jdk);
      }
    }
  }

  @Override
  protected void onProjectInit(@NotNull Project project) {
    GradleSettings settings = (GradleSettings)getSettingsManager().getSettings(project, GradleConstants.SYSTEM_ID);
    settings.setPreferLocalInstallationToWrapper(getConfigurable().isPreferLocalInstallationToWrapper());
    settings.setGradleHome(getConfigurable().getGradleHomePath());

    // Reset linked gradle home for default project (legacy bug).
    Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    getSettingsManager().getSettings(defaultProject, GradleConstants.SYSTEM_ID).setLinkedExternalProjectPath(null);
  }

  @Override
  protected File getExternalProjectConfigToUse(@NotNull File file) {
    if (file.isDirectory()) {
      File candidate = new File(file, GradleConstants.DEFAULT_SCRIPT_NAME);
      if (candidate.isFile()) {
        return candidate;
      }
    }
    return file;
  }
}
