package com.wildermods.workspace.wilder;

import static com.wildermods.workspace.Version.NO_VERSION;

import java.io.File;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.util.HashMap;

import com.wildermods.workspace.Installation;
import com.wildermods.workspace.InstallationProperties;
import com.wildermods.workspace.Version;

import static com.wildermods.workspace.wilder.WilderInstallationProperties.Properties.*;

public interface WilderInstallationProperties extends InstallationProperties<WildermythGameInfo> {
	
	public static enum Properties {
		
		sourceDir,
		destDir,
		copySaves,
	 	copyMods,
		overwriteSaves,
		overwriteMods,
		overwriteGame,
		forceCopy,
		createGradle,
		nuke,
		decompile;
		
	}
	
	public File getSourceDir();
	public File getDestDir();
	@Override
	public WildermythGameInfo getGameInfo();
	public default Version getGameVersion() {return getGameInfo().getVersion();};
	public boolean copySaves();
	public boolean copyMods();
	public boolean overwriteSaves();
	public boolean overwriteMods();
	public boolean overwriteGame();
	public boolean forceCopy();
	public boolean createGradle();
	public boolean nuke();
	public boolean decompile();
	
	public default boolean isValid() {
		try {
			
			if(getSourceDir() == null) {
				throw new NullPointerException("No source directory set!");
			}
			else if(getSourceDir().exists()) {
				if(getGameVersion() == NO_VERSION && !forceCopy()) {
					throw new IllegalStateException("Source directory doesn't appear to be a wildermyth installation. Enable 'forceCopy' to copy anyway.");
				}
				if(!getSourceDir().canRead()) {
					throw new FileSystemException("Source directory is not readable: " + getSourceDir());
				}
				else if (!getSourceDir().isDirectory()) {
					throw new NotDirectoryException(getSourceDir().toString());
				}
			}
			else {
				throw new NoSuchFileException(getSourceDir().toString());
			}
			
			if(getDestDir() == null) {
				throw new NullPointerException("No dest directory set!");
			}
			else if(getDestDir().exists()) {
				File[] files = getDestDir().listFiles();
				if(files.length > 0) {
					if(!forceCopy()) {
						throw new IllegalStateException("Destination directory is not empty. Enable 'forceCopy' to copy anyway.");
					}
				}
				else if(!getDestDir().canRead()) {
					throw new FileSystemException("Destination directory is not readable: " + getDestDir());
				}
				else if (!getDestDir().canWrite()) {
					throw new FileSystemException("Destination directory is not writable: " + getDestDir());
				}
				else if (!getDestDir().isDirectory()) {
					throw new NotDirectoryException(getDestDir().toString());
				}
			}
			else {
				if(nuke()) {
					throw new IllegalStateException("Destination directory does not exist. No destination directory to nuke. Disable 'nuke'");
				}
				else if(overwriteSaves()) {
					throw new IllegalStateException("Destination directory does not exist. No saves to overwrite. Disable 'overwriteSaves'");
				}
				else if(overwriteMods()) {
					throw new IllegalStateException("Destination directory does not exist. No mods to overwrite. Disable 'overwriteMods'");
				}
				else if (overwriteGame()) {
					throw new IllegalStateException("Destination directory does not exist. No game files to overwrite. Disable 'overwriteGame'");
				}
			}
			
			if(!createGradle() && decompile()) {
				throw new IllegalStateException("WilderWorkspace will not decompile the files unless you enable gradle workspace creation. Enable 'createGradle'");
			}
			
			return true;
			
		}
		catch(Exception e) {
			e.printStackTrace();
			return false;
		}

	}
	
	public static InstallationProperties fromArgs(String[] args) {
		
		@SuppressWarnings("rawtypes")
		HashMap<Properties, String> properties = new HashMap<Properties, String>();
		
		for(String arg : args) {
			String[] pair = arg.split("=");
			if(pair.length == 2) {
				for(Properties property : Properties.values()) {
					String name = property.name();
					if(pair[0].equals(name)) {
						properties.put(property, pair[1]);
					}
				}
			}
			else {
				System.err.println("Could not interpret argument " + arg);
			}
		}
		
		return new WilderInstallationProperties() {

			@Override
			public File getSourceDir() {
				return handleNull(File.class, sourceDir);
			}

			@Override
			public File getDestDir() {
				return handleNull(File.class, destDir);
			}

			@Override
			public boolean copySaves() {
				return handleNull(boolean.class, copySaves);
			}

			@Override
			public boolean copyMods() {
				return handleNull(boolean.class, copyMods);
			}

			@Override
			public boolean overwriteSaves() {
				return handleNull(boolean.class, overwriteSaves);
			}

			@Override
			public boolean overwriteMods() {
				return handleNull(boolean.class, overwriteMods);
			}

			@Override
			public boolean overwriteGame() {
				return handleNull(boolean.class, overwriteGame);
			}

			@Override
			public boolean forceCopy() {
				return handleNull(boolean.class, forceCopy);
			}

			@Override
			public boolean createGradle() {
				return handleNull(boolean.class, createGradle);
			}

			@Override
			public boolean nuke() {
				return handleNull(boolean.class, nuke);
			}

			@Override
			public boolean decompile() {
				return handleNull(boolean.class, decompile);
			}
			
			@Override
			public WildermythGameInfo getGameInfo() {
				return new WildermythGameInfo(getSourceDir());
			}
			
			private <T> T handleNull(Class<T> type, Properties property) {
				String value = properties.get(property);
				if(value == null) {
					return null;
				}
				else if(type == File.class) {
					return (T) new File(value);
				}
				else if(type == boolean.class || type == Boolean.class) {
					return (T) Boolean.valueOf(value);
				}
				throw new IllegalArgumentException("Don't know how to convert a " + type);
			}

			@Override
			public Installation<WilderInstallationProperties, WildermythGameInfo> getInstallation() {
				return new WilderInstallation(this);
			}

			@Override
			public File getBinDir() {
				if(createGradle()) {
					return new File(getDestDir() + "/bin");
				}
				return getDestDir();
			}
			
		};
		
	}
	
}
