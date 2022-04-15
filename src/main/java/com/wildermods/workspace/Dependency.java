package com.wildermods.workspace;

import java.io.File;
import java.net.URL;

public interface Dependency extends Resource {

	public String getName();
	public String getVersion();
	public URL getURL();
	public File getDest();
	public default boolean isBin() {
		return true;
	}
	
}
