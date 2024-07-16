package com.wildermods.workspace.tasks;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import org.apache.commons.io.file.PathUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import com.wildermods.workspace.util.FileHelper.IgnoreSymbolicVisitor;

public class ClearLocalDependenciesTask extends DefaultTask {

	@Input
	private String destDir = getProject().relativePath("bin/wildermyth");
	
	@Input
	private String decompDir = Path.of(destDir).resolve("decomp").toString();
	
	@TaskAction
	public void clearDependencies() throws IOException {
		final Path decompDir = Path.of(this.decompDir).toAbsolutePath().normalize();
		final Path destDir = Path.of(this.destDir).toAbsolutePath().normalize();
		
		delete(decompDir);
		delete(destDir);
	}
	
	private void delete(Path path) throws IOException {
		Files.walkFileTree(path, new IgnoreSymbolicVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) {
				try {
					FileVisitResult supResult = super.preVisitDirectory(path, attrs);
					switch(supResult) {
						case CONTINUE:
							PathUtils.delete(path);
							return FileVisitResult.SKIP_SUBTREE; //no more subtree if it's been deleted
						default:
							return supResult;
					}
				}
				catch(IOException e) {
					throw new RuntimeException(e);
				}
			}
		});
	}
	
	public String getDestDir() {
		return destDir;
	}

	public String getDecompDir() {
		return decompDir;
	}

	public void setDecompDir(String decompDir) {
		this.decompDir = decompDir;
	}

	public void setDestDir(String destDir) {
		this.destDir = destDir;
	}
	
}
