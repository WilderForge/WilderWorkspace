package com.wildermods.workspace;

import java.io.File;
import java.io.IOException;

public abstract class WriteRule {
	
	private final String regex;
	
	public WriteRule(String regex) {
		this.regex = regex;
	}
	
	public abstract void write(File source, File dest) throws IOException;
	
	public boolean matches(File source) {
		if(source.getAbsolutePath().matches(regex)) {
			System.out.println(source + " matches writerule " + regex);
			return true;
		}
		return false;
	}
}
