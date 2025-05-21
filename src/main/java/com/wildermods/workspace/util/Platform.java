package com.wildermods.workspace.util;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.NotImplementedException;

import com.wildermods.workspace.util.Platform;

public enum Platform {
		
		steam(() -> {return resolveSteamWildermyth(OS.getSteamDefaultDirectory());}),
		epic(unknownPlatformLocation("epic")),
		itch(unknownPlatformLocation("itch")),
		gog(unknownPlatformLocation("gog")),
		thrixlvault(unknownPlatformLocation("thrixlvault")),
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
		
		public static Path resolveSteamWildermyth(Path dir) {
			return dir.resolve("Steam").resolve("steamapps").resolve("common").resolve("Wildermyth");
		}
		
}