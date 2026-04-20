package com.wildermods.workspace.dependency;

import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gradle.api.Project;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.wildermods.thrixlvault.exception.VersionParsingException;
import com.wildermods.thrixlvault.utils.version.Version;

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

		public final List<Path> locations;
		public final List<String> namePatterns;
		public final List<VersionRule> versionRules;
		
		public FileAlias(List<Path> locations, List<String> namePatterns, List<VersionRule> versionRules) {
			this.locations = locations;
			this.namePatterns = namePatterns;
			this.versionRules = versionRules;
		}
		
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
	
	public static interface VersionRule<T> {
		public Version obtainVersion(T component);
	}
	
	public static class DerivedVersionRule implements VersionRule<String> {

		private final Pattern pattern;
		
		public DerivedVersionRule(String regex) {
			this.pattern = Pattern.compile(regex);
		}
		
		@Override
		public Version obtainVersion(String fileName) {
			Matcher m = pattern.matcher(fileName);
			try {
				return m.find() ? Version.parse(m.group(1)) : null;
			} catch (VersionParsingException e) {
				e.printStackTrace();
				return null;
			}
		}
		
	}
	
	public static class ProjectVersionRule implements VersionRule<Project> {
		
		private final String varName;
		private final Project project;
		
		public ProjectVersionRule(Project project, String varName) {
			this.project = project;
			this.varName = varName;
		}
		
		@Override
		public Version obtainVersion(Project project) {
			try {
				String version = (String) project.findProperty(varName);
				return version != null ? Version.parse(version) : null;
			}
			catch(Exception e) {
				e.printStackTrace();
				return null;
			}
		}
		
	}
	
	public static record LiteralVersionRule(String version) implements VersionRule<Void>{

		@Override
		public Version obtainVersion(Void component) {
			try {
				return Version.parse(version);
			} catch (VersionParsingException e) {
				throw new IllegalStateException(new IllegalArgumentException(version, e));
			}
		}
		
	}
	
}
