/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.ballerina.projects.util;

import io.ballerina.projects.DocumentId;
import io.ballerina.projects.JarLibrary;
import io.ballerina.projects.Module;
import io.ballerina.projects.ModuleName;
import io.ballerina.projects.Package;
import io.ballerina.projects.PackageDescriptor;
import io.ballerina.projects.PackageManifest;
import io.ballerina.projects.PackageName;
import io.ballerina.projects.PlatformLibraryScope;
import io.ballerina.projects.ProjectException;
import io.ballerina.projects.ResolvedPackageDependency;
import io.ballerina.projects.Settings;
import org.apache.commons.compress.archivers.jar.JarArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntryPredicate;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.ballerinalang.compiler.BLangCompilerException;
import org.wso2.ballerinalang.compiler.CompiledJarFile;
import org.wso2.ballerinalang.compiler.util.ProjectDirConstants;
import org.wso2.ballerinalang.util.Lists;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.ballerina.projects.util.FileUtils.getFileNameWithoutExtension;
import static io.ballerina.projects.util.ProjectConstants.ASM_COMMONS_JAR;
import static io.ballerina.projects.util.ProjectConstants.ASM_JAR;
import static io.ballerina.projects.util.ProjectConstants.ASM_TREE_JAR;
import static io.ballerina.projects.util.ProjectConstants.BALLERINA_HOME;
import static io.ballerina.projects.util.ProjectConstants.BALLERINA_HOME_BRE;
import static io.ballerina.projects.util.ProjectConstants.BALLERINA_PACK_VERSION;
import static io.ballerina.projects.util.ProjectConstants.BALLERINA_TOML;
import static io.ballerina.projects.util.ProjectConstants.BLANG_COMPILED_JAR_EXT;
import static io.ballerina.projects.util.ProjectConstants.BLANG_COMPILED_PKG_BINARY_EXT;
import static io.ballerina.projects.util.ProjectConstants.DIFF_UTILS_JAR;
import static io.ballerina.projects.util.ProjectConstants.JACOCO_CORE_JAR;
import static io.ballerina.projects.util.ProjectConstants.JACOCO_REPORT_JAR;
import static io.ballerina.projects.util.ProjectConstants.LIB_DIR;
import static io.ballerina.projects.util.ProjectConstants.PROPERTIES_FILE;
import static io.ballerina.projects.util.ProjectConstants.TEST_CORE_JAR_PREFIX;
import static io.ballerina.projects.util.ProjectConstants.TEST_RUNTIME_JAR_PREFIX;
import static io.ballerina.projects.util.ProjectConstants.USER_NAME;

/**
 * Project related util methods.
 *
 * @since 2.0.0
 */
public class ProjectUtils {
    private static final Pattern separatedIdentifierPattern = Pattern.compile("^[a-zA-Z0-9_.]*$");
    private static final Pattern orgNamePattern = Pattern.compile("^[a-zA-Z0-9_]*$");
    private static final String UNKNOWN = "unknown";

    /**
     * Validates the org-name.
     *
     * @param orgName The org-name
     * @return True if valid org-name or package name, else false.
     */
    public static boolean validateOrgName(String orgName) {
        Matcher m = orgNamePattern.matcher(orgName);
        return m.matches();
    }

    /**
     * Validates the package name.
     *
     * @param packageName The package name.
     * @return True if valid package name, else false.
     */
    public static boolean validatePackageName(String packageName) {
        return validateDotSeparatedIdentifiers(packageName);
    }

    /**
     * Validates the module name.
     *
     * @param moduleName The module name.
     * @return True if valid module name, else false.
     */
    public static boolean validateModuleName(String moduleName) {
        return validateDotSeparatedIdentifiers(moduleName);
    }

    /**
     * Find the project root by recursively up to the root.
     *
     * @param filePath project path
     * @return project root
     */
    public static Path findProjectRoot(Path filePath) {
        if (filePath != null) {
            filePath = filePath.toAbsolutePath().normalize();
            if (filePath.toFile().isDirectory()) {
                if (Files.exists(filePath.resolve(BALLERINA_TOML))) {
                    return filePath;
                }
            }
            return findProjectRoot(filePath.getParent());
        }
        return null;
    }

    /**
     * Checks if the path is a Ballerina project.
     *
     * @param sourceRoot source root of the project.
     * @return true if the directory is a project repo, false if its the home repo
     */
    public static boolean isBallerinaProject(Path sourceRoot) {
        Path ballerinaToml = sourceRoot.resolve(BALLERINA_TOML);
        return Files.isDirectory(sourceRoot)
                && Files.exists(ballerinaToml)
                && Files.isRegularFile(ballerinaToml);
    }

    /**
     * Guess organization name based on user name in system.
     *
     * @return organization name
     */
    public static String guessOrgName() {
        String guessOrgName = System.getProperty(USER_NAME);
        if (guessOrgName == null) {
            guessOrgName = "my_org";
        } else {
            guessOrgName = guessOrgName.toLowerCase(Locale.getDefault());
        }
        return guessOrgName;
    }

    /**
     * Guess package name with valid pattern.
     *
     * @param packageName package name
     * @return package name
     */
    public static String guessPkgName(String packageName) {
        if (!validatePackageName(packageName)) {
            return packageName.replaceAll("[^a-zA-Z0-9_]", "_");
        }
        return packageName;
    }

    public static String getBalaName(PackageManifest pkgDesc) {
        return ProjectUtils.getBalaName(pkgDesc.org().toString(),
                pkgDesc.name().toString(),
                pkgDesc.version().toString(),
                null
        );
    }

    public static String getBalaName(String org, String pkgName, String version, String platform) {
        // <orgname>-<packagename>-<platform>-<version>.bala
        if (platform == null || "".equals(platform)) {
            platform = "any";
        }
        return org + "-" + pkgName + "-" + platform + "-" + version + BLANG_COMPILED_PKG_BINARY_EXT;
    }

    /**
     * Returns the relative path of extracted bala beginning from the package org.
     *
     * @param org package org
     * @param pkgName package name
     * @param version package version
     * @param platform version, null converts to `any`
     * @return relative bala path
     */
    public static Path getRelativeBalaPath(String org, String pkgName, String version, String platform) {
        // <orgname>-<packagename>-<platform>-<version>.bala
        if (platform == null || "".equals(platform)) {
            platform = "any";
        }
        return Paths.get(org, pkgName, version, platform);
    }

    public static String getJarFileName(Package pkg) {
        // <orgname>-<packagename>-<version>.jar
        return pkg.packageOrg().toString() + "-" + pkg.packageName().toString()
                + "-" + pkg.packageVersion() + BLANG_COMPILED_JAR_EXT;
    }

    public static String getExecutableName(Package pkg) {
        // <packagename>.jar
        return pkg.packageName().toString() + BLANG_COMPILED_JAR_EXT;
    }

    public static String getOrgFromBalaName(String balaName) {
        return balaName.split("-")[0];
    }

    public static String getPackageNameFromBalaName(String balaName) {
        return balaName.split("-")[1];
    }

    public static String getVersionFromBalaName(String balaName) {
        // TODO validate this method of getting the version of the bala
        String versionAndExtension = balaName.split("-")[3];
        int extensionIndex = versionAndExtension.indexOf(BLANG_COMPILED_PKG_BINARY_EXT);
        return versionAndExtension.substring(0, extensionIndex);
    }

    private static final HashSet<String> excludeExtensions = new HashSet<>(Lists.of("DSA", "SF"));

    public static Path getBalHomePath() {
        return Paths.get(System.getProperty(BALLERINA_HOME));
    }

    public static Path getBallerinaRTJarPath() {
        String ballerinaVersion = getBallerinaPackVersion();
        String runtimeJarName = "ballerina-rt-" + ballerinaVersion + BLANG_COMPILED_JAR_EXT;
        return getBalHomePath().resolve("bre").resolve("lib").resolve(runtimeJarName);
    }

    public static List<JarLibrary> testDependencies() {
        List<JarLibrary> dependencies = new ArrayList<>();
        String testPkgName = "ballerina/test";

        String ballerinaVersion = getBallerinaPackVersion();
        Path homeLibPath = getBalHomePath().resolve(BALLERINA_HOME_BRE).resolve(LIB_DIR);
        String testRuntimeJarName = TEST_RUNTIME_JAR_PREFIX + ballerinaVersion + BLANG_COMPILED_JAR_EXT;
        String testCoreJarName = TEST_CORE_JAR_PREFIX + ballerinaVersion + BLANG_COMPILED_JAR_EXT;
        String langJarName = "ballerina-lang-" + ballerinaVersion + BLANG_COMPILED_JAR_EXT;

        Path testRuntimeJarPath = homeLibPath.resolve(testRuntimeJarName);
        Path testCoreJarPath = homeLibPath.resolve(testCoreJarName);
        Path langJarPath = homeLibPath.resolve(langJarName);
        Path jacocoCoreJarPath = homeLibPath.resolve(JACOCO_CORE_JAR);
        Path jacocoReportJarPath = homeLibPath.resolve(JACOCO_REPORT_JAR);
        Path asmJarPath = homeLibPath.resolve(ASM_JAR);
        Path asmTreeJarPath = homeLibPath.resolve(ASM_TREE_JAR);
        Path asmCommonsJarPath = homeLibPath.resolve(ASM_COMMONS_JAR);
        Path diffUtilsJarPath = homeLibPath.resolve(DIFF_UTILS_JAR);

        dependencies.add(new JarLibrary(testRuntimeJarPath, PlatformLibraryScope.TEST_ONLY, testPkgName));
        dependencies.add(new JarLibrary(testCoreJarPath, PlatformLibraryScope.TEST_ONLY, testPkgName));
        dependencies.add(new JarLibrary(langJarPath, PlatformLibraryScope.TEST_ONLY, testPkgName));
        dependencies.add(new JarLibrary(jacocoCoreJarPath, PlatformLibraryScope.TEST_ONLY, testPkgName));
        dependencies.add(new JarLibrary(jacocoReportJarPath, PlatformLibraryScope.TEST_ONLY, testPkgName));
        dependencies.add(new JarLibrary(asmJarPath, PlatformLibraryScope.TEST_ONLY, testPkgName));
        dependencies.add(new JarLibrary(asmTreeJarPath, PlatformLibraryScope.TEST_ONLY, testPkgName));
        dependencies.add(new JarLibrary(asmCommonsJarPath, PlatformLibraryScope.TEST_ONLY, testPkgName));
        dependencies.add(new JarLibrary(diffUtilsJarPath, PlatformLibraryScope.TEST_ONLY, testPkgName));
        return dependencies;
    }

    public static Path generateObservabilitySymbolsJar(String packageName) throws IOException {
        Path jarPath = Files.createTempFile(packageName + "-", "-observability-symbols.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        JarOutputStream jarOutputStream = new JarOutputStream(new BufferedOutputStream(
                new FileOutputStream(jarPath.toFile())), manifest);
        jarOutputStream.close();
        return jarPath;
    }

    public static void assembleExecutableJar(Manifest manifest,
                                             List<CompiledJarFile> compiledPackageJarList,
                                             Path targetPath) throws IOException {

        // Used to prevent adding duplicated entries during the final jar creation.
        HashSet<String> copiedEntries = new HashSet<>();

        try (ZipArchiveOutputStream outStream = new ZipArchiveOutputStream(
                new BufferedOutputStream(new FileOutputStream(targetPath.toString())))) {
            copyRuntimeJar(outStream, getBallerinaRTJarPath(), copiedEntries);

            JarArchiveEntry e = new JarArchiveEntry(JarFile.MANIFEST_NAME);
            outStream.putArchiveEntry(e);
            manifest.write(new BufferedOutputStream(outStream));
            outStream.closeArchiveEntry();

            for (CompiledJarFile compiledJarFile : compiledPackageJarList) {
                for (Map.Entry<String, byte[]> keyVal : compiledJarFile.getJarEntries().entrySet()) {
                    copyEntry(copiedEntries, outStream, keyVal);
                }
            }
        }
    }

    private static void copyEntry(HashSet<String> copiedEntries,
                                  ZipArchiveOutputStream outStream,
                                  Map.Entry<String, byte[]> keyVal) throws IOException {
        String entryName = keyVal.getKey();
        if (!isCopiedOrExcludedEntry(entryName, copiedEntries)) {
            byte[] entryContent = keyVal.getValue();
            JarArchiveEntry entry = new JarArchiveEntry(entryName);
            outStream.putArchiveEntry(entry);
            outStream.write(entryContent);
            outStream.closeArchiveEntry();
        }
    }

    /**
     * Copies a given jar file into the executable fat jar.
     *
     * @param ballerinaRTJarPath Ballerina runtime jar path.
     * @throws IOException If jar file copying is failed.
     */
    public static void copyRuntimeJar(ZipArchiveOutputStream outStream,
                                      Path ballerinaRTJarPath,
                                      HashSet<String> copiedEntries) throws IOException {
        // TODO This code is copied from the current executable jar creation logic. We may need to refactor this.
        HashMap<String, StringBuilder> services = new HashMap<>();
        ZipFile zipFile = new ZipFile(ballerinaRTJarPath.toString());
        ZipArchiveEntryPredicate predicate = entry -> {

            String entryName = entry.getName();
            if (entryName.equals("META-INF/MANIFEST.MF")) {
                return false;
            }

            if (entryName.startsWith("META-INF/services")) {
                StringBuilder s = services.get(entryName);
                if (s == null) {
                    s = new StringBuilder();
                    services.put(entryName, s);
                }
                char c = '\n';

                int len;
                try (BufferedInputStream inStream = new BufferedInputStream(zipFile.getInputStream(entry))) {
                    while ((len = inStream.read()) != -1) {
                        c = (char) len;
                        s.append(c);
                    }
                } catch (IOException e) {
                    throw new ProjectException(e);
                }
                if (c != '\n') {
                    s.append('\n');
                }

                // Its not required to copy SPI entries in here as we'll be adding merged SPI related entries
                // separately. Therefore the predicate should be set as false.
                return false;
            }

            // Skip already copied files or excluded extensions.
            if (isCopiedOrExcludedEntry(entryName, copiedEntries)) {
                return false;
            }
            // SPIs will be merged first and then put into jar separately.
            copiedEntries.add(entryName);
            return true;
        };

        // Transfers selected entries from this zip file to the output stream, while preserving its compression and
        // all the other original attributes.
        zipFile.copyRawEntries(outStream, predicate);
        zipFile.close();

        for (Map.Entry<String, StringBuilder> entry : services.entrySet()) {
            String s = entry.getKey();
            StringBuilder service = entry.getValue();
            JarArchiveEntry e = new JarArchiveEntry(s);
            outStream.putArchiveEntry(e);
            outStream.write(service.toString().getBytes(StandardCharsets.UTF_8));
            outStream.closeArchiveEntry();
        }
    }

    private static boolean isCopiedOrExcludedEntry(String entryName, HashSet<String> copiedEntries) {
        return copiedEntries.contains(entryName) ||
                excludeExtensions.contains(entryName.substring(entryName.lastIndexOf(".") + 1));
    }

    /**
     * Construct and return the thin jar name of the provided module.
     *
     * @param module Module instance
     * @return the name of the thin jar
     */
    public static String getJarFileName(Module module) {
        String jarName;
        if (module.packageInstance().manifest().org().anonymous()) {
            DocumentId documentId = module.documentIds().iterator().next();
            String documentName = module.document(documentId).name();
            jarName = getFileNameWithoutExtension(documentName);
        } else {
            ModuleName moduleName = module.moduleName();
            if (moduleName.isDefaultModuleName()) {
                jarName = moduleName.packageName().toString();
            } else {
                jarName = moduleName.moduleNamePart();
            }
        }
        return jarName;
    }

    /**
     * Create and get the home repository path.
     *
     * @return home repository path
     */
    public static Path createAndGetHomeReposPath() {
        Path homeRepoPath;
        String homeRepoDir = System.getenv(ProjectConstants.HOME_REPO_ENV_KEY);
        if (homeRepoDir == null || homeRepoDir.isEmpty()) {
            String userHomeDir = System.getProperty(ProjectConstants.USER_HOME);
            if (userHomeDir == null || userHomeDir.isEmpty()) {
                throw new BLangCompilerException("Error creating home repository: unable to get user home directory");
            }
            homeRepoPath = Paths.get(userHomeDir, ProjectConstants.HOME_REPO_DEFAULT_DIRNAME);
        } else {
            // User has specified the home repo path with env variable.
            homeRepoPath = Paths.get(homeRepoDir);
        }

        homeRepoPath = homeRepoPath.toAbsolutePath();
        if (Files.exists(homeRepoPath) && !Files.isDirectory(homeRepoPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new BLangCompilerException("Home repository is not a directory: " + homeRepoPath.toString());
        }
        return homeRepoPath;
    }

    /**
     * Check if a ballerina module exist.
     * @param projectPath project path
     * @param moduleName module name
     * @return module exist
     */
    public static boolean isModuleExist(Path projectPath, String moduleName) {
        Path modulePath = projectPath.resolve(ProjectConstants.MODULES_ROOT).resolve(moduleName);
        return Files.exists(modulePath);
    }

    /**
     * Initialize proxy if proxy is available in settings.toml.
     *
     * @param proxy toml model proxy
     * @return proxy
     */
    public static Proxy initializeProxy(io.ballerina.projects.internal.model.Proxy proxy) {
        if (proxy != null && !"".equals(proxy.host()) && proxy.port() > 0) {
            InetSocketAddress proxyInet = new InetSocketAddress(proxy.host(), proxy.port());
            if (!"".equals(proxy.username()) && "".equals(proxy.password())) {
                Authenticator authenticator = new RemoteAuthenticator(proxy);
                Authenticator.setDefault(authenticator);
            }
            return new Proxy(Proxy.Type.HTTP, proxyInet);
        }

        return null;
    }

    /**
     * Read the access token generated for the CLI.
     *
     * @return access token for generated for the CLI
     */
    public static String getAccessTokenOfCLI(Settings settings) {
        // The access token can be specified as an environment variable or in 'Settings.toml'. First we would check if
        // the access token was specified as an environment variable. If not we would read it from 'Settings.toml'
        String tokenAsEnvVar = System.getenv(ProjectConstants.BALLERINA_CENTRAL_ACCESS_TOKEN);
        if (tokenAsEnvVar != null && !tokenAsEnvVar.isEmpty()) {
            return tokenAsEnvVar;
        }
        if (settings.getCentral() != null) {
            return settings.getCentral().getAccessToken();
        }
        return "";
    }

    public static void checkWritePermission(Path path) {
        if (!path.toFile().canWrite()) {
            throw new ProjectException("'" + path.normalize() + "' does not have write permissions");
        }
    }

    public static void checkReadPermission(Path path) {
        if (!path.toFile().canRead()) {
            throw new ProjectException("'" + path.normalize() + "' does not have read permissions");
        }
    }

    public static void checkExecutePermission(Path path) {
        if (!path.toFile().canRead()) {
            throw new ProjectException("'" + path.normalize() + "' does not have execute permissions");
        }
    }

    private static boolean validateDotSeparatedIdentifiers(String identifiers) {
        Matcher m = separatedIdentifierPattern.matcher(identifiers);
        return m.matches();
    }

    /**
     * Get `Dependencies.toml` content as a string.
     *
     * @param pkgGraphDependencies    direct dependencies of the package dependency graph
     * @param pkgManifestDependencies list of dependencies as of root package manifest
     * @return Dependencies.toml` content
     */
    public static String getDependenciesTomlContent(Collection<ResolvedPackageDependency> pkgGraphDependencies,
            List<PackageManifest.Dependency> pkgManifestDependencies) {
        StringBuilder content = new StringBuilder();

        // write dependencies already in the `Dependencies.toml`
        pkgManifestDependencies.forEach(manifestDependency -> {
            addDependencyContent(content, manifestDependency.org().value(), manifestDependency.name().value(),
                                 manifestDependency.version().value().toString());
            if (manifestDependency.repository() != null) {
                content.append("repository = \"").append(manifestDependency.repository()).append("\"\n");
            }
            content.append("\n");
        });

        // write dependencies from package dependency graph
        pkgGraphDependencies.forEach(graphDependency -> {
            PackageDescriptor descriptor = graphDependency.packageInstance().descriptor();

            // ignore lang libs & ballerina internal packages
            if (!descriptor.isBuiltInPackage() && !graphDependency.injected()) {
                // write dependency, if it not already exists in `Dependencies.toml`
                if (!isPkgManifestDependency(graphDependency, pkgManifestDependencies)) {
                    // write dependencies only with stable versions
                    if (!descriptor.version().value().isPreReleaseVersion()) {
                        addDependencyContent(content, descriptor.org().value(), descriptor.name().value(),
                                             descriptor.version().value().toString());
                        content.append("\n");
                    }
                }
            }
        });
        return String.valueOf(content);
    }

    private static boolean isPkgManifestDependency(ResolvedPackageDependency graphDependency,
            List<PackageManifest.Dependency> pkgManifestDependencies) {
        if (pkgManifestDependencies.isEmpty()) {
            return false;
        }

        for (PackageManifest.Dependency manifestDependency : pkgManifestDependencies) {
            if (manifestDependency.org().value().equals(graphDependency.packageInstance().packageOrg().value())
                    && manifestDependency.name().value()
                    .equals(graphDependency.packageInstance().packageName().value())) {
                return true;
            }
        }
        return false;
    }

    private static void addDependencyContent(StringBuilder content, String org, String name, String version) {
        content.append("[[dependency]]\n");
        content.append("org = \"").append(org).append("\"\n");
        content.append("name = \"").append(name).append("\"\n");
        content.append("version = \"").append(version).append("\"\n");
    }

    public static List<PackageName> getPossiblePackageNames(String moduleName) {
        String[] modNameParts = moduleName.split("\\.");
        StringJoiner pkgNameBuilder = new StringJoiner(".");
        List<PackageName> possiblePkgNames = new ArrayList<>(modNameParts.length);
        for (String modNamePart : modNameParts) {
            pkgNameBuilder.add(modNamePart);
            possiblePkgNames.add(PackageName.from(pkgNameBuilder.toString()));
        }
        return possiblePkgNames;
    }

    /**
     * Extracts a .bala file into the provided destination directory.
     *
     * @param balaFilePath .bala file path
     * @param balaFileDestPath directory into which the .bala should be extracted
     * @throws IOException if extraction fails
     */
    public static void extractBala(Path balaFilePath, Path balaFileDestPath) throws IOException {
        if (Files.exists(balaFileDestPath) && Files.isDirectory(balaFilePath)) {
            deleteDirectory(balaFileDestPath);
        } else {
            Files.createDirectories(balaFileDestPath);
        }

        byte[] buffer = new byte[1024 * 4];
        try (FileInputStream fileInputStream = new FileInputStream(balaFilePath.toString())) {
            // Get the zip file content.
            try (ZipInputStream zipInputStream = new ZipInputStream(fileInputStream)) {
                // Get the zipped file entry.
                ZipEntry zipEntry = zipInputStream.getNextEntry();
                while (zipEntry != null) {
                    // Get the name.
                    String fileName = zipEntry.getName();
                    // Construct the output file.
                    Path outputPath = balaFileDestPath.resolve(fileName);
                    // If the zip entry is for a directory, we create the directory and continue with the next entry.
                    if (zipEntry.isDirectory()) {
                        Files.createDirectories(outputPath);
                        zipEntry = zipInputStream.getNextEntry();
                        continue;
                    }

                    // Create all non-existing directories.
                    Files.createDirectories(Optional.of(outputPath.getParent()).get());
                    // Create a new file output stream.
                    try (FileOutputStream fileOutputStream = new FileOutputStream(outputPath.toFile())) {
                        // Write the content from zip input stream to the file output stream.
                        int len;
                        while ((len = zipInputStream.read(buffer)) > 0) {
                            fileOutputStream.write(buffer, 0, len);
                        }
                    }
                    // Continue with the next entry.
                    zipEntry = zipInputStream.getNextEntry();
                }
                // Close zip input stream.
                zipInputStream.closeEntry();
            }
        }
    }

    /**
     * Delete the given directory along with all files and sub directories.
     *
     * @param directoryPath Directory to delete.
     */
    public static boolean deleteDirectory(Path directoryPath) {
        File directory = new File(String.valueOf(directoryPath));
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File f : files) {
                    boolean success = deleteDirectory(f.toPath());
                    if (!success) {
                        return false;
                    }
                }
            }
        }
        return directory.delete();
    }

    /**
     * Get the ballerina version the package is built with.
     *
     * @return ballerina version
     */
    public static String getBallerinaVersion() {
        try (InputStream inputStream = ProjectUtils.class.getResourceAsStream(ProjectDirConstants.PROPERTIES_FILE)) {
            var properties = new Properties();
            properties.load(inputStream);
            return properties.getProperty(ProjectDirConstants.BALLERINA_VERSION);
        } catch (Throwable ignore) {
        }
        return UNKNOWN;
    }

    /**
     * Get the ballerina pack version.
     *
     * @return ballerina pack version
     */
    public static String getBallerinaPackVersion() {
        try (InputStream inputStream = ProjectUtils.class.getResourceAsStream(PROPERTIES_FILE)) {
            var properties = new Properties();
            properties.load(inputStream);
            return properties.getProperty(BALLERINA_PACK_VERSION);
        } catch (Throwable ignore) {
        }
        return UNKNOWN;
    }

    /**
     * Get the ballerina short version.
     *
     * @return ballerina short version
     */
    public static String getBallerinaShortVersion() {
        try (InputStream inputStream = ProjectUtils.class.getResourceAsStream(ProjectDirConstants.PROPERTIES_FILE)) {
            var properties = new Properties();
            properties.load(inputStream);
            return properties.getProperty(ProjectDirConstants.BALLERINA_SHORT_VERSION);
        } catch (Throwable ignore) {
        }
        return UNKNOWN;
    }

    /**
     * Get the ballerina specification version.
     *
     * @return ballerina spec version
     */
    public static String getBallerinaSpecVersion() {
        try (InputStream inputStream = ProjectUtils.class.getResourceAsStream(ProjectDirConstants.PROPERTIES_FILE)) {
            var properties = new Properties();
            properties.load(inputStream);
            return properties.getProperty(ProjectDirConstants.BALLERINA_SPEC_VERSION);
        } catch (Throwable ignore) {
        }
        return UNKNOWN;
    }

    /**
     * Authenticator for the proxy server if provided.
     */
    public static class RemoteAuthenticator extends Authenticator {
        io.ballerina.projects.internal.model.Proxy proxy;
        public RemoteAuthenticator(io.ballerina.projects.internal.model.Proxy proxy) {
            this.proxy = proxy;
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            return (new PasswordAuthentication(this.proxy.username(), this.proxy.password().toCharArray()));
        }
    }
}
