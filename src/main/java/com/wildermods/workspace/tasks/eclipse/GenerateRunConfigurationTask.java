package com.wildermods.workspace.tasks.eclipse;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "This task is a one time workspace setup task and should not be cached")
public class GenerateRunConfigurationTask extends DefaultTask {

	private static final Path NESTED_JARS = Path.of("build").resolve("nested-jars");
	private static final Path PROCESSED_JARS = Path.of("bin").resolve(".wilderworkspace").resolve("processedMods");
	private static final Path DEV_CLASSPATH = Path.of("..").resolve("build").resolve("classes").resolve("java").resolve("main");
	private static final Path DEV_RESOURCEPATH = Path.of("..").resolve("build").resolve("processedResources");
	
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
                <stringAttribute key="org.eclipse.jdt.launching.VM_ARGUMENTS" value="-Dmixin.debug=true -Dfabric.development=true [KNOT_CLASSPATH] -Dfabric.addMods=${workspace_loc:%1$s}[NESTED_JARS][PATH_SEPARATOR]${workspace_loc:%1$s}[PROCESSED_JARS] -Dprovider.dev.classpath=[DEV_CLASSPATH][PATH_SEPARATOR][DEV_RESOURCEPATH]"/>
                <stringAttribute key="org.eclipse.jdt.launching.WORKING_DIRECTORY" value="${workspace_loc:%1$s/bin}"/>
            </launchConfiguration>
            """;
    	xml = xml.replace("[NESTED_JARS]", File.separator + NESTED_JARS.toString());
    	xml = xml.replace("[PATH_SEPARATOR]", File.pathSeparator);
    	xml = xml.replace("[PROCESSED_JARS]", File.separator + PROCESSED_JARS.toString());
    	xml = xml.replace("[DEV_CLASSPATH]", DEV_CLASSPATH.toString() + File.separator);
    	xml = xml.replace("[DEV_RESOURCEPATH]", DEV_RESOURCEPATH.toString() + File.separator);
    	LAUNCH_CONTENT_TEMPLATE = xml;
    }
    public @Input boolean overwrite = false;
    
    public boolean getOverwrite() {
    	return overwrite;
    }
    
	@OutputFile
	public Path getLaunchFile() {
		return getProject().getProjectDir().toPath().resolve(".eclipse/configurations/runClient.launch");
	}

	@TaskAction
    public void generateLaunchConfig() {
        Path launchFile = getLaunchFile();

        String projectName = getProject().getName();

        // Check if the file exists and if overwrite is allowed
        if (launchFile.toFile().exists() && !overwrite) {
            getProject().getLogger().warn("Launch configuration file already exists and overwrite is false: " + launchFile);
            return;
        }

        String launchContent = String.format(LAUNCH_CONTENT_TEMPLATE, projectName);
        String knotClasspath = "";
        if(getProject().getExtensions().getExtraProperties().has("knotClasspath")) {
        	knotClasspath = (String) getProject().getExtensions().getExtraProperties().get("knotClasspath");
        }
        getProject().getLogger().info("Knot Classpath: " + knotClasspath);
        if(!knotClasspath.isEmpty()) {
        	knotClasspath = "-Dknot.class.path=" + knotClasspath;
        }
        launchContent = launchContent.replace("[KNOT_CLASSPATH]", knotClasspath);
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(launchFile.toFile()))) {
            writer.write(launchContent);
            getProject().getLogger().info("Eclipse run configuration generated at: " + launchFile.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}