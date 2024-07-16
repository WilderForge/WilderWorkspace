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
	
	public static Path getSteamDefaultDirectory() {
		return getSteamDefaultDirectory(os);
	}
	
	public static Path resolveSteamWildermyth(Path dir) {
		return dir.resolve("Steam").resolve("steamapps").resolve("Wildermyth");
	}
	
	public static Path getSteamDefaultDirectory(OS os) {
		switch(os) {
			case WINDOWS:
				return resolveSteamWildermyth(Path.of(System.getProperty("user.home")).resolve("Program Files (x86)"));
			case MAC:
				return resolveSteamWildermyth(Path.of("~").resolve("Library").resolve("Application Support"));
			case LINUX:
			case UNKNOWN: //hope and pray?
			default:
				return resolveSteamWildermyth(Path.of("~").resolve(".local").resolve("share"));
		}
	}
	
}
