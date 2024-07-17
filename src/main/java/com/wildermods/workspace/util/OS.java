package com.wildermods.workspace.util;

import java.nio.file.Path;

import org.apache.commons.lang3.SystemUtils;

public enum OS {

	LINUX,
	WINDOWS,
	MAC,
	UNKNOWN;
	
	private static final OS os = getOS();
	
	public static OS getOS() {
		if(SystemUtils.IS_OS_LINUX) {
			return LINUX;
		}
		if(SystemUtils.IS_OS_WINDOWS) {
			return WINDOWS;
		}
		if(SystemUtils.IS_OS_MAC) {
			return MAC;
		}
		return UNKNOWN;
	}
	
	public Path steamDefaultDirectory() {
		return getSteamDefaultDirectory(this);
	}
	
	public static Path getSteamDefaultDirectory() {
		return getSteamDefaultDirectory(os);
	}
	
	public static Path getSteamDefaultDirectory(OS os) {
		switch(os) {
			case WINDOWS:
				return Path.of(System.getProperty("user.home")).resolve("Program Files (x86)");
			case MAC:
				return Path.of(System.getProperty("user.home")).resolve("Library").resolve("Application Support");
			case LINUX:
			case UNKNOWN: //hope and pray?
			default:
				return Path.of(System.getProperty("user.home")).resolve(".local").resolve("share");
		}
	}
	
}
