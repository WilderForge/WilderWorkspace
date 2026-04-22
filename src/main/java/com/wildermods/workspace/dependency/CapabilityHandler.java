package com.wildermods.workspace.dependency;

import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gradle.api.Project;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.wildermods.thrixlvault.exception.VersionParsingException;
import com.wildermods.thrixlvault.utils.version.Version;

public class CapabilityHandler {

	private static final Gson gson = new GsonBuilder().create();
	private static final JsonObject capabilities;
	static {
		capabilities = JsonParser.parseReader(
			new JsonReader(
				new InputStreamReader(
					CapabilityHandler.class.getResourceAsStream("capabilities.json")
				)
			)
		).getAsJsonObject();
	}
	
	public static class CanonicalModule implements Moduled {
		private final Project project;
		private final String canonicalKey;
		private final List<AliasContainer<?>> aliases = new ArrayList<>();
		private final List<FileAlias> fileAliases = new ArrayList<>();
		private final List<MavenAlias> mavenAliases = new ArrayList<>();
		
		public CanonicalModule(Project project, String canonicalKey) {
			this.canonicalKey = canonicalKey;
			this.project = project;
		}
		
		public void addAlias(AliasContainer<?> alias) {
			aliases.add(alias);
			if(alias instanceof FileAlias) {
				fileAliases.add((FileAlias) alias);
			}
			else if (alias instanceof MavenAlias) {
				mavenAliases.add((MavenAlias)alias);
			}
		}
		
		public List<AliasContainer<?>> getAliases() {
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

		@Override
		public CanonicalModule getCanonicalModule() {
			return this;
		}
	
		private final class FileAlias implements AliasContainer<FileAlias.ConcreteFileAlias> {
	
			public final List<ConcreteFileAlias> aliases;
			
			public FileAlias(JsonObject json) {
				String type;
				Path[] locs;
				String[] names;
				
				try {
					type = json.getAsJsonPrimitive("type").toString();
				}
				catch(Exception e) {
					throw new CapabilityDefinitionError("Unable to parse type", e);
				}
				
				if("file".equals(type)) {
					try {
						List<JsonElement> locations = json.getAsJsonArray("location").asList();
						locs = new Path[locations.size()];
						for(int i = 0; i < locations.size(); i++) {
							Path dir;
							
							try {
								locs[i] = Path.of(locations.get(i).getAsJsonPrimitive().toString());
							}
							catch(Exception e) {
								throw new CapabilityDefinitionError("Unable to parse location primitive");
							}
						}
					}
					catch(Exception e) {
						throw new CapabilityDefinitionError("Unable to parse location array", e);
					}
				}
				else {
					throw new CapabilityDefinitionError("Unknown type: " + type);
				}
				
				
			}
			
			public class ConcreteFileAlias implements ConcreteAlias<ConcreteFileAlias> {
	
				private final Path file;
				private final VersionRule rule;
				
				public ConcreteFileAlias(Path path, VersionRule rule) {
					this.file = path;
					this.rule = rule;
				}
				
				@Override
				public boolean matches() {
					if(Files.exists(file)) {
						if(Files.isRegularFile(file)) {
							if(rule.obtainVersion() != null) {
								return true;
							}
						}
					}
					return false;
				}

				@Override
				public CanonicalModule getCanonicalModule() {
					return CanonicalModule.this;
				}
				
				public class DerivedVersionRule implements VersionRule {

					private final Pattern pattern;
					
					public DerivedVersionRule(String regex) {
						this.pattern = Pattern.compile(regex);
					}
					
					@Override
					public Version obtainVersion() {
						Matcher m = pattern.matcher(file.getFileName().toString());
						try {
							return m.find() ? Version.parse(m.group(1)) : null;
						} catch (VersionParsingException e) {
							e.printStackTrace();
							return null;
						}
					}
					
				}
				
				public class ProjectVersionRule implements VersionRule{
					
					private final String varName;
					
					public ProjectVersionRule(String varName) {
						this.varName = varName;
					}
					
					@Override
					public Version obtainVersion() {
						try {
							String version = (String) CanonicalModule.this.project.findProperty(varName);
							return version != null ? Version.parse(version) : null;
						}
						catch(Exception e) {
							e.printStackTrace();
							return null;
						}
					}
					
				}
				
			}
	
			@Override
			public Iterator<ConcreteAlias<ConcreteFileAlias>> iterator() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public CanonicalModule getCanonicalModule() {
				return CanonicalModule.this;
			}
			
		}
		
		public final class MavenAlias implements AliasContainer<MavenAlias.ConcreteMavenAlias> {
			
			public class ConcreteMavenAlias implements ConcreteAlias<ConcreteMavenAlias> {
	
				@Override
				public boolean matches() {
					// TODO Auto-generated method stub
					return false;
				}

				@Override
				public CanonicalModule getCanonicalModule() {
					return CanonicalModule.this;
				}
				
			}
	
			@Override
			public Iterator<ConcreteAlias<ConcreteMavenAlias>> iterator() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public CanonicalModule getCanonicalModule() {
				return CanonicalModule.this;
			}
			
		}
	
	}
	
	private static interface AliasContainer<T extends ConcreteAlias<T>> extends Iterable<ConcreteAlias<T>>, Moduled {
		public default String getCanonicalKey() {
			return Moduled.super.getCanonicalKey();
		}
	}
	
	private static interface ConcreteAlias<T> extends Moduled {
		public boolean matches();
	}
	
	public static interface Moduled {
		public CanonicalModule getCanonicalModule();
		
		public default String getCanonicalKey() {
			return getCanonicalModule().canonicalKey;
		}
	}
	
	public static interface VersionRule {
		public Version obtainVersion();
	}
	
	public static record LiteralVersionRule(String version) implements VersionRule {

		@Override
		public Version obtainVersion() {
			try {
				return Version.parse(version);
			} catch (VersionParsingException e) {
				throw new IllegalStateException(new IllegalArgumentException(version, e));
			}
		}
		
	}
	
	public static record AssertVersionRule(String message) implements VersionRule {
		
		@Override
		public Version obtainVersion() {
			throw new AssertionError(message);
		}
		
	}
	
}
