package com.google.cloud.tools.jib.frontend;

import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link JavaLayerConfigurations}. */
@RunWith(MockitoJUnitRunner.class)
public class JavaLayerConfigurationsTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private JavaLayerConfigurations.Builder.EntryAdder fileToLayerAdder;

  private static JavaLayerConfigurations createFakeConfigurations() throws IOException {
    return JavaLayerConfigurations.builder()
        .addDependencyFile(Paths.get("dependency"), AbsoluteUnixPath.get("/dependency/path"))
        .addSnapshotDependencyFile(
            Paths.get("snapshot dependency"), AbsoluteUnixPath.get("/snapshots"))
        .addResourceFile(Paths.get("resource"), AbsoluteUnixPath.get("/resources/here"))
        .addClassFile(Paths.get("class"), AbsoluteUnixPath.get("/classes/go/here"))
        .addExtraFile(Paths.get("extra file"), AbsoluteUnixPath.get("/some/extras"))
        .build();
  }

  private static List<Path> layerEntriesToSourceFiles(List<LayerEntry> entries) {
    return entries.stream().map(LayerEntry::getSourceFile).collect(Collectors.toList());
  }

  private static List<AbsoluteUnixPath> layerEntriesToExtractionPaths(List<LayerEntry> entries) {
    return entries.stream().map(LayerEntry::getExtractionPath).collect(Collectors.toList());
  }

  private static <T> List<String> toSortedStrings(List<T> paths) {
    return paths.stream().map(T::toString).sorted().collect(Collectors.toList());
  }

  private static void verifyRecursiveAdd(
      Supplier<List<LayerEntry>> layerEntriesSupplier, Path sourceRoot, String extractionRoot) {
    AbsoluteUnixPath extractionRootPath = AbsoluteUnixPath.get(extractionRoot);
    List<String> expectedPaths =
        Arrays.asList(
            "",
            "file1",
            "file2",
            "sub-directory",
            "sub-directory/file3",
            "sub-directory/file4",
            "sub-directory/leaf",
            "sub-directory/leaf/file5",
            "sub-directory/leaf/file6");

    List<Path> expectedSourcePaths =
        expectedPaths.stream().map(sourceRoot::resolve).collect(Collectors.toList());
    List<AbsoluteUnixPath> expectedTargetPaths =
        expectedPaths.stream().map(extractionRootPath::resolve).collect(Collectors.toList());

    List<Path> sourcePaths = layerEntriesToSourceFiles(layerEntriesSupplier.get());
    Assert.assertEquals(toSortedStrings(expectedSourcePaths), toSortedStrings(sourcePaths));

    List<AbsoluteUnixPath> targetPaths = layerEntriesToExtractionPaths(layerEntriesSupplier.get());
    Assert.assertEquals(toSortedStrings(expectedTargetPaths), toSortedStrings(targetPaths));
  }

  @Test
  public void testLabels() throws IOException {
    JavaLayerConfigurations javaLayerConfigurations = createFakeConfigurations();

    List<String> expectedLabels = new ArrayList<>();
    for (JavaLayerConfigurations.LayerType layerType : JavaLayerConfigurations.LayerType.values()) {
      expectedLabels.add(layerType.getName());
    }
    List<String> actualLabels = new ArrayList<>();
    for (LayerConfiguration layerConfiguration : javaLayerConfigurations.getLayerConfigurations()) {
      actualLabels.add(layerConfiguration.getName());
    }
    Assert.assertEquals(expectedLabels, actualLabels);
  }

  @Test
  public void testAddFile() throws IOException {
    JavaLayerConfigurations javaLayerConfigurations = createFakeConfigurations();

    List<List<Path>> expectedFiles =
        Arrays.asList(
            Collections.singletonList(Paths.get("dependency")),
            Collections.singletonList(Paths.get("snapshot dependency")),
            Collections.singletonList(Paths.get("resource")),
            Collections.singletonList(Paths.get("class")),
            Collections.singletonList(Paths.get("extra file")));
    List<List<Path>> actualFiles =
        javaLayerConfigurations
            .getLayerConfigurations()
            .stream()
            .map(LayerConfiguration::getLayerEntries)
            .map(JavaLayerConfigurationsTest::layerEntriesToSourceFiles)
            .collect(Collectors.toList());
    Assert.assertEquals(expectedFiles, actualFiles);
  }

  @Test
  public void testAddFile_directories() throws IOException, URISyntaxException {
    Path sourceDirectory = Paths.get(Resources.getResource("random-contents").toURI());

    JavaLayerConfigurations configurations =
        JavaLayerConfigurations.builder()
            .addDependencyFile(sourceDirectory, AbsoluteUnixPath.get("/libs/dir"))
            .addSnapshotDependencyFile(sourceDirectory, AbsoluteUnixPath.get("/snapshots/target"))
            .addResourceFile(sourceDirectory, AbsoluteUnixPath.get("/resources"))
            .addClassFile(sourceDirectory, AbsoluteUnixPath.get("/classes/here"))
            .addExtraFile(sourceDirectory, AbsoluteUnixPath.get("/extra/files"))
            .build();

    verifyRecursiveAdd(configurations::getDependencyLayerEntries, sourceDirectory, "/libs/dir");
    verifyRecursiveAdd(
        configurations::getSnapshotDependencyLayerEntries, sourceDirectory, "/snapshots/target");
    verifyRecursiveAdd(configurations::getResourceLayerEntries, sourceDirectory, "/resources");
    verifyRecursiveAdd(configurations::getClassLayerEntries, sourceDirectory, "/classes/here");
    verifyRecursiveAdd(configurations::getExtraFilesLayerEntries, sourceDirectory, "/extra/files");
  }

  @Test
  public void testAddFile_regularFiles() throws IOException, URISyntaxException {
    Path sourceFile =
        Paths.get(Resources.getResource("random-contents/sub-directory/leaf/file6").toURI());

    JavaLayerConfigurations configurations =
        JavaLayerConfigurations.builder()
            .addDependencyFile(sourceFile, AbsoluteUnixPath.get("/libs/file"))
            .addSnapshotDependencyFile(sourceFile, AbsoluteUnixPath.get("/snapshots/target/file"))
            .addResourceFile(sourceFile, AbsoluteUnixPath.get("/resources-file"))
            .addClassFile(sourceFile, AbsoluteUnixPath.get("/classes/file"))
            .addExtraFile(sourceFile, AbsoluteUnixPath.get("/some/file"))
            .build();

    Assert.assertEquals(
        Arrays.asList(new LayerEntry(sourceFile, AbsoluteUnixPath.get("/libs/file"))),
        configurations.getDependencyLayerEntries());
    Assert.assertEquals(
        Arrays.asList(new LayerEntry(sourceFile, AbsoluteUnixPath.get("/snapshots/target/file"))),
        configurations.getSnapshotDependencyLayerEntries());
    Assert.assertEquals(
        Arrays.asList(new LayerEntry(sourceFile, AbsoluteUnixPath.get("/resources-file"))),
        configurations.getResourceLayerEntries());
    Assert.assertEquals(
        Arrays.asList(new LayerEntry(sourceFile, AbsoluteUnixPath.get("/classes/file"))),
        configurations.getClassLayerEntries());
    Assert.assertEquals(
        Arrays.asList(new LayerEntry(sourceFile, AbsoluteUnixPath.get("/some/file"))),
        configurations.getExtraFilesLayerEntries());
  }

  @Test
  public void testWebApp() throws IOException {
    AbsoluteUnixPath appRoot = AbsoluteUnixPath.get("/usr/local/tomcat/webapps/ROOT/");

    JavaLayerConfigurations configurations =
        JavaLayerConfigurations.builder()
            .addResourceFile(Paths.get("test.jsp"), appRoot.resolve("test.jsp"))
            .addResourceFile(Paths.get("META-INF/"), appRoot.resolve("META-INF/"))
            .addResourceFile(Paths.get("context.xml"), appRoot.resolve("WEB-INF/context.xml"))
            .addResourceFile(Paths.get("sub_dir/"), appRoot.resolve("WEB-INF/sub_dir/"))
            .addDependencyFile(Paths.get("myLib.jar"), appRoot.resolve("WEB-INF/lib/myLib.jar"))
            .addSnapshotDependencyFile(
                Paths.get("my-SNAPSHOT.jar"), appRoot.resolve("WEB-INF/lib/my-SNAPSHOT.jar"))
            .addClassFile(Paths.get("test.class"), appRoot.resolve("WEB-INF/classes/test.class"))
            .addExtraFile(Paths.get("extra.file"), AbsoluteUnixPath.get("/extra.file"))
            .build();

    ImmutableList<LayerEntry> expectedDependenciesLayer =
        ImmutableList.of(
            new LayerEntry(Paths.get("myLib.jar"), appRoot.resolve("WEB-INF/lib/myLib.jar")));
    ImmutableList<LayerEntry> expectedSnapshotDependenciesLayer =
        ImmutableList.of(
            new LayerEntry(
                Paths.get("my-SNAPSHOT.jar"), appRoot.resolve("WEB-INF/lib/my-SNAPSHOT.jar")));
    ImmutableList<LayerEntry> expectedResourcesLayer =
        ImmutableList.of(
            new LayerEntry(Paths.get("test.jsp"), appRoot.resolve("test.jsp")),
            new LayerEntry(Paths.get("META-INF"), appRoot.resolve("META-INF")),
            new LayerEntry(Paths.get("context.xml"), appRoot.resolve("WEB-INF/context.xml")),
            new LayerEntry(Paths.get("sub_dir"), appRoot.resolve("WEB-INF/sub_dir")));
    ImmutableList<LayerEntry> expectedClassesLayer =
        ImmutableList.of(
            new LayerEntry(Paths.get("test.class"), appRoot.resolve("WEB-INF/classes/test.class")));
    ImmutableList<LayerEntry> expectedExtraLayer =
        ImmutableList.of(
            new LayerEntry(Paths.get("extra.file"), AbsoluteUnixPath.get("/extra.file")));

    Assert.assertEquals(expectedDependenciesLayer, configurations.getDependencyLayerEntries());
    Assert.assertEquals(
        expectedSnapshotDependenciesLayer, configurations.getSnapshotDependencyLayerEntries());
    Assert.assertEquals(expectedResourcesLayer, configurations.getResourceLayerEntries());
    Assert.assertEquals(expectedClassesLayer, configurations.getClassLayerEntries());
    Assert.assertEquals(expectedExtraLayer, configurations.getExtraFilesLayerEntries());
  }

  @Test
  public void testAddFilesRoot_file() throws IOException {
    temporaryFolder.newFile("file");

    Path sourceRoot = temporaryFolder.getRoot().toPath();
    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/path/in/container");

    JavaLayerConfigurations.Builder.addFilesRoot(
        sourceRoot, path -> true, basePath, fileToLayerAdder);
    Mockito.verify(fileToLayerAdder).add(sourceRoot.resolve("file"), basePath.resolve("file"));
    Mockito.verifyNoMoreInteractions(fileToLayerAdder);
  }

  @Test
  public void testAddFilesRoot_emptyDirectory() throws IOException {
    temporaryFolder.newFolder("leaf");

    Path sourceRoot = temporaryFolder.getRoot().toPath();
    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/");

    JavaLayerConfigurations.Builder.addFilesRoot(
        sourceRoot, path -> true, basePath, fileToLayerAdder);
    Mockito.verify(fileToLayerAdder).add(sourceRoot.resolve("leaf"), basePath.resolve("leaf"));
    Mockito.verifyNoMoreInteractions(fileToLayerAdder);
  }

  @Test
  public void testAddFilesRoot_nonEmptyDirectoryIgnored() throws IOException {
    temporaryFolder.newFolder("non-empty", "leaf");

    Path sourceRoot = temporaryFolder.getRoot().toPath();
    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/path/in/container");

    JavaLayerConfigurations.Builder.addFilesRoot(
        sourceRoot, path -> true, basePath, fileToLayerAdder);
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("non-empty"), basePath.resolve("non-empty"));
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("non-empty/leaf"), basePath.resolve("non-empty/leaf"));
    Mockito.verifyNoMoreInteractions(fileToLayerAdder);
  }

  @Test
  public void testAddFilesRoot_filter() throws IOException {
    temporaryFolder.newFile("non-target");
    temporaryFolder.newFolder("sub");
    temporaryFolder.newFile("sub/target");

    Path sourceRoot = temporaryFolder.getRoot().toPath();
    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/");

    Predicate<Path> nameIsTarget = path -> "target".equals(path.getFileName().toString());
    JavaLayerConfigurations.Builder.addFilesRoot(
        sourceRoot, nameIsTarget, basePath, fileToLayerAdder);
    Mockito.verify(fileToLayerAdder).add(sourceRoot.resolve("sub"), basePath.resolve("sub"));
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("sub/target"), basePath.resolve("sub/target"));
    Mockito.verifyNoMoreInteractions(fileToLayerAdder);
  }

  @Test
  public void testAddFilesRoot_emptyDirectoryForced() throws IOException {
    temporaryFolder.newFolder("sub", "leaf");

    Path sourceRoot = temporaryFolder.getRoot().toPath();
    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/path/in/container");

    JavaLayerConfigurations.Builder.addFilesRoot(
        sourceRoot, path -> false, basePath, fileToLayerAdder);
    Mockito.verify(fileToLayerAdder).add(sourceRoot.resolve("sub"), basePath.resolve("sub"));
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("sub/leaf"), basePath.resolve("sub/leaf"));
    Mockito.verifyNoMoreInteractions(fileToLayerAdder);
  }

  @Test
  public void testAddFilesRoot_fileAsSource() throws IOException {
    Path sourceFile = temporaryFolder.newFile("foo").toPath();

    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/");
    try {
      JavaLayerConfigurations.Builder.addFilesRoot(
          sourceFile, path -> true, basePath, fileToLayerAdder);
      Assert.fail();
    } catch (NotDirectoryException ex) {
      Assert.assertThat(ex.getMessage(), CoreMatchers.containsString("foo is not a directory"));
    }
  }

  @Test
  public void testAddFilesRoot_complex() throws IOException {
    temporaryFolder.newFile("A.class");
    temporaryFolder.newFile("B.java");
    temporaryFolder.newFolder("example", "dir");
    temporaryFolder.newFile("example/dir/C.class");
    temporaryFolder.newFile("example/C.class");
    temporaryFolder.newFolder("test", "resources", "leaf");
    temporaryFolder.newFile("test/resources/D.java");
    temporaryFolder.newFile("test/D.class");

    Path sourceRoot = temporaryFolder.getRoot().toPath();
    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/base");

    Predicate<Path> isClassFile = path -> path.getFileName().toString().endsWith(".class");

    JavaLayerConfigurations.Builder.addFilesRoot(
        sourceRoot, isClassFile, basePath, fileToLayerAdder);
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("A.class"), basePath.resolve("A.class"));
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("example"), basePath.resolve("example"));
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("example/dir"), basePath.resolve("example/dir"));
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("example/dir/C.class"), basePath.resolve("example/dir/C.class"));
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("example/C.class"), basePath.resolve("example/C.class"));
    Mockito.verify(fileToLayerAdder).add(sourceRoot.resolve("test"), basePath.resolve("test"));
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("test/resources"), basePath.resolve("test/resources"));
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("test/resources/leaf"), basePath.resolve("test/resources/leaf"));
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("test/D.class"), basePath.resolve("test/D.class"));
    Mockito.verifyNoMoreInteractions(fileToLayerAdder);
  }
}
