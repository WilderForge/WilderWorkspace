package com.wildermods.workspace.util;

import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Utility class for file operations within the WilderWorkspace plugin.
 * <p>
 * This class provides static methods and nested classes to assist with common file operations, such as determining
 * relative paths and handling symbolic links during file traversal.
 * </p>
 */
public class FileHelper {

	public static final Path mainDir = Path.of("");
	public static final Path libDir = mainDir.resolve("lib");
	
	public static class IgnoreSymbolicVisitor<T> extends SimpleFileVisitor<T> {
		@Override
		public FileVisitResult preVisitDirectory(T t, BasicFileAttributes attrs) {
			if(attrs.isSymbolicLink()) {
				return FileVisitResult.SKIP_SUBTREE;
			}
			return FileVisitResult.CONTINUE;
		}
	}
	
	public static Path relativePath(Path parent, Path child) {
		parent = parent.normalize().toAbsolutePath();
		child = child.normalize().toAbsolutePath();
		if(!child.startsWith(parent)) {
			throw new IllegalArgumentException("Child path not a subpath of parent!");
		}
		return parent.relativize(child);
	}
	
	public static boolean shouldBeRemapped(Path file) {
		if(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
			switch(file.getFileName().toString()) {
				case "devvotes-client.jar":
				case "gameEngine-1.0.jar":
				case "server-1.0.jar":
				case "scratchpad.jar":
				case "wildermyth.jar":
					return true;
			}
		}
		return false;
	}
	
}
