/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.maven.JibPluginConfiguration.AuthConfiguration;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collections;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link PluginConfigurationProcessor}. */
@RunWith(MockitoJUnitRunner.class)
public class PluginConfigurationProcessorTest {

  @Mock private Log mockLog;
  @Mock private JibPluginConfiguration mockJibPluginConfiguration;
  @Mock private MavenProjectProperties mockProjectProperties;
  @Mock private MavenSession mockMavenSession;
  @Mock private Settings mockMavenSettings;
  @Mock private MavenProject mavenProject;

  @Before
  public void setUp() throws Exception {
    Mockito.doReturn(mockMavenSession).when(mockJibPluginConfiguration).getSession();
    Mockito.doReturn(mockMavenSettings).when(mockMavenSession).getSettings();

    Mockito.doReturn(new AuthConfiguration()).when(mockJibPluginConfiguration).getBaseImageAuth();
    Mockito.doReturn(Collections.emptyList()).when(mockJibPluginConfiguration).getEntrypoint();
    Mockito.doReturn(Collections.emptyList()).when(mockJibPluginConfiguration).getJvmFlags();
    Mockito.doReturn(Collections.emptyList()).when(mockJibPluginConfiguration).getArgs();
    Mockito.doReturn(Collections.emptyList()).when(mockJibPluginConfiguration).getExposedPorts();
    Mockito.doReturn("/app").when(mockJibPluginConfiguration).getAppRoot();
    Mockito.doReturn(mavenProject).when(mockJibPluginConfiguration).getProject();

    Mockito.doReturn(JavaLayerConfigurations.builder().build())
        .when(mockProjectProperties)
        .getJavaLayerConfigurations();
    Mockito.doReturn("java.lang.Object")
        .when(mockProjectProperties)
        .getMainClass(mockJibPluginConfiguration);
  }

  /** Test with our default mocks, which try to mimic the bare Maven configuration. */
  @Test
  public void testPluginConfigurationProcessor_defaults()
      throws MojoExecutionException, InvalidImageReferenceException {
    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLog, mockJibPluginConfiguration, mockProjectProperties);
    ContainerConfiguration configuration = processor.getContainerConfigurationBuilder().build();
    Assert.assertEquals(
        Arrays.asList("java", "-cp", "/app/resources:/app/classes:/app/libs/*", "java.lang.Object"),
        configuration.getEntrypoint());

    Assert.assertEquals(
        ImageReference.parse("gcr.io/distroless/java").toString(),
        processor.getBaseImageConfigurationBuilder().build().getImage().toString());
    Mockito.verifyZeroInteractions(mockLog);
  }

  @Test
  public void testPluginConfigurationProcessor_warPackaging()
      throws MojoExecutionException, InvalidImageReferenceException {
    Mockito.doReturn("war").when(mavenProject).getPackaging();

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLog, mockJibPluginConfiguration, mockProjectProperties);

    Assert.assertEquals(
        ImageReference.parse("gcr.io/distroless/java/jetty").toString(),
        processor.getBaseImageConfigurationBuilder().build().getImage().toString());
    Mockito.verifyZeroInteractions(mockLog);
  }

  @Test
  public void testEntrypoint() throws MojoExecutionException {
    Mockito.doReturn(Arrays.asList("custom", "entrypoint"))
        .when(mockJibPluginConfiguration)
        .getEntrypoint();

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLog, mockJibPluginConfiguration, mockProjectProperties);
    ContainerConfiguration configuration = processor.getContainerConfigurationBuilder().build();

    Assert.assertEquals(Arrays.asList("custom", "entrypoint"), configuration.getEntrypoint());
    Mockito.verifyZeroInteractions(mockLog);
  }

  @Test
  public void testEntrypoint_defaultWarPackaging() throws MojoExecutionException {
    Mockito.doReturn(ImmutableList.of()).when(mockJibPluginConfiguration).getEntrypoint();
    Mockito.doReturn(mavenProject).when(mockJibPluginConfiguration).getProject();
    Mockito.doReturn("war").when(mavenProject).getPackaging();

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLog, mockJibPluginConfiguration, mockProjectProperties);
    ContainerConfiguration configuration = processor.getContainerConfigurationBuilder().build();

    Assert.assertEquals(
        Arrays.asList("java", "-jar", "/jetty/start.jar"), configuration.getEntrypoint());
    Mockito.verifyZeroInteractions(mockLog);
  }

  @Test
  public void testEntrypoint_defaulNonWarPackaging() throws MojoExecutionException {
    Mockito.doReturn(ImmutableList.of()).when(mockJibPluginConfiguration).getEntrypoint();
    Mockito.doReturn(mavenProject).when(mockJibPluginConfiguration).getProject();
    Mockito.doReturn(null).when(mavenProject).getPackaging();

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLog, mockJibPluginConfiguration, mockProjectProperties);
    ContainerConfiguration configuration = processor.getContainerConfigurationBuilder().build();

    Assert.assertEquals(
        Arrays.asList("java", "-cp", "/app/resources:/app/classes:/app/libs/*", "java.lang.Object"),
        configuration.getEntrypoint());
    Mockito.verifyZeroInteractions(mockLog);
  }

  @Test
  public void testEntrypoint_warningOnJvmFlags() throws MojoExecutionException {
    Mockito.doReturn(Arrays.asList("custom", "entrypoint"))
        .when(mockJibPluginConfiguration)
        .getEntrypoint();
    Mockito.doReturn(Arrays.asList("jvmFlag")).when(mockJibPluginConfiguration).getJvmFlags();

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLog, mockJibPluginConfiguration, mockProjectProperties);
    ContainerConfiguration configuration = processor.getContainerConfigurationBuilder().build();

    Assert.assertEquals(Arrays.asList("custom", "entrypoint"), configuration.getEntrypoint());
    Mockito.verify(mockLog)
        .warn("<mainClass> and <jvmFlags> are ignored when <entrypoint> is specified");
  }

  @Test
  public void testEntrypoint_warningOnMainclass() throws MojoExecutionException {
    Mockito.doReturn(Arrays.asList("custom", "entrypoint"))
        .when(mockJibPluginConfiguration)
        .getEntrypoint();
    Mockito.doReturn("java.util.Object").when(mockJibPluginConfiguration).getMainClass();

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLog, mockJibPluginConfiguration, mockProjectProperties);
    ContainerConfiguration configuration = processor.getContainerConfigurationBuilder().build();

    Assert.assertEquals(Arrays.asList("custom", "entrypoint"), configuration.getEntrypoint());
    Mockito.verify(mockLog)
        .warn("<mainClass> and <jvmFlags> are ignored when <entrypoint> is specified");
  }

  @Test
  public void testEntrypointClasspath_nonDefaultAppRoot() throws MojoExecutionException {
    Mockito.doReturn("/my/app").when(mockJibPluginConfiguration).getAppRoot();

    PluginConfigurationProcessor processor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mockLog, mockJibPluginConfiguration, mockProjectProperties);
    ContainerConfiguration configuration = processor.getContainerConfigurationBuilder().build();

    Assert.assertEquals(
        "/my/app/resources:/my/app/classes:/my/app/libs/*", configuration.getEntrypoint().get(2));
  }

  @Test
  public void testGetAppRootChecked() throws MojoExecutionException {
    Mockito.doReturn("/some/root").when(mockJibPluginConfiguration).getAppRoot();

    Assert.assertEquals(
        AbsoluteUnixPath.get("/some/root"),
        PluginConfigurationProcessor.getAppRootChecked(mockJibPluginConfiguration));
  }

  @Test
  public void testGetAppRootChecked_errorOnNonAbsolutePath() {
    Mockito.doReturn("relative/path").when(mockJibPluginConfiguration).getAppRoot();

    try {
      PluginConfigurationProcessor.getAppRootChecked(mockJibPluginConfiguration);
      Assert.fail();
    } catch (MojoExecutionException ex) {
      Assert.assertEquals(
          "<container><appRoot> is not an absolute Unix-style path: relative/path",
          ex.getMessage());
    }
  }

  @Test
  public void testGetAppRootChecked_errorOnWindowsPath() {
    Mockito.doReturn("\\windows\\path").when(mockJibPluginConfiguration).getAppRoot();

    try {
      PluginConfigurationProcessor.getAppRootChecked(mockJibPluginConfiguration);
      Assert.fail();
    } catch (MojoExecutionException ex) {
      Assert.assertEquals(
          "<container><appRoot> is not an absolute Unix-style path: \\windows\\path",
          ex.getMessage());
    }
  }

  @Test
  public void testGetAppRootChecked_errorOnWindowsPathWithDriveLetter() {
    Mockito.doReturn("C:\\windows\\path").when(mockJibPluginConfiguration).getAppRoot();

    try {
      PluginConfigurationProcessor.getAppRootChecked(mockJibPluginConfiguration);
      Assert.fail();
    } catch (MojoExecutionException ex) {
      Assert.assertEquals(
          "<container><appRoot> is not an absolute Unix-style path: C:\\windows\\path",
          ex.getMessage());
    }
  }

  @Test
  public void testGetAppRootChecked_defaultNonWarPackaging() throws MojoExecutionException {
    Mockito.doReturn("").when(mockJibPluginConfiguration).getAppRoot();
    Mockito.doReturn(mavenProject).when(mockJibPluginConfiguration).getProject();
    Mockito.doReturn(null).when(mavenProject).getPackaging();

    Assert.assertEquals(
        AbsoluteUnixPath.get("/app"),
        PluginConfigurationProcessor.getAppRootChecked(mockJibPluginConfiguration));
  }

  @Test
  public void testGetAppRootChecked_defaultJarPackaging() throws MojoExecutionException {
    Mockito.doReturn("").when(mockJibPluginConfiguration).getAppRoot();
    Mockito.doReturn(mavenProject).when(mockJibPluginConfiguration).getProject();
    Mockito.doReturn("jar").when(mavenProject).getPackaging();

    Assert.assertEquals(
        AbsoluteUnixPath.get("/app"),
        PluginConfigurationProcessor.getAppRootChecked(mockJibPluginConfiguration));
  }

  @Test
  public void testGetAppRootChecked_defaultWarPackaging() throws MojoExecutionException {
    Mockito.doReturn("").when(mockJibPluginConfiguration).getAppRoot();
    Mockito.doReturn(mavenProject).when(mockJibPluginConfiguration).getProject();
    Mockito.doReturn("war").when(mavenProject).getPackaging();

    Assert.assertEquals(
        AbsoluteUnixPath.get("/jetty/webapps/ROOT"),
        PluginConfigurationProcessor.getAppRootChecked(mockJibPluginConfiguration));
  }

  @Test
  public void testGetBaseImage_defaultWarPackaging() {
    Mockito.doReturn(mavenProject).when(mockJibPluginConfiguration).getProject();
    Mockito.doReturn("war").when(mavenProject).getPackaging();

    Assert.assertEquals(
        "gcr.io/distroless/java/jetty",
        PluginConfigurationProcessor.getBaseImage(mockJibPluginConfiguration));
  }

  @Test
  public void testGetBaseImage_defaultNonWarPackaging() {
    Mockito.doReturn(mavenProject).when(mockJibPluginConfiguration).getProject();
    Mockito.doReturn(null).when(mavenProject).getPackaging();

    Assert.assertEquals(
        "gcr.io/distroless/java",
        PluginConfigurationProcessor.getBaseImage(mockJibPluginConfiguration));
  }

  @Test
  public void testGetBaseImage_nonDefault() {
    Mockito.doReturn("tomcat").when(mockJibPluginConfiguration).getBaseImage();

    Assert.assertEquals(
        "tomcat", PluginConfigurationProcessor.getBaseImage(mockJibPluginConfiguration));
  }

  // TODO should test other behaviours
}
