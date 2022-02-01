package com.wildermods.workspace;

import java.io.File;

/**
 * Write rules are used to modify files (or perform other actions) if a file that matches the regex is found when copying.
 */
public abstract class WriteRule {
	
	private final String regex;
	private boolean matched = false;
	
	public WriteRule(String regex) {
		this.regex = regex;
	}
	
	/**
	 * Copies the file, and places it in the destination folder. Possibly modifying the file
	 * or performing other actions.
	 * 
	 * @param installation the current installation that is running
	 * @param source the original file
	 * @param dest the destination to write the new file into
	 * @return
	 */
	public abstract Throwable write(Installation installation, File source, File dest);
	
	/**
	 * Checks if this writerule matches the specified file.
	 * @param source the file to check
	 * @return true if thismatches the specified file
	 */
	public boolean matches(File source) {
		if(source.getAbsolutePath().matches(regex)) {
			System.out.println(source + " matches writerule " + regex);
			matched = true;
			return true;
		}
		return false;
	}
	
	/**
	 * @return true if this writerule matches a file.
	 */
	public boolean matchFound() {
		return matched;
	}
}
