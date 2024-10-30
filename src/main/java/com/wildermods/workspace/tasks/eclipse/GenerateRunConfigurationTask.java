package com.wildermods.workspace.tasks.eclipse;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public class GenerateRunConfigurationTask extends DefaultTask {

    private static final String LAUNCH_CONTENT_TEMPLATE = 
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
                <stringAttribute key="org.eclipse.jdt.launching.VM_ARGUMENTS" value="-Dmixin.debug=true -Dfabric.development=true"/>
                <stringAttribute key="org.eclipse.jdt.launching.WORKING_DIRECTORY" value="${workspace_loc:%1$s/bin}"/>
            </launchConfiguration>
            """;
    
    public @Input boolean overwrite = false;
    
    public boolean getOverwrite() {
    	return overwrite;
    }
    
	@OutputFile
	public Path getLaunchFile() {
		return getProject().getProjectDir().toPath().resolve(".eclipse/configurations/runClient.launch");
	}

	@TaskAction
    private void generateLaunchConfig() {
        Path launchFile = getLaunchFile();

        String projectName = getProject().getName();

        // Check if the file exists and if overwrite is allowed
        if (launchFile.toFile().exists() && !overwrite) {
            System.out.println("Launch configuration file already exists and overwrite is false: " + launchFile);
            return;
        }

        String launchContent = String.format(LAUNCH_CONTENT_TEMPLATE, projectName);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(launchFile.toFile()))) {
            writer.write(launchContent);
            System.out.println("Eclipse run configuration generated at: " + launchFile.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}