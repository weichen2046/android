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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater;
import com.android.tools.idea.gradle.project.build.GradleBuildContext;
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder;
import com.android.tools.idea.gradle.project.build.PostProjectBuildTasksExecutor;
import com.android.tools.idea.gradle.project.build.compiler.AndroidGradleBuildConfiguration;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.project.AndroidProjectBuildNotifications;
import com.android.tools.idea.testing.Modules;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.AvdManagerDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.GradleBuildModelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.GradleProjectEventListener;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.GradleToolWindowFixture;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.util.PathUtil;
import com.intellij.util.ThreeState;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.timing.Wait;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.android.SdkConstants.FD_GRADLE;
import static com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.registerSyncErrorToSimulate;
import static com.android.tools.idea.gradle.util.BuildMode.COMPILE_JAVA;
import static com.android.tools.idea.gradle.util.BuildMode.SOURCE_GEN;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.vfs.VfsUtilCore.urlToPath;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.fail;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.fest.util.Strings.quote;
import static org.jetbrains.android.AndroidPlugin.EXECUTE_BEFORE_PROJECT_BUILD_IN_GUI_TEST_KEY;
import static org.jetbrains.plugins.gradle.settings.DistributionType.LOCAL;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IdeFrameFixture extends ComponentFixture<IdeFrameFixture, IdeFrameImpl> {
  @NotNull private final File myProjectPath;
  @NotNull private final GradleProjectEventListener myGradleProjectEventListener;
  @NotNull private final Modules myModules;

  private EditorFixture myEditor;
  private boolean myIsClosed;

  @NotNull
  public static IdeFrameFixture find(@NotNull final Robot robot, @NotNull final File projectPath, @Nullable final String projectName) {
    final GenericTypeMatcher<IdeFrameImpl> matcher = new GenericTypeMatcher<IdeFrameImpl>(IdeFrameImpl.class) {
      @Override
      protected boolean isMatching(@NotNull IdeFrameImpl frame) {
        Project project = frame.getProject();
        if (project != null && toSystemIndependentName(projectPath.getPath()).equals(project.getBasePath())) {
          return projectName == null || projectName.equals(project.getName());
        }
        return false;
      }
    };

    return new IdeFrameFixture(robot, GuiTests.waitUntilShowing(robot, matcher), projectPath);
  }

  public IdeFrameFixture(@NotNull Robot robot, @NotNull IdeFrameImpl target, @NotNull File projectPath) {
    super(IdeFrameFixture.class, robot, target);
    myProjectPath = projectPath;
    Project project = getProject();
    myModules = new Modules(project);

    Disposable disposable = new NoOpDisposable();
    Disposer.register(project, disposable);

    myGradleProjectEventListener = new GradleProjectEventListener();

    GradleSyncState.subscribe(project, myGradleProjectEventListener);
    PostProjectBuildTasksExecutor.subscribe(project, myGradleProjectEventListener);
  }

  @NotNull
  public File getProjectPath() {
    return myProjectPath;
  }

  @NotNull
  public List<String> getModuleNames() {
    List<String> names = Lists.newArrayList();
    for (Module module : getModuleManager().getModules()) {
      names.add(module.getName());
    }
    return names;
  }

  @NotNull
  public IdeFrameFixture requireModuleCount(int expected) {
    Module[] modules = getModuleManager().getModules();
    assertThat(modules).named("Module count in project " + quote(getProject().getName())).asList().hasSize(expected);
    return this;
  }

  @NotNull
  public AndroidModuleModel getAndroidProjectForModule(@NotNull String name) {
    Module module = getModule(name);
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null && facet.requiresAndroidModel()) {
      // TODO: Resolve direct AndroidGradleModel dep (b/22596984)
      AndroidModuleModel androidModel = AndroidModuleModel.get(facet);
      if (androidModel != null) {
        return androidModel;
      }
    }
    throw new AssertionError("Unable to find AndroidGradleModel for module " + quote(name));
  }

  /**
   * Returns a list of system independent paths
   */
  @NotNull
  public Collection<String> getSourceFolderRelativePaths(@NotNull String moduleName, @NotNull JpsModuleSourceRootType<?> sourceType) {
    Set<String> paths = Sets.newHashSet();

    Module module = getModule(moduleName);
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);

    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();
        try {
          for (ContentEntry contentEntry : rootModel.getContentEntries()) {
            for (SourceFolder folder : contentEntry.getSourceFolders()) {
              JpsModuleSourceRootType<?> rootType = folder.getRootType();
              if (rootType.equals(sourceType)) {
                String path = urlToPath(folder.getUrl());
                String relativePath = getRelativePath(myProjectPath, new File(toSystemDependentName(path)));
                paths.add(PathUtil.toSystemIndependentName(relativePath));
              }
            }
          }
        }
        finally {
          rootModel.dispose();
        }
      }
    });

    return paths;
  }

  @NotNull
  public Module getModule(@NotNull String name) {
    return myModules.getModule(name);
  }

  public boolean hasModule(@NotNull String name) {
    return myModules.hasModule(name);
  }

  @NotNull
  private ModuleManager getModuleManager() {
    return ModuleManager.getInstance(getProject());
  }

  @NotNull
  public EditorFixture getEditor() {
    if (myEditor == null) {
      myEditor = new EditorFixture(robot(), this);
    }

    return myEditor;
  }

  @NotNull
  public AndroidModuleModel getAndroidModel(@NotNull String moduleName) {
    Module module = getModule(moduleName);
    assertNotNull("Could not find module " + moduleName, module);
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assertNotNull("Module " + moduleName + " is not an Android module", facet);
    assertTrue("Module " + moduleName + " is not a Gradle project", facet.requiresAndroidModel());
    // TODO: Resolve direct AndroidGradleModel dep (b/22596984)
    AndroidModuleModel androidModel = AndroidModuleModel.get(facet);
    assertNotNull("Module " + moduleName + " does not have a Gradle project (not synced yet or sync failed?)", androidModel);

    return androidModel;
  }

  @NotNull
  public GradleInvocationResult invokeProjectMake() {
    myGradleProjectEventListener.reset();

    AtomicReference<GradleInvocationResult> resultRef = new AtomicReference<>();
    AndroidProjectBuildNotifications.subscribe(
      getProject(), context -> {
        if (context instanceof GradleBuildContext) {
          resultRef.set(((GradleBuildContext)context).getBuildResult());
        }
      });
    selectProjectMakeAction();

    waitForBuildToFinish(COMPILE_JAVA);

    return resultRef.get();
  }

  @NotNull
  public IdeFrameFixture invokeProjectMakeAndSimulateFailure(@NotNull String failure) {
    Runnable failTask = () -> {
      throw new ExternalSystemException(failure);
    };
    ApplicationManager.getApplication().putUserData(EXECUTE_BEFORE_PROJECT_BUILD_IN_GUI_TEST_KEY, failTask);
    selectProjectMakeAction();
    return this;
  }

  @NotNull
  public CompileContext invokeProjectMakeUsingJps() {
    Project project = getProject();
    AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
    buildConfiguration.USE_EXPERIMENTAL_FASTER_BUILD = false;

    AtomicReference<CompileContext> contextRef = new AtomicReference<>();
    CompilerManager compilerManager = CompilerManager.getInstance(project);

    Disposable disposable = new NoOpDisposable();
    compilerManager.addCompilationStatusListener(new CompilationStatusListener() {
      @Override
      public void compilationFinished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
        contextRef.set(compileContext);
      }
    }, disposable);

    try {
      selectProjectMakeAction();

      Wait.seconds(10).expecting("Build (" + COMPILE_JAVA + ") for project " + quote(project.getName()) + " to finish'")
        .until(() -> contextRef.get() != null);

      return contextRef.get();
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  /**
   * Finds the Run button in the IDE interface.
   *
   * @return ActionButtonFixture for the run button.
   */
  @NotNull
  public ActionButtonFixture findRunApplicationButton() {
    return findActionButtonByActionId("Run");
  }

  public DeployTargetPickerDialogFixture debugApp(@NotNull String appName) throws ClassNotFoundException {
    selectApp(appName);
    findActionButtonByActionId("Debug").click();
    return DeployTargetPickerDialogFixture.find(robot());
  }

  public DeployTargetPickerDialogFixture runApp(@NotNull String appName) throws ClassNotFoundException {
    selectApp(appName);
    findActionButtonByActionId("Run").click();
    return DeployTargetPickerDialogFixture.find(robot());
  }

  /** Similar to runApp except doesn't wait for a DeployTargetPickerDialog after. */
  @NotNull
  public IdeFrameFixture runNonAndroidApp(@NotNull String appName) {
    selectApp(appName);
    findActionButtonByActionId("Run").click();
    return this;
  }

  @NotNull
  public RunToolWindowFixture getRunToolWindow() {
    return new RunToolWindowFixture(this);
  }

  @NotNull
  public DebugToolWindowFixture getDebugToolWindow() {
    return new DebugToolWindowFixture(this);
  }

  protected void selectProjectMakeAction() {
    invokeMenuPath("Build", "Make Project");
  }

  /** Selects the item at {@code menuPath} and returns the result of {@code fixtureFunction} applied to this {@link IdeFrameFixture}. */
  public <T> T openFromMenu(Function<IdeFrameFixture, T> fixtureFunction, @NotNull String... menuPath) {
    getMenuFixture().invokeMenuPath(menuPath);
    return fixtureFunction.apply(this);
  }

  /**
   * Invokes an action by menu path
   *
   * @param path the series of menu names, e.g. {@link invokeActionByMenuPath("Build", "Make Project")}
   */
  public IdeFrameFixture invokeMenuPath(@NotNull String... path) {
    getMenuFixture().invokeMenuPath(path);
    return this;
  }

  /**
   * Returns whether a menu path is enabled
   *
   * @param path the series of menu names, e.g. {@link isMenuPathEnabled("Build", "Make Project ")}
   */
  public boolean isMenuPathEnabled(@NotNull String... path) {
    return getMenuFixture().isMenuPathEnabled(path);
  }

  @NotNull
  private MenuFixture getMenuFixture() {
    return new MenuFixture(robot(), target());
  }

  @NotNull
  public IdeFrameFixture waitForBuildToFinish(@NotNull BuildMode buildMode) {
    Project project = getProject();
    if (buildMode == SOURCE_GEN && !GradleProjectBuilder.getInstance(project).isSourceGenerationEnabled()) {
      return this;
    }

    // http://b.android.com/226797 - Most builds finish in 10 seconds, but GradleBuildTest.compileWithJack was failing.
    Wait.seconds(20).expecting("Build (" + buildMode + ") for project " + quote(project.getName()) + " to finish'")
      .until(() -> {
        if (buildMode == SOURCE_GEN) {
          PostProjectBuildTasksExecutor tasksExecutor = PostProjectBuildTasksExecutor.getInstance(project);
          if (tasksExecutor.getLastBuildTimestamp() != null) {
            // This will happen when creating a new project. Source generation happens before the IDE frame is found and build listeners
            // are created. It is fairly safe to assume that source generation happened if we have a timestamp for a "last performed build".
            return true;
          }
        }
        return myGradleProjectEventListener.isBuildFinished(buildMode);
      });

    GuiTests.waitForBackgroundTasks(robot());
    robot().waitForIdle();

    return this;
  }

  @NotNull
  public FileFixture findExistingFileByRelativePath(@NotNull String relativePath) {
    VirtualFile file = findFileByRelativePath(relativePath, true);
    return new FileFixture(getProject(), file);
  }

  @Nullable
  @Contract("_, true -> !null")
  public VirtualFile findFileByRelativePath(@NotNull String relativePath, boolean requireExists) {
    //noinspection Contract
    assertFalse("Should use '/' in test relative paths, not File.separator", relativePath.contains("\\"));
    Project project = getProject();
    VirtualFile file = project.getBaseDir().findFileByRelativePath(relativePath);
    if (requireExists) {
      //noinspection Contract
      assertNotNull("Unable to find file with relative path " + quote(relativePath), file);
    }
    return file;
  }

  @NotNull
  public IdeFrameFixture requestProjectSyncAndExpectFailure() {
    requestProjectSync();
    return waitForGradleProjectSyncToFail();
  }

  @NotNull
  public IdeFrameFixture requestProjectSyncAndSimulateError(@NotNull String error) {
    registerSyncErrorToSimulate(error);
    // When simulating the error, we don't have to wait for sync to happen. Sync never happens because the error is thrown before it (sync)
    // is started.
    return requestProjectSync();
  }

  @NotNull
  public IdeFrameFixture requestProjectSyncAndSimulateError(@NotNull Throwable error) {
    registerSyncErrorToSimulate(error);
    // When simulating the error, we don't have to wait for sync to happen. Sync never happens because the error is thrown before it (sync)
    // is started.
    return requestProjectSync();
  }

  @NotNull
  public IdeFrameFixture requestProjectSync() {
    myGradleProjectEventListener.reset();

    waitForGradleSyncAction();
    invokeMenuPath("Tools", "Android", "Sync Project with Gradle Files");

    return this;
  }

  private void waitForGradleSyncAction() {
    GuiTests.waitForBackgroundTasks(robot());
    GuiTests.waitUntilShowing(robot(), target(), new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton button) {
        AnAction action = button.getAction();
        return action != null
               && "Android.SyncProject".equals(ActionManager.getInstance().getId(action))
               && action.getTemplatePresentation().isEnabledAndVisible()
               && button.isEnabled();
      }
    });
  }

  @NotNull
  public IdeFrameFixture waitForGradleProjectSyncToFail() {
    try {
      waitForGradleProjectSyncToFinish(true);
      fail("Expecting project sync to fail");
    }
    catch (RuntimeException expected) {
      // expected failure.
    }
    GuiTests.waitForBackgroundTasks(robot());
    return this;
  }

  @NotNull
  public IdeFrameFixture waitForGradleProjectSyncToStart() {
    Project project = getProject();
    GradleSyncState syncState = GradleSyncState.getInstance(project);
    if (!syncState.isSyncInProgress()) {
      Wait.seconds(10).expecting("Syncing project " + quote(project.getName()) + " to finish")
        .until(myGradleProjectEventListener::isSyncStarted);
    }
    return this;
  }

  @NotNull
  public IdeFrameFixture waitForGradleProjectSyncToFinish() {
    waitForGradleProjectSyncToFinish(false);
    return this;
  }

  private void waitForGradleProjectSyncToFinish(boolean expectSyncFailure) {
    Project project = getProject();

    // ensure GradleInvoker (in-process build) is always enabled.
    AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
    buildConfiguration.USE_EXPERIMENTAL_FASTER_BUILD = true;

    Wait.seconds(10).expecting("Syncing project " + quote(project.getName()) + " to finish")
      .until(() -> {
        GradleSyncState syncState = GradleSyncState.getInstance(project);
        boolean syncFinished =
          (myGradleProjectEventListener.isSyncFinished() || syncState.isSyncNeeded() != ThreeState.YES) && !syncState.isSyncInProgress();
        if (expectSyncFailure) {
          syncFinished = syncFinished && myGradleProjectEventListener.hasSyncError();
        }
        return syncFinished;
      });

    waitForGradleSyncAction();

    if (myGradleProjectEventListener.hasSyncError()) {
      RuntimeException syncError = myGradleProjectEventListener.getSyncError();
      myGradleProjectEventListener.reset();
      throw syncError;
    }

    if (!myGradleProjectEventListener.isSyncSkipped()) {
      waitForBuildToFinish(SOURCE_GEN);
    }

    GuiTests.waitForBackgroundTasks(robot());
  }

  @NotNull
  private ActionButtonFixture findActionButtonByActionId(String actionId) {
    return ActionButtonFixture.findByActionId(actionId, robot(), target());
  }

  @NotNull
  public AndroidToolWindowFixture getAndroidToolWindow() {
    return new AndroidToolWindowFixture(getProject(), robot());
  }

  @NotNull
  public CapturesToolWindowFixture getCapturesToolWindow() {
    return new CapturesToolWindowFixture(getProject(), robot());
  }

  @NotNull
  public BuildVariantsToolWindowFixture getBuildVariantsWindow() {
    return new BuildVariantsToolWindowFixture(this);
  }

  @NotNull
  public MessagesToolWindowFixture getMessagesToolWindow() {
    return new MessagesToolWindowFixture(getProject(), robot());
  }

  @NotNull
  public GradleToolWindowFixture getGradleToolWindow() {
    return new GradleToolWindowFixture(getProject(), robot());
  }

  @NotNull
  public DeviceExplorerToolWindowFixture getDeviceExplorerToolWindow() {
    return new DeviceExplorerToolWindowFixture(getProject(), robot());
  }

  @NotNull
  public IdeSettingsDialogFixture openIdeSettings() {
    // Using invokeLater because we are going to show a *modal* dialog via API (instead of clicking a button, for example.) If we use
    // GuiActionRunner the test will hang until the modal dialog is closed.
    ApplicationManager.getApplication().invokeLater(
      () -> {
        Project project = getProject();
        ShowSettingsUtil.getInstance().showSettingsDialog(project, ShowSettingsUtilImpl.getConfigurableGroups(project, true));
      });
    return IdeSettingsDialogFixture.find(robot());
  }

  @NotNull
  public IdeFrameFixture deleteGradleWrapper() {
    deleteWrapper(getProjectPath());
    return this;
  }

  @NotNull
  public IdeFrameFixture requireGradleWrapperSet() {
    File wrapperDirPath = getGradleWrapperDirPath(getProjectPath());
    assertAbout(file()).that(wrapperDirPath).named("Gradle wrapper").isDirectory();
    return this;
  }

  public static void deleteWrapper(@NotNull File projectDirPath) {
    File wrapperDirPath = getGradleWrapperDirPath(projectDirPath);
    delete(wrapperDirPath);
    assertAbout(file()).that(wrapperDirPath).named("Gradle wrapper").doesNotExist();
  }

  @NotNull
  private static File getGradleWrapperDirPath(@NotNull File projectDirPath) {
    return new File(projectDirPath, FD_GRADLE);
  }

  @NotNull
  public IdeFrameFixture useLocalGradleDistribution(@NotNull File gradleHomePath) {
    return useLocalGradleDistribution(gradleHomePath.getPath());
  }

  @NotNull
  public IdeFrameFixture useLocalGradleDistribution(@NotNull String gradleHome) {
    GradleProjectSettings settings = getGradleSettings();
    settings.setDistributionType(LOCAL);
    settings.setGradleHome(gradleHome);
    return this;
  }

  @NotNull
  public GradleProjectSettings getGradleSettings() {
    return GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(getProject());
  }

  @NotNull
  public AvdManagerDialogFixture invokeAvdManager() {
    ActionButtonFixture button = findActionButtonByActionId("Android.RunAndroidAvdManager");
    button.requireVisible();
    button.requireEnabled();
    button.click();
    return AvdManagerDialogFixture.find(robot());
  }

  @NotNull
  public ProjectViewFixture getProjectView() {
    return new ProjectViewFixture(getProject(), robot());
  }

  @NotNull
  public Project getProject() {
    return target().getProject();
  }

  public WelcomeFrameFixture closeProject() {
    myIsClosed = true;
    return openFromMenu(WelcomeFrameFixture::find, "File", "Close Project");
  }

  public boolean isClosed() {
    return myIsClosed;
  }

  @NotNull
  public LibraryPropertiesDialogFixture showPropertiesForLibrary(@NotNull String libraryName) {
    return getProjectView().showPropertiesForLibrary(libraryName);
  }

  @NotNull
  public MessagesFixture findMessageDialog(@NotNull String title) {
    return MessagesFixture.findByTitle(robot(), title);
  }

  @NotNull
  public IdeFrameFixture setGradleJvmArgs(@NotNull String jvmArgs) {
    Project project = getProject();

    GradleSettings settings = GradleSettings.getInstance(project);
    settings.setGradleVmOptions(jvmArgs);

    Wait.seconds(1).expecting("Gradle settings to be set").until(() -> jvmArgs.equals(settings.getGradleVmOptions()));

    return this;
  }

  @NotNull
  public IdeFrameFixture updateGradleWrapperVersion(@NotNull String version) throws IOException {
    GradleWrapper.find(getProject()).updateDistributionUrlAndDisplayFailure(version);
    return this;
  }

  @NotNull
  public IdeFrameFixture updateAndroidGradlePluginVersion(@NotNull String version) throws IOException {
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        AndroidPluginVersionUpdater versionUpdater = AndroidPluginVersionUpdater.getInstance(getProject());
        AndroidPluginVersionUpdater.UpdateResult result = versionUpdater.updatePluginVersion(GradleVersion.parse(version), null);
        assertTrue("Android Gradle plugin version was not updated", result.isPluginVersionUpdated());
      }
    });
    return this;
  }

  @NotNull
  public GradleBuildModelFixture parseBuildFileForModule(@NotNull String moduleName) {
    Module module = getModule(moduleName);
    VirtualFile buildFile = getGradleBuildFile(module);
    Ref<GradleBuildModel> buildModelRef = new Ref<>();
    new ReadAction() {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        buildModelRef.set(GradleBuildModel.parseBuildFile(buildFile, getProject()));
      }
    }.execute();
    return new GradleBuildModelFixture(buildModelRef.get());
  }

  private static class NoOpDisposable implements Disposable {
    @Override
    public void dispose() {
    }
  }

  public void selectApp(@NotNull String appName) {
    ActionButtonFixture runButton = findRunApplicationButton();
    Container actionToolbarContainer = GuiQuery.getNonNull(() -> runButton.target().getParent());
    ComboBoxActionFixture comboBoxActionFixture = ComboBoxActionFixture.findComboBox(robot(), actionToolbarContainer);
    comboBoxActionFixture.selectItem(appName);
    robot().pressAndReleaseKey(KeyEvent.VK_ENTER);
    Wait.seconds(1).expecting("ComboBox to be selected").until(() -> appName.equals(comboBoxActionFixture.getSelectedItemText()));
  }

  /**
   * Gets the focus back to Android Studio if it was lost
   */
  public void requestFocusIfLost() {
    KeyboardFocusManager keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    Wait.seconds(1).expecting("a component to have the focus").until(() -> {
      // Keep requesting focus until it is obtained. Since there is no guarantee that the request focus will be granted,
      // keep asking until it is. This problem has appeared at least when not using a window manager when running tests.
      if (keyboardFocusManager.getFocusOwner() == null) {
        execute(new GuiTask() {
          @Override
          protected void executeInEDT() throws Throwable {
            target().requestFocus();
          }
        });
        return false;
      }
      return true;
    });
  }
}
