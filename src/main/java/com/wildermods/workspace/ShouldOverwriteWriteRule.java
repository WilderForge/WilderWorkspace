package com.wildermods.workspace;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;


/**
 * If shouldOverwrite is true, then if a file is found at its destination, it will be overwritten
 * otherwise, copying for that file will be skipped.
 */
public class ShouldOverwriteWriteRule extends WriteRule {

	final boolean shouldOverwrite;
	
	public ShouldOverwriteWriteRule(boolean shouldOverwrite, String regex) {
		super(regex);
		this.shouldOverwrite = shouldOverwrite;
	}

	@Override
	public Throwable write(Installation installation, Path source, Path dest) {
		if(shouldOverwrite || !dest.toFile().exists()) {
			try {
				System.out.println("Overwriting " + dest);
				FileUtils.copyFile(source.toFile(), dest.toFile());
			} catch (IOException e) {
				return e;
			}
		}
		else {
			System.out.println("Skipping " + dest + " because it already exists.");
		}
		return null;
	}

}
