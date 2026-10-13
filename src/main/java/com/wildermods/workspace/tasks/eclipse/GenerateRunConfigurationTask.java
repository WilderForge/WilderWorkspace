package com.wildermods.workspace.tasks.eclipse;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.work.DisableCachingByDefault;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.wildermods.workspace.tasks.CalculateRuntimeTask;

@DisableCachingByDefault(because = "Projects depend on dynamic classpath")
public class GenerateRunConfigurationTask extends DefaultTask {
    @Input
    public boolean overwrite = false;

    public boolean getOverwrite() { return overwrite; }

    @OutputFile
    public Path getLaunchFile() {
        return getProject().getProjectDir().toPath().resolve(".eclipse/configurations/runClient.launch");
    }

    @InputFile
    public Path getArgsFile() {
        return getProject().getBuildDir().toPath().resolve("runArgs.properties");
    }

    @TaskAction
    public void generateLaunchConfig() throws IOException {
        Path launchFile = getLaunchFile();
        if (Files.exists(launchFile) && !overwrite) {
            getLogger().warn("Launch configuration file already exists and overwrite is false: " + launchFile);
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(getArgsFile())) {
            props.load(in);
        }
        String providers = props.getProperty("providerClasspath", "");
        if (providers.isBlank()) throw new GradleException("Provider classpath is empty; refusing to generate an Eclipse launch using the default project classpath.");
        EclipseModel eclipse = getProject().getExtensions().getByType(EclipseModel.class);
        String projectName = eclipse.getProject().getName();
        String workspace = "${workspace_loc:" + projectName + "}";
        // These paths must be registered for discovery as well as class loading.
        // Keep outputs even before Eclipse compiles fabric.mod.json into them.
        var modPaths = new LinkedHashSet<String>();
        for (String path : props.getProperty("projectOutputClasspath", "").split(Pattern.quote(File.pathSeparator))) {
            if (!path.isBlank()) modPaths.add(path);
        }
        modPaths.add(workspace + File.separator + Path.of("build", "nested-jars"));
        modPaths.add(workspace + File.separator + Path.of("bin", ".wilderworkspace", "processedMods"));
        String addedMods = String.join(File.pathSeparator, modPaths);
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element root = doc.createElement("launchConfiguration");
            root.setAttribute("type", "org.eclipse.jdt.launching.localJavaApplication");
            doc.appendChild(root);
            attribute(root, "booleanAttribute", "org.eclipse.debug.core.ATTR_FORCE_SYSTEM_CONSOLE_ENCODING", "false");
            list(root, "org.eclipse.debug.core.MAPPED_RESOURCE_PATHS", "/" + projectName);
            list(root, "org.eclipse.debug.core.MAPPED_RESOURCE_TYPES", "4");
            attribute(root, "booleanAttribute", "org.eclipse.jdt.launching.ATTR_ATTR_USE_ARGFILE", "false");
            attribute(root, "booleanAttribute", "org.eclipse.jdt.launching.ATTR_SHOW_CODEDETAILS_IN_EXCEPTION_MESSAGES", "true");
            attribute(root, "booleanAttribute", "org.eclipse.jdt.launching.ATTR_USE_CLASSPATH_ONLY_JAR", "false");
            attribute(root, "booleanAttribute", "org.eclipse.jdt.launching.ATTR_USE_START_ON_FIRST_THREAD", "true");
            attribute(root, "booleanAttribute", "org.eclipse.jdt.launching.DEFAULT_CLASSPATH", "false");
            attribute(root, "stringAttribute", "org.eclipse.jdt.launching.MAIN_TYPE", "net.fabricmc.loader.impl.launch.knot.KnotClient");
            attribute(root, "stringAttribute", "org.eclipse.jdt.launching.PROJECT_ATTR", projectName);
            attribute(root, "stringAttribute", "org.eclipse.jdt.launching.VM_ARGUMENTS",
                props.getProperty("vmArgs", "") + " " + CalculateRuntimeTask.quoteVmArgument("-Dfabric.addMods=" + addedMods));
            attribute(root, "stringAttribute", "org.eclipse.jdt.launching.WORKING_DIRECTORY", "${workspace_loc:" + projectName + "/bin}");
            Element classpath = doc.createElement("listAttribute");
            classpath.setAttribute("key", "org.eclipse.jdt.launching.CLASSPATH");
            root.appendChild(classpath);
            String jre = eclipse.getClasspath().getContainers().stream()
                .filter(path -> path.startsWith("org.eclipse.jdt.launching.JRE_CONTAINER"))
                .findFirst().orElse("org.eclipse.jdt.launching.JRE_CONTAINER");
            entry(classpath, memento("containerPath", jre, "4", "1", projectName));
            var paths = new LinkedHashSet<String>();
            for (String path : providers.split(Pattern.quote(File.pathSeparator))) {
                if (!path.isBlank()) paths.add(Path.of(path).toAbsolutePath().normalize().toString());
            }
            for (String path : paths) entry(classpath, memento("externalArchive", path, "2", "3", null));
            Files.createDirectories(launchFile.getParent());
            Files.writeString(launchFile, xml(doc, false));
            getLogger().info("Eclipse run configuration generated at: " + launchFile);
        } catch (javax.xml.parsers.ParserConfigurationException | javax.xml.transform.TransformerException e) {
            throw new IOException("Could not generate Eclipse launch configuration", e);
        }
    }

    private static void attribute(Element parent, String type, String key, String value) {
        Element element = parent.getOwnerDocument().createElement(type);
        element.setAttribute("key", key);
        element.setAttribute("value", value);
        parent.appendChild(element);
    }

    private static void list(Element parent, String key, String value) {
        Element list = parent.getOwnerDocument().createElement("listAttribute");
        list.setAttribute("key", key);
        parent.appendChild(list);
        entry(list, value);
    }

    private static void entry(Element parent, String value) {
        Element entry = parent.getOwnerDocument().createElement("listEntry");
        entry.setAttribute("value", value);
        parent.appendChild(entry);
    }

    private static String memento(String key, String value, String type, String property, String project)
            throws javax.xml.parsers.ParserConfigurationException, javax.xml.transform.TransformerException {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element element = doc.createElement("runtimeClasspathEntry");
        element.setAttribute(key, value);
        element.setAttribute("type", type);
        element.setAttribute("path", property);
        if (project != null) element.setAttribute("javaProject", project);
        doc.appendChild(element);
        return xml(doc, true);
    }

    private static String xml(Document doc, boolean omitDeclaration) throws javax.xml.transform.TransformerException {
        var transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, omitDeclaration ? "yes" : "no");
        transformer.setOutputProperty(OutputKeys.INDENT, omitDeclaration ? "no" : "yes");
        StringWriter result = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(result));
        return result.toString();
    }
}
