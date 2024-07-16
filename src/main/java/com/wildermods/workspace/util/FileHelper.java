package com.wildermods.workspace.util;

import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class FileHelper {

	public static class IgnoreSymbolicVisitor<T> extends SimpleFileVisitor<T> {
		@Override
		public FileVisitResult preVisitDirectory(T t, BasicFileAttributes attrs) {
			if(attrs.isSymbolicLink()) {
				return FileVisitResult.SKIP_SUBTREE;
			}
			return FileVisitResult.CONTINUE;
		}
	}
	
}
