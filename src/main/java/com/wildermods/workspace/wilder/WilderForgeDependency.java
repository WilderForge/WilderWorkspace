package com.wildermods.workspace.wilder;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import com.wildermods.workspace.Dependency;
import com.wildermods.workspace.Resource;

public enum WilderForgeDependency implements Dependency, Resource {
	
	FABRIC_LOADER("Fabric Loader", 
		"@fabricLoaderVersion@", 
		"https://maven.fabricmc.net/net/fabricmc/fabric-loader/@fabricLoaderVersion@/fabric-loader-@fabricLoaderVersion@.jar", 
		"/fabric-loader-@fabricLoaderVersion@.jar"),
	
	WILDER_LOADER("WilderLoader",
		"@wilderLoaderVersion@",
		"https://dl.wildermods.com/?user=WilderForge&?project=wilderloader&?version=@wilderLoaderVersion@&?artifact=wilderloader-@wilderLoaderVersion@.jar",
		"/wilderloader-@wilderLoaderVersion@.jar"),
	
	GAME_PROVIDER("Wildermyth Game Provider",
		"@gameProviderVersion@",
		"https://dl.wildermods.com/?user=WilderForge&?project=wildermythgameprovider&?version=@gameProviderVersion@&?artifact=provider-@gameProviderVersion@.jar",
		"/fabric/provider-@gameProviderVersion@.jar"),
	
	TINY_MAPPINGS_PARSER("Tiny Mappings Parser",
		"@tinyMappingsVersion@",
		"https://maven.fabricmc.net/net/fabricmc/tiny-mappings-parser/@tinyMappingsVersion@/tiny-mappings-parser-@tinyMappingsVersion@.jar",
		"/fabric/tiny-mappings-parser-@tinyMappingsVersion@.jar"
	),
	
	TINY_REMAPPER("Tiny Remapper",
		"@tinyRemapperVersion@",
		"https://maven.fabricmc.net/net/fabricmc/tiny-remapper/@tinyRemapperVersion@/tiny-remapper-@tinyRemapperVersion@.jar",
		"/fabric/tiny-remapper-@tinyRemapperVersion@.jar"
	),
	
	ACCESS_WIDENER("Access Widener",
		"@accessWidenerVersion@",
		"https://maven.fabricmc.net/net/fabricmc/access-widener/@accessWidenerVersion@/access-widener-@accessWidenerVersion@.jar",
		"/fabric/access-widener-@accessWidenerVersion@.jar"
	),
	
	ASM("ASM",
		"@asmVersion@",
		"https://repo.maven.apache.org/maven2/org/ow2/asm/asm/@asmVersion@/asm-@asmVersion@.jar",
		"/fabric/asm-@asmVersion@.jar"
	),
	
	ASM_ANALYSIS("ASM Analysis",
		"@asmVersion@",
		"https://repo.maven.apache.org/maven2/org/ow2/asm/asm-analysis/@asmVersion@/asm-analysis-@asmVersion@.jar",
		"/fabric/asm-analysis-@asmVersion@.jar"
	),
	
	ASM_COMMONS("ASM Commons",
		"@asmVersion@",
		"https://repo.maven.apache.org/maven2/org/ow2/asm/asm-commons/@asmVersion@/asm-commons-@asmVersion@.jar",
		"/fabric/asm-commons-@asmVersion@.jar"
	),
	
	ASM_TREE("ASM Tree",
		"@asmVersion@",
		"https://repo.maven.apache.org/maven2/org/ow2/asm/asm-tree/@asmVersion@/asm-tree-@asmVersion@.jar",
		"/fabric/asm-tree-@asmVersion@.jar"
	),
	
	ASM_UTIL("ASM Util",
		"@asmVersion@",
		"https://repo.maven.apache.org/maven2/org/ow2/asm/asm-util/@asmVersion@/asm-util-@asmVersion@.jar",
		"/fabric/asm-util-@asmVersion@.jar"
	),
	
	MIXIN("Spongepowered Mixin",
		"@mixinVersion@",
		"https://repo.spongepowered.org/repository/maven-public/org/spongepowered/mixin/@mixinVersion@/mixin-@mixinVersion@.jar",
		"/fabric/mixin-@mixinVersion@.jar"
	),
	
	GUAVA("Guava",
		"@guavaVersion@",
		"https://repo.maven.apache.org/maven2/com/google/guava/guava/@guavaVersion@/guava-@guavaVersion@.jar",
		"/fabric/guava-@guavaVersion@.jar"
	),
	
	GSON("gson",
		"@gsonVersion@",
		"https://repo.maven.apache.org/maven2/com/google/code/gson/gson/@gsonVersion@/gson-@gsonVersion@.jar",
		"/fabric/gson-@gsonVersion@.jar"
	)
	
	;

	private final String name;
	private String version;
	private final String url;
	private final String dest;
	private final boolean isBin;
	private final Pattern obtainVersionRegex;
	
	private static final Pattern versionRegex = Pattern.compile("@.*?@");
	
	private WilderForgeDependency(String name, String version, String url, String dest) {
		this(name, version, url, dest, true);
	}
	
	private WilderForgeDependency(String name, String version, String url, String dest, boolean isBin) {
		this.name = name;
		this.version = version;
		this.url = url;
		this.dest = dest;
		this.isBin = isBin;
		obtainVersionRegex = Pattern.compile(version.replace("@",  "") + " = (?<version>.*)");
	}
	
	@Override
	public String getName() {
		return name;
	}
	
	@Override
	public String getVersion() {
		if(versionRegex.matcher(version).matches()) {
			try {
				String versions = IOUtils.resourceToString("./gradle.properties", Charset.defaultCharset());
				Matcher matcher = obtainVersionRegex.matcher(versions);
				if(matcher.find()) {
					version = matcher.group("version");
					System.out.println("Found version in gradle.properties RESOURCE: " + "");
				}
				else {
					throw new Error("gradle.properties exists, but no version was found for " + name);
				}
			} catch (IOException e) {
				try {
					System.out.println("There was either no gradle.properties in resources, or there was an error reading it. Looking for gradle.properties in devspace.");
					File gradleProperties = new File("./gradle.properties");
					if(gradleProperties.exists()) {
						String versions = Files.readString(gradleProperties.toPath());
						Matcher matcher = obtainVersionRegex.matcher(versions);
						if(matcher.find()) {
							version = matcher.group("version");
							System.out.println("Found version in gradle.properties DEVSPACE: " + "");
						}
						else {
							throw new Error("gradle.properties exists, but no version was found for " + name);
						}
					}
				}
				catch(IOException e2) {
					throw new IOError(e2);
				}
			}
			version = version.substring(version.indexOf('=') + 1).trim();
		}
		return version;
	}

	@Override
	public URL getURL() {
		try {
			URL url = new URL(versionRegex.matcher(this.url).replaceAll(getVersion()));
			if(url.getProtocol().equals("http")) {
				throw new Error("HTTP protocol declared for URL in " + name + ". use HTTPS instead");
			}
			return url;
		} catch (MalformedURLException e) {
			throw new IOError(e);
		}
	}

	@Override
	public File getDest() {
		return new File(versionRegex.matcher(dest).replaceAll(getVersion()));
	}
	
	@Override
	public boolean isBin() {
		return isBin;
	}

	@Override
	public void write(File destDir, boolean binEnabled) throws IOException {
		File dest = new File(destDir, (binEnabled ? "/bin" : "") + getDest());
		if(!dest.exists()) {
			System.out.println("Downloading " + name + ":\nFrom: " + url + "\nInto: " + dest);
			FileUtils.copyInputStreamToFile(getURL().openStream(), dest);
		}
		else {
			System.out.println("Resource already exists: " + dest);
		}
	}

}
