package com.wildermods.workspace;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.apache.commons.io.FileUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class RemoteResource implements Resource {

	public final String name;
	private final URL url;
	private final String destPath;
	private final boolean useBinDir;
	
	public RemoteResource(JsonElement resourceDefinition) throws IOException {
		JsonObject resourceJson = resourceDefinition.getAsJsonObject();
		JsonElement nameEle = resourceJson.get("name");
		JsonElement urlEle = resourceJson.get("url");
		JsonElement destEle = resourceJson.get("dest");
		JsonElement binEle = resourceJson.get("bin");
		
		this.name = nameEle.getAsString();
		this.url = new URL(urlEle.getAsString());
		this.destPath = destEle.getAsString();
		this.useBinDir = destEle.getAsBoolean();
	}
	
	@Override
	public void write(File destDir, boolean binEnabled) throws IOException {
		File dest = new File(destDir, (binEnabled ? "/bin" : "") + destPath);
		if(!dest.exists()) {
			System.out.println("Downloading " + name + ":\nFrom: " + url + "\nInto: " + dest);
			FileUtils.copyInputStreamToFile(url.openStream(), dest);
		}
		else {
			System.out.println("Resource already exists: " + dest);
		}
	}

}
