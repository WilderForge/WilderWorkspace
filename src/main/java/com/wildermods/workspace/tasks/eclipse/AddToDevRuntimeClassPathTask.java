package com.wildermods.workspace.tasks.eclipse;

import java.io.File;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.plugins.ide.eclipse.model.Classpath;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.Library;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "This task modifies the Eclipse .classpath configuration as a side effect and is not cacheable")
public abstract class AddToDevRuntimeClassPathTask extends DefaultTask {

	private File addedDir;
	
	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
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
