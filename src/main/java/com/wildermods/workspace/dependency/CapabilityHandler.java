package com.wildermods.workspace.dependency;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

public class CapabilityHandler {

	public static final JsonObject capabilities;
	static {
		capabilities = JsonParser.parseReader(
			new JsonReader(
				new InputStreamReader(
					CapabilityHandler.class.getResourceAsStream("capabilities.json")
				)
			)
		).getAsJsonObject();
	}
	
	public static class CanonicalModule {
		private final String canonicalKey;
		private final List<Alias> aliases;
		private final List<FileAlias> fileAliases = new ArrayList<>();
		private final List<MavenAlias> mavenAliases = new ArrayList<>();
		
		public CanonicalModule(String canonicalKey) {
			this.canonicalKey = canonicalKey;
		}
		
		public void addAlias(Alias alias) {
			aliases.add(alias);
			if(alias instanceof FileAlias) {
				fileAliases.add((FileAlias) alias);
			}
			else if (alias instanceof MavenAlias) {
				mavenAliases.add((MavenAlias)alias);
			}
		}
		
		public List<Alias> getAliases() {
			return aliases;
		}
		
		public List<FileAlias> getFileAliases() {
			return fileAliases;
		}
		
		public List<MavenAlias> getMavenAliases(){
			return mavenAliases;
		}
		
		public String getCanonicalKey() {
			return canonicalKey;
		}
		
		public String toString() {
			return canonicalKey;
		}
	}
	
	private static final class FileAlias implements Alias {

		public final List<String> locations;
		public final List<String> namePatterns;
		public final List<String> versionRules;
		
		
		@Override
		public boolean matches() {
			// TODO Auto-generated method stub
			return false;
		}
		
	}
	
	public static final class MavenAlias implements Alias {

		@Override
		public boolean matches() {
			// TODO Auto-generated method stub
			return false;
		}
		
	}
	
	private static interface Alias {
		boolean matches();
	}
	
}
