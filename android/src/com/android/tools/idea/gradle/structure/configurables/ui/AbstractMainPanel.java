/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.ui;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

public abstract class AbstractMainPanel extends JPanel implements Disposable {
  @NotNull private final PsProject myProject;
  @NotNull private final PsContext myContext;

  private boolean myShowModulesDropDown;
  private JComponent myModulesToolbar;

  protected AbstractMainPanel(@NotNull PsProject project, @NotNull PsContext context) {
    this(project, context, Collections.<PsModule>emptyList());
  }

  protected AbstractMainPanel(@NotNull PsProject project, @NotNull PsContext context, @NotNull final List<PsModule> extraTopModules) {
    super(new BorderLayout());
    myProject = project;
    myContext = context;

    PsUISettings settings = PsUISettings.getInstance();
    myShowModulesDropDown = settings.MODULES_LIST_MINIMIZE;
    if (myShowModulesDropDown) {
      createAndAddModulesAction(extraTopModules);
    }
    settings.addListener(new PsUISettings.ChangeListener() {
      @Override
      public void settingsChanged(@NotNull PsUISettings settings) {
        if (settings.MODULES_LIST_MINIMIZE != myShowModulesDropDown) {
          myShowModulesDropDown = settings.MODULES_LIST_MINIMIZE;
          if (myShowModulesDropDown) {
            createAndAddModulesAction(extraTopModules);
          }
          else {
            removeModulesAction();
          }
        }
      }
    }, this);
  }

  private void createAndAddModulesAction(@NotNull List<PsModule> extraTopModules) {
    DefaultActionGroup actions = new DefaultActionGroup();
    actions.add(new ModulesComboBoxAction(myProject, myContext, extraTopModules));

    AnAction restoreModuleListAction = new DumbAwareAction("Restore 'Modules' List", "", AllIcons.Actions.MoveTo2) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        PsUISettings settings = PsUISettings.getInstance();
        settings.MODULES_LIST_MINIMIZE = myShowModulesDropDown = false;
        settings.fireUISettingsChanged();
        removeModulesAction();
      }
    };
    actions.add(restoreModuleListAction);

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("TOP", actions, true);
    myModulesToolbar = toolbar.getComponent();
    myModulesToolbar.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));

    add(myModulesToolbar, BorderLayout.NORTH);
  }

  private void removeModulesAction() {
    if (myModulesToolbar != null) {
      remove(myModulesToolbar);
      myModulesToolbar = null;
      revalidate();
      repaint();
    }
  }

  @NotNull
  protected PsProject getProject() {
    return myProject;
  }

  @NotNull
  protected PsContext getContext() {
    return myContext;
  }
}
