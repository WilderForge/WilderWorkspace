package com.wildermods.workspace.tasks;

import java.io.File;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.plugins.ide.eclipse.model.Classpath;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.Library;

public abstract class AddToDevRuntimeClassPathTask extends DefaultTask {

	private File addedDir;
	
	@InputDirectory
	public File getAddedDir() {
		return addedDir;
	}
	
	public void setAddedDir(File addedDir) {
		this.addedDir = addedDir;
	}
	
	@TaskAction
	public void addToClasspath() {
		Project project = getProject();
        project.getPlugins().withId("eclipse", plugin -> {
            project.getExtensions()
                .configure(Classpath.class, classpath -> {
                	File addedDir = getAddedDir();
                    if (addedDir != null && addedDir.exists()) {
                        // Add the directory as a runtime-only classpath entry
                        if (addedDir.exists()) {
                            EclipseModel eclipseModel = project.getExtensions().getByType(EclipseModel.class);

                            eclipseModel.classpath(c -> {
                                c.getFile().whenMerged(model -> {
                                    org.gradle.plugins.ide.eclipse.model.Classpath cp =
                                            (org.gradle.plugins.ide.eclipse.model.Classpath) model;

                                    cp.getEntries().add(new Library(cp.fileReference(addedDir)));
                                });
                            });
                        } else {
                            project.getLogger().lifecycle("Extra directory does not exist: " + addedDir);
                        }
                    }
                });
        });
	}
	
}
