package com.jetbrains.lang.dart.ide.runner.test;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.filters.UrlFilter;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.autotest.ToggleAutoTestAction;
import com.intellij.execution.testframework.sm.SMCustomMessagesParsing;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.ide.runner.DartConsoleFilter;
import com.jetbrains.lang.dart.ide.runner.DartRelativePathsConsoleFilter;
import com.jetbrains.lang.dart.ide.runner.server.DartCommandLineRunningState;
import com.jetbrains.lang.dart.ide.runner.util.DartTestLocationProvider;
import com.jetbrains.lang.dart.ide.runner.util.Scope;
import com.jetbrains.lang.dart.sdk.DartSdk;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DartTestRunningState extends DartCommandLineRunningState {
  private static final String DART_FRAMEWORK_NAME = "DartTestRunner";
  private static final String PUB_SNAPSHOT_PATH = "/bin/snapshots/pub.dart.snapshot";
  private static final String RUN_COMMAND = "global run"; // TODO Change to "run" after package:test is committed.
  private static final String TEST_PACKAGE_SPEC = "test:test";
  private static final String EXPANDED_REPORTER_OPTION = " -r json"; // Initial space is required.
  private static final String NAME_REGEX_OPTION = "-n "; // Trailing space is required.

  public DartTestRunningState(final @NotNull ExecutionEnvironment environment) throws ExecutionException {
    super(environment);
  }

  @Override
  @NotNull
  public ExecutionResult execute(final @NotNull Executor executor, final @NotNull ProgramRunner runner) throws ExecutionException {
    final ProcessHandler processHandler = startProcess();
    final ConsoleView consoleView = createConsole(getEnvironment());
    consoleView.attachToProcess(processHandler);

    final DefaultExecutionResult executionResult =
      new DefaultExecutionResult(consoleView, processHandler, createActions(consoleView, processHandler, executor));
    executionResult.setRestartActions(new ToggleAutoTestAction());
    return executionResult;
  }

  private static ConsoleView createConsole(@NotNull ExecutionEnvironment env) {
    final Project project = env.getProject();
    final DartTestRunConfiguration runConfiguration = (DartTestRunConfiguration)env.getRunProfile();
    final DartTestRunnerParameters runnerParameters = runConfiguration.getRunnerParameters();

    final TestConsoleProperties testConsoleProperties = new DartConsoleProperties(runConfiguration, env);
    final ConsoleView consoleView = SMTestRunnerConnectionUtil.createConsole(DART_FRAMEWORK_NAME, testConsoleProperties);

    try {
      final VirtualFile dartFile = runnerParameters.getDartFile();
      consoleView.addMessageFilter(new DartConsoleFilter(project, dartFile));

      final String workingDir = StringUtil.isEmptyOrSpaces(runnerParameters.getWorkingDirectory())
                                ? dartFile.getParent().getPath()
                                : runnerParameters.getWorkingDirectory();
      consoleView.addMessageFilter(new DartRelativePathsConsoleFilter(project, workingDir));
      consoleView.addMessageFilter(new UrlFilter());
    }
    catch (RuntimeConfigurationError ignore) {/**/}

    Disposer.register(project, consoleView);
    return consoleView;
  }

  @NotNull
  @Override
  protected ProcessHandler startProcess() throws ExecutionException {
    Project project = getEnvironment().getProject();
    DartSdk sdk = DartSdk.getDartSdk(project);
    if (sdk == null) {
      throw new ExecutionException("Dart SDK cannot be found"); // can't happen
    }
    String sdkPath = sdk.getHomePath();
    DartTestRunnerParameters params = (DartTestRunnerParameters)myRunnerParameters;
    final String filePath = params.getFilePath();
    // TODO Try adding --pause-after-load to VM args to see if that makes test debugging possible
    StringBuilder builder = new StringBuilder();
    builder.append(RUN_COMMAND).append(' ').append(TEST_PACKAGE_SPEC);
    builder.append(EXPANDED_REPORTER_OPTION);
    if (filePath != null) {
      builder.append(' ').append(filePath);
    }
    String testName = params.getTestName();
    if (testName != null && !testName.isEmpty() && params.getScope() != Scope.ALL) {
      String safeName = StringUtil.escapeStringCharacters(testName);
      builder.append(' ').append(NAME_REGEX_OPTION).append('"').append(safeName).append('"');
    }
    params.setArguments(builder.toString());
    params.setWorkingDirectory(project.getBasePath());
    return doStartProcess(pathToDartUrl(sdkPath + PUB_SNAPSHOT_PATH));
  }

  private static String pathToDartUrl(@NonNls @NotNull String path) {
    final String url = VfsUtilCore.pathToUrl(path);
    return SystemInfo.isWindows ? url.replace("file://", "file:///") : url;
  }

  private static class DartConsoleProperties extends SMTRunnerConsoleProperties implements SMCustomMessagesParsing {
    public DartConsoleProperties(DartTestRunConfiguration runConfiguration, ExecutionEnvironment env) {
      super(runConfiguration, DART_FRAMEWORK_NAME, env.getExecutor());
      setUsePredefinedMessageFilter(false);
      setIdBasedTestTree(true);
    }

    @Nullable
    @Override
    public SMTestLocator getTestLocator() {
      return DartTestLocationProvider.INSTANCE;
    }

    @Override
    public OutputToGeneralTestEventsConverter createTestEventsConverter(@NotNull String testFrameworkName,
                                                                        @NotNull TestConsoleProperties consoleProperties) {
      return new DartTestToGeneralTestEventsConverter(testFrameworkName, consoleProperties);
    }
  }
}
