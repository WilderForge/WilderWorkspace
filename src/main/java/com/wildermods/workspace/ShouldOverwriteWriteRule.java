package com.wildermods.workspace;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

public class ShouldOverwriteWriteRule extends WriteRule {

	final boolean shouldOverwrite;
	
	public ShouldOverwriteWriteRule(boolean shouldOverwrite, String regex) {
		super(regex);
		this.shouldOverwrite = shouldOverwrite;
	}

	@Override
	public Throwable write(File source, File dest) {
		if(shouldOverwrite || !dest.exists()) {
			try {
				System.out.println("Overwriting " + dest);
				FileUtils.copyFile(source, dest);
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
