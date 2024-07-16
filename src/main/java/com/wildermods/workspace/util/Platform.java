package com.wildermods.workspace.util;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import org.gradle.internal.impldep.org.apache.commons.lang.NotImplementedException;

import com.wildermods.workspace.util.Platform;

public enum Platform {
		
		steam(() -> {return OS.getSteamDefaultDirectory().resolve("common").resolve("Wildermyth");}),
		epic(unknownPlatformLocation("epic")),
		itch(unknownPlatformLocation("itch")),
		gog(unknownPlatformLocation("gog")),
		filesystem(() -> {return null;});
		
		private Callable<Path> dir;
		
		private Platform (Callable<Path> dir) {
			this.dir = dir;
		}
		
		public Path getDefaultInstallDirectory() throws Exception {
			return dir.call();
		}
		
		public static Platform fromString(String input) {
			for(Platform platform : Platform.values()) {
				if(platform.name().equals(input.toLowerCase())) {
					return platform;
				}
			}
			return filesystem;
		}
		
		public static Callable<Path> unknownPlatformLocation(String platform) {
			return (() -> {throw new NotImplementedException("I don't know where the default install directory for Wildermyth is for the " + platform + " platform. Submit a pull request or input a raw path to the installation location.");});
		}
		
}