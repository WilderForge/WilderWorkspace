package com.wildermods.workspace.tasks.eclipse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

@DisableCachingByDefault(because = "This task is a one time workspace setup task and should not be cached")
public class GenerateRunConfigurationTask extends DefaultTask {

    private static final Path NESTED_JARS = Path.of("build").resolve("nested-jars");
    private static final Path PROCESSED_JARS = Path.of("bin").resolve(".wilderworkspace").resolve("processedMods");
    private static final Gson GSON = new GsonBuilder().create();

    private static final String LAUNCH_CONTENT_TEMPLATE;
    static {
        String xml =
            """
            <?xml version="1.0" encoding="UTF-8" standalone="no"?>
            <launchConfiguration type="org.eclipse.jdt.launching.localJavaApplication">
                <booleanAttribute key="org.eclipse.debug.core.ATTR_FORCE_SYSTEM_CONSOLE_ENCODING" value="false"/>
                <listAttribute key="org.eclipse.debug.core.MAPPED_RESOURCE_PATHS">
                    <listEntry value="/%1$s"/>
                </listAttribute>
                <listAttribute key="org.eclipse.debug.core.MAPPED_RESOURCE_TYPES">
                    <listEntry value="4"/>
                </listAttribute>
                <booleanAttribute key="org.eclipse.jdt.launching.ATTR_ATTR_USE_ARGFILE" value="false"/>
                <booleanAttribute key="org.eclipse.jdt.launching.ATTR_SHOW_CODEDETAILS_IN_EXCEPTION_MESSAGES" value="true"/>
                <booleanAttribute key="org.eclipse.jdt.launching.ATTR_USE_CLASSPATH_ONLY_JAR" value="false"/>
                <booleanAttribute key="org.eclipse.jdt.launching.ATTR_USE_START_ON_FIRST_THREAD" value="true"/>
                <stringAttribute key="org.eclipse.jdt.launching.MAIN_TYPE" value="net.fabricmc.loader.impl.launch.knot.KnotClient"/>
                <stringAttribute key="org.eclipse.jdt.launching.MODULE_NAME" value="%1$s"/>
                <stringAttribute key="org.eclipse.jdt.launching.PROJECT_ATTR" value="%1$s"/>
                <stringAttribute key="org.eclipse.jdt.launching.VM_ARGUMENTS" value="-Dmixin.debug=true -Dfabric.development=true [VM_ARGS] -Dfabric.addMods=${workspace_loc:%1$s}[NESTED_JARS][PATH_SEPARATOR]${workspace_loc:%1$s}[PROCESSED_JARS]"/>
                <stringAttribute key="org.eclipse.jdt.launching.WORKING_DIRECTORY" value="${workspace_loc:%1$s/bin}"/>
            </launchConfiguration>
            """;
        xml = xml.replace("[NESTED_JARS]", File.separator + NESTED_JARS.toString());
        xml = xml.replace("[PATH_SEPARATOR]", File.pathSeparator);
        xml = xml.replace("[PROCESSED_JARS]", File.separator + PROCESSED_JARS.toString());
        LAUNCH_CONTENT_TEMPLATE = xml;
    }

    @Input
    public boolean overwrite = false;

    public boolean getOverwrite() {
        return overwrite;
    }

    @OutputFile
    public Path getLaunchFile() {
        return getProject().getProjectDir().toPath().resolve(".eclipse/configurations/runClient.launch");
    }

    @TaskAction
    public void generateLaunchConfig() throws IOException {
        Path launchFile = getLaunchFile();
        String projectName = getProject().getName();

        if (launchFile.toFile().exists() && !overwrite) {
            getProject().getLogger().warn("Launch configuration file already exists and overwrite is false: " + launchFile);
            return;
        }

        // Build the VM arguments
        String knotClasspath = "";
        if (getProject().getExtensions().getExtraProperties().has("knotClasspath")) {
            knotClasspath = (String) getProject().getExtensions().getExtraProperties().get("knotClasspath");
        }
        getProject().getLogger().info("Knot Classpath: " + knotClasspath);

        String providerClasspath = "";
        if (getProject().getExtensions().getExtraProperties().has("providerClasspath")) {
            providerClasspath = (String) getProject().getExtensions().getExtraProperties().get("providerClasspath");
        }
        getProject().getLogger().info("Provider Classpath: " + providerClasspath);

        Set<String> vmArgs = new LinkedHashSet<>();
        vmArgs.add("-Dmixin.debug=true");
        vmArgs.add("-Dfabric.development=true");

        if (!knotClasspath.isEmpty()) {
            vmArgs.add("-Dknot.class.path=" + knotClasspath);
        }

        // Collect JARs from knotClasspath and providerClasspath
        List<Path> jarPaths = new ArrayList<>();
        if (!knotClasspath.isEmpty()) {
            for (String entry : knotClasspath.split(File.pathSeparator)) {
                Path p = Paths.get(entry);
                if (Files.exists(p) && !Files.isDirectory(p) && p.toString().endsWith(".jar")) {
                    jarPaths.add(p);
                    getProject().getLogger().info("Adding knot JAR for scanning: " + p);
                } else {
                    getProject().getLogger().info("Skipping knot path (not a JAR or missing): " + p);
                }
            }
        }

        if (!providerClasspath.isEmpty()) {
            for (String entry : providerClasspath.split(File.pathSeparator)) {
                Path p = Paths.get(entry);
                if (Files.exists(p) && !Files.isDirectory(p) && p.toString().endsWith(".jar")) {
                    jarPaths.add(p);
                    getProject().getLogger().info("Adding provider JAR for scanning: " + p);
                } else {
                    getProject().getLogger().info("Skipping provider path (not a JAR or missing): " + p);
                }
            }
        }

        // Also check the project's own resources
        Path projectJson = getProject().getProjectDir().toPath().resolve("src/main/resources/fabric.mod.json");
        if (Files.exists(projectJson)) {
            jarPaths.add(projectJson); // special marker
            getProject().getLogger().info("Adding project fabric.mod.json: " + projectJson);
        } else {
            getProject().getLogger().info("Project fabric.mod.json not found at " + projectJson);
        }

        getProject().getLogger().info("Total paths to scan for force entries: " + jarPaths.size());

        Map<String, List<String>> opens = new LinkedHashMap<>();
        Map<String, List<String>> exports = new LinkedHashMap<>();

        for (Path p : jarPaths) {
            // Handle project's own fabric.mod.json directly
            if (p.toString().endsWith("fabric.mod.json") && Files.isRegularFile(p)) {
                getProject().getLogger().info("Reading fabric.mod.json directly: " + p);
                try (Reader reader = Files.newBufferedReader(p)) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    extractForceEntries(json, opens, exports);
                } catch (Exception e) {
                    getProject().getLogger().warn("Failed to parse " + p, e);
                }
                continue;
            }

            // Otherwise it's a JAR
            if (!Files.isRegularFile(p)) continue;
            try (JarFile jar = new JarFile(p.toFile())) {
                ZipEntry entry = jar.getEntry("fabric.mod.json");
                if (entry != null) {
                    getProject().getLogger().info("Found fabric.mod.json in JAR: " + p);
                    try (InputStream is = jar.getInputStream(entry);
                         Reader reader = new InputStreamReader(is)) {
                        JsonObject json = GSON.fromJson(reader, JsonObject.class);
                        extractForceEntries(json, opens, exports);
                    } catch (Exception e) {
                        getProject().getLogger().warn("Failed to parse fabric.mod.json in " + p, e);
                    }
                } else {
                    getProject().getLogger().info("No fabric.mod.json in JAR: " + p);
                }
            } catch (IOException e) {
                getProject().getLogger().warn("Failed to open JAR: " + p, e);
            }
        }

        getProject().getLogger().info("Collected forceOpens entries: " + opens);
        getProject().getLogger().info("Collected forceExports entries: " + exports);

        // Generate VM arguments
        for (Map.Entry<String, List<String>> e : opens.entrySet()) {
            String target = e.getKey();
            for (String pkg : e.getValue()) {
                String[] parts = pkg.split("/", 2);
                if (parts.length == 2) {
                    String arg = "--add-opens " + parts[0] + "/" + parts[1] + "=" + target;
                    vmArgs.add(arg);
                    getProject().getLogger().info("Added VM arg (opens): " + arg);
                } else {
                    getProject().getLogger().warn("Invalid package format in forceOpens: " + pkg);
                }
            }
        }
        for (Map.Entry<String, List<String>> e : exports.entrySet()) {
            String target = e.getKey();
            for (String pkg : e.getValue()) {
                String[] parts = pkg.split("/", 2);
                if (parts.length == 2) {
                    String arg = "--add-exports " + parts[0] + "/" + parts[1] + "=" + target;
                    vmArgs.add(arg);
                    getProject().getLogger().info("Added VM arg (exports): " + arg);
                } else {
                    getProject().getLogger().warn("Invalid package format in forceExports: " + pkg);
                }
            }
        }

        String vmArgsString = String.join(" ", vmArgs);
        getProject().getLogger().info("Final VM arguments: " + vmArgsString);

        String launchContent = String.format(LAUNCH_CONTENT_TEMPLATE, projectName);
        launchContent = launchContent.replace("[VM_ARGS]", vmArgsString);

        // Ensure parent directory exists
        Files.createDirectories(launchFile.getParent());

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(launchFile.toFile()))) {
            writer.write(launchContent);
            getProject().getLogger().info("Eclipse run configuration generated at: " + launchFile.toString());
        } catch (IOException e) {
            getProject().getLogger().error("Failed to write launch configuration", e);
            e.printStackTrace();
        }
    }

    private void extractForceEntries(JsonObject json, Map<String, List<String>> opens, Map<String, List<String>> exports) {
        JsonObject custom = json.getAsJsonObject("custom");
        if (custom == null) {
            getProject().getLogger().info("No 'custom' field in fabric.mod.json");
            return;
        }

        // forceOpens
        JsonObject forceOpens = custom.getAsJsonObject("forceOpens");
        if (forceOpens != null) {
            getProject().getLogger().info("Found forceOpens: " + forceOpens);
            for (String key : forceOpens.keySet()) {
                var packages = forceOpens.getAsJsonArray(key);
                if (packages != null) {
                    List<String> list = opens.computeIfAbsent(key, k -> new ArrayList<>());
                    for (var elem : packages) {
                        list.add(elem.getAsString());
                    }
                }
            }
        } else {
            getProject().getLogger().info("No forceOpens in this mod");
        }

        // forceExports
        JsonObject forceExports = custom.getAsJsonObject("forceExports");
        if (forceExports != null) {
            getProject().getLogger().info("Found forceExports: " + forceExports);
            for (String key : forceExports.keySet()) {
                var packages = forceExports.getAsJsonArray(key);
                if (packages != null) {
                    List<String> list = exports.computeIfAbsent(key, k -> new ArrayList<>());
                    for (var elem : packages) {
                        list.add(elem.getAsString());
                    }
                }
            }
        } else {
            getProject().getLogger().info("No forceExports in this mod");
        }
    }
}