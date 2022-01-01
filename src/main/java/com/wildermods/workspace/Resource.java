package com.wildermods.workspace;

import java.io.File;
import java.io.IOException;

public interface Resource {

	public void write(File destDir, boolean binEnabled) throws IOException;
	
}
