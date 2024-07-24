package com.wildermods.workspace;

import java.nio.file.Path;

import com.wildermods.workspace.util.FileHelper;

public enum GameJars {
	devvotes(FileHelper.libDir, "devvotes-client.jar"),
	gameEngine(FileHelper.libDir, "gameEngine-1.0.jar"),
	server_1_0(FileHelper.libDir, "server-1.0.jar"),
	scratchpad("scratchpad.jar"),
	wildermyth("wildermyth.jar"),
	
	;
	
	private final Path dir;
	private final String name;
	
	private GameJars(Path dir, String name) {
		this.dir = dir;
		this.name = name;
	}
	
	private GameJars(String name) {
		this(FileHelper.mainDir, name);
	}
	
	public Path getDir() {
		return dir;
	}
	
	public Path getPath() {
		return dir.resolve(name);
	}
	
	public static GameJars fromString(String name) {
		for(GameJars jar : values()) {
			if(jar.name.equals(name)) {
				return jar;
			}
			System.out.println(name + "!= " + jar.name);
		}
		return null;
	}
	
	public static GameJars fromPath(Path path) {
		return fromString(path.getFileName().toString());
	}
}
