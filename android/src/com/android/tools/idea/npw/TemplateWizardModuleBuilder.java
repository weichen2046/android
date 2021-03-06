/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.npw;

import com.android.tools.idea.npw.deprecated.ImportWizardModuleBuilder;
import com.android.tools.idea.npw.deprecated.WrapArchiveWizardPath;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.io.File;
import java.text.Collator;
import java.util.*;

import static com.android.tools.idea.templates.Template.CATEGORY_PROJECTS;

/*
 * Builder that also supports creating Android modules
 */
public class TemplateWizardModuleBuilder extends ImportWizardModuleBuilder {
  protected static final String MODULE_NAME = "Android Module";
  protected static final String APP_TEMPLATE_NAME = "Android Application";
  protected static final String LIB_TEMPLATE_NAME = "Android Library";

  public TemplateWizardModuleBuilder(@Nullable File templateLocation,
                                     @Nullable Project project,
                                     @Nullable Icon sidePanelIcon,
                                     @NotNull List<ModuleWizardStep> steps,
                                     @NotNull Disposable disposable,
                                     boolean inGlobalWizard) {
    super(templateLocation, project, sidePanelIcon, steps, disposable, inGlobalWizard);
    if (!inGlobalWizard) {
      mySteps.add(0, buildChooseModuleStep(project));
    }
  }

  @Override
  protected Iterable<WizardPath> setupWizardPaths(Project project, Icon sidePanelIcon, Disposable disposable) {
    List<WizardPath> paths = Lists.newArrayList(super.setupWizardPaths(project, sidePanelIcon, disposable));
    paths.add(new WrapArchiveWizardPath(myWizardState, project, this, sidePanelIcon));
    return paths;
  }

  /**
   * Create a template chooser step populated with the correct templates for the new modules.
   */
  private ChooseTemplateStep buildChooseModuleStep(@Nullable Project project) {
    // We're going to build up our own list of templates here
    // This is a little hacky, we should clean this up later.
    ChooseTemplateStep chooseModuleStep =
      new ChooseTemplateStep(myWizardState, null, project, null, AndroidIcons.Wizards.NewModuleSidePanel,
                             this, this);

    Set<String> excludedTemplates = Sets.newHashSet();
    Set<ChooseTemplateStep.MetadataListItem> builtinTemplateList =
      new TreeSet<>(new Comparator<ChooseTemplateStep.MetadataListItem>() {
        @Override
        public int compare(ChooseTemplateStep.MetadataListItem o1, ChooseTemplateStep.MetadataListItem o2) {
          return Collator.getInstance().compare(o1.toString(), o2.toString());
        }
      });
    for (WizardPath path : myPaths) {
      excludedTemplates.addAll(path.getExcludedTemplates());

      Collection<ChooseTemplateStep.MetadataListItem> templates = path.getBuiltInTemplates();
      builtinTemplateList.addAll(templates);
      for (ChooseTemplateStep.MetadataListItem template : templates) {
        myWizardState.associateTemplateWithPath(template.toString(), path);
      }
    }
    // Get the list of templates to offer, but exclude the NewModule and NewProject template
    List<ChooseTemplateStep.MetadataListItem> templateList =
      ChooseTemplateStep.getTemplateList(myWizardState, CATEGORY_PROJECTS, excludedTemplates);

    List<ChooseTemplateStep.MetadataListItem> list = Lists.newArrayListWithExpectedSize(builtinTemplateList.size() + templateList.size());
    list.addAll(builtinTemplateList);
    list.addAll(templateList);
    chooseModuleStep.setListData(list);
    return chooseModuleStep;
  }

  // Temporary hack for tests while refactoring is in progress
  @TestOnly
  protected NewModuleWizardState getWizardState() {
    return myWizardState;
  }

  // Temporary hack for tests while refactoring is in progress
  @TestOnly
  protected boolean getInitializationComplete() {
    return myInitializationComplete;
  }
}
