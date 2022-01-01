package com.wildermods.workspace;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.MissingResourceException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

public class LocalResource implements Resource {

	private final InputStream inputstream;
	private final String resourceLocation;
	private final String destPath;
	private final boolean useBinDir;
	
	public LocalResource(String path, boolean useBinDir) {
		this(path, path, useBinDir);
	}
	
	public LocalResource(String resourceLocation, String destPath, boolean useBinDir) {
		this.resourceLocation = resourceLocation;
		this.destPath = destPath;
		this.useBinDir = useBinDir;
		InputStream stream = LocalResource.class.getResourceAsStream(resourceLocation);
		if(stream == null) { //If we're being executed from a dir not a jar
			stream = LocalResource.class.getResourceAsStream("/" + resourceLocation);
		}
		this.inputstream = stream;
		if(inputstream == null) {
			throw new MissingResourceException("Could not find resource " + resourceLocation, LocalResource.class.getName(), resourceLocation);
		}
	}
	
	/*
	 * Note that a resource can only be written once.
	 */
	@SuppressWarnings("deprecation")
	public void write(File destDir, boolean binEnabled) throws IOException {
		File dest = new File(destDir.getAbsolutePath() + "/" + destPath);
		if(!dest.exists()) {
			System.out.println("writing " + dest);
			FileUtils.writeByteArrayToFile(dest, IOUtils.toByteArray(inputstream), false);
		}
		else {
			System.out.println("Resource already exists: " + dest);
		}
	}

}
