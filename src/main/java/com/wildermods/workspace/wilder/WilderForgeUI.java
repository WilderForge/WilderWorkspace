package com.wildermods.workspace.wilder;

import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.text.DefaultCaret;

import com.wildermods.workspace.Installation;
import com.wildermods.workspace.Main;
import com.wildermods.workspace.UI;
import com.wildermods.workspace.gui.SmartScroller;

import javax.swing.JButton;
import javax.swing.JSeparator;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;

import java.awt.Color;

import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.JRadioButton;
import javax.swing.SwingConstants;

public class WilderForgeUI implements UI<WilderInstallationProperties, WildermythGameInfo>, WilderInstallationProperties {
	
	static final File defaultRootInstallation;
	static {
		String os = System.getProperty("os.name").toLowerCase();
		if(os != null && os.contains("windows")) {
			defaultRootInstallation = new File(new File(".").toPath().getRoot().toAbsolutePath().toString() + "/Program Files/Steam/steamapps/common/Wildermyth");
		}
		else {
			defaultRootInstallation = new File(System.getProperty("user.home") + "/.local/share/Steam/steamapps/common/Wildermyth");
		}
	}

	private JFrame frame;
	private JTextField destinationDirectory;
	private JCheckBox copyModsFolder;
	private JCheckBox copySaveData;
	private JCheckBox forceCopy;
	private JTextField sourceDirectory;
	private JScrollPane scrollPane;
	private JTextArea textArea;
	private JCheckBox createGradleWorkspace;
	//private JRadioButton rdbtnAdvancedDecomposition;
	private JButton createButton;
	private SmartScroller xSmartScroller;
	private SmartScroller ySmartScroller;
	private JCheckBox overwriteModsFolder;
	private JCheckBox overwriteSaves;
	private JCheckBox overwriteCoreGame;
	private JRadioButton nuke;
	private JCheckBox decompile;

	/**
	 * Launch the UI.
	 */
	public WilderForgeUI (String[] args) {
		preCheck();
		initialize();
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					WilderForgeUI window = WilderForgeUI.this;
					window.frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	public void preCheck() {
	    String version = System.getProperty("java.version");
	    if(version.startsWith("1.")) {
	        version = version.substring(2, 3);
	    } else {
	        int dot = version.indexOf(".");
	        if(dot != -1) { version = version.substring(0, dot); }
	    } 
	    int versionNo = Integer.parseInt(version);
	    int requiredVersion = Integer.parseInt(WilderForgeDependency.JAVA.getVersion());
	    if(versionNo < 17) {
	    	JOptionPane.showMessageDialog(null, "WilderWorkspace can only run on Java " + requiredVersion+ " or later. You are currently running Java " + System.getProperty("java.version") + ". \n\nRe-run this jar in a Java " + requiredVersion + " environment.");
	    	System.exit(-1);
	    }
	}

	/**
	 * Initialize the contents of the frame.
	 */
	public void initialize() {
		frame = new JFrame();
		frame.setTitle("Wilderforge Installer");
		frame.setBounds(100, 100, 850, 600);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().setLayout(null);
		frame.setResizable(false);
		
		JLabel lblUnmoddedDirectory = new JLabel("Source Directory:");
		lblUnmoddedDirectory.setToolTipText("The directory of your unmodified wildermyth installation. Usually in your steam folder.");
		lblUnmoddedDirectory.setBounds(12, 12, 165, 15);
		frame.getContentPane().add(lblUnmoddedDirectory);
		
		sourceDirectory = new JTextField();
		sourceDirectory.setToolTipText("The directory of your unmodified wildermyth installation. Usually in your steam folder.");
		if(defaultRootInstallation.exists()) {
			sourceDirectory.setText(defaultRootInstallation.getAbsolutePath());
		}
		sourceDirectory.setBounds(171, 10, 538, 19);
		frame.getContentPane().add(sourceDirectory);
		sourceDirectory.setColumns(10);
		
		JButton btnChangeUnmodded = new JButton("Change");
		btnChangeUnmodded.setToolTipText("Change where wilderforge will copy the game files from");
		btnChangeUnmodded.addActionListener(listener -> {
			frame.setEnabled(false);
			JFileChooser fileChooser = new JFileChooser();
			if(defaultRootInstallation.exists()) {
				fileChooser.setCurrentDirectory(defaultRootInstallation);
			}
			fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			fileChooser.setAcceptAllFileFilterUsed(true);
			fileChooser.setDialogTitle("Select default game installation");
			fileChooser.showOpenDialog(frame);
			File file = fileChooser.getSelectedFile();
			if(file != null) {
				sourceDirectory.setText(file.getAbsolutePath());
			}
			else {
				sourceDirectory.setText(null);
			}
			updateState();
			frame.setEnabled(true);
		});
		btnChangeUnmodded.setBounds(721, 10, 117, 19);
		frame.getContentPane().add(btnChangeUnmodded);
		
		JLabel lblModdedDirectory = new JLabel("Destination Directory:");
		lblModdedDirectory.setToolTipText("Where your new modded game instance will be located.");
		lblModdedDirectory.setBounds(12, 37, 165, 15);
		frame.getContentPane().add(lblModdedDirectory);
		
		destinationDirectory = new JTextField();
		destinationDirectory.setToolTipText("Where your new modded game instance will be located.");
		destinationDirectory.setBounds(171, 35, 538, 19);
		frame.getContentPane().add(destinationDirectory);
		destinationDirectory.setColumns(10);
		
		JButton btnChangeModded = new JButton("Change");
		btnChangeModded.setToolTipText("Change where your new Wildermyth game instance will be located.");
		btnChangeModded.addActionListener(listener -> {
			frame.setEnabled(false);
			JFileChooser fileChooser = new JFileChooser();
			if(defaultRootInstallation.exists()) {
				fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
			}
			fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			fileChooser.setAcceptAllFileFilterUsed(true);
			fileChooser.setDialogTitle("Select default game installation");
			fileChooser.showOpenDialog(frame);
			File file = fileChooser.getSelectedFile();
			if(file != null) {
				destinationDirectory.setText(file.getAbsolutePath());
			}
			else {
				destinationDirectory.setText(null);
			}
			updateState();
			frame.setEnabled(true);
		});
		btnChangeModded.setBounds(721, 35, 117, 19);
		frame.getContentPane().add(btnChangeModded);
		
		JSeparator separator = new JSeparator();
		separator.setBounds(12, 64, 826, 2);
		frame.getContentPane().add(separator);
		
		copyModsFolder = new JCheckBox("Copy Mods Folder");
		copyModsFolder.setToolTipText("Copy all files located in the mods folder into your new game instance.");
		copyModsFolder.setBounds(12, 74, 165, 23);
		frame.getContentPane().add(copyModsFolder);
		
		copySaveData = new JCheckBox("Copy Save Data");
		copySaveData.setToolTipText("Copy all save data and backups into your new game instance.");
		copySaveData.setBounds(12, 99, 165, 23);
		frame.getContentPane().add(copySaveData);
		
		JButton btnHelp = new JButton("Help");
		btnHelp.setBounds(721, 73, 117, 25);
		frame.getContentPane().add(btnHelp);
		
		createButton = new JButton("Create New Game Instance");
		createButton.setBounds(12, 130, 826, 71);
		createButton.addActionListener((listener) -> {
			if (updateState()) {
				try {
					Main.install(this);
				} catch (InterruptedException e) {
					throw new Error(e);
				}
			}
		});
		frame.getContentPane().add(createButton);
		
		scrollPane = new JScrollPane();
		scrollPane.setBounds(12, 210, 826, 239);
		frame.getContentPane().add(scrollPane);
		
		xSmartScroller = new SmartScroller(scrollPane, SmartScroller.HORIZONTAL, SmartScroller.START);
		ySmartScroller = new SmartScroller(scrollPane, SmartScroller.VERTICAL, SmartScroller.END);
		
		textArea = new JTextArea();
		scrollPane.setViewportView(textArea);
		textArea.setTabSize(4);
		textArea.setEditable(false);
		DefaultCaret  caret = (DefaultCaret) textArea.getCaret();
		caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
		
		JSeparator separator_1 = new JSeparator();
		separator_1.setBounds(16, 475, 822, 2);
		frame.getContentPane().add(separator_1);
		
		JLabel lblforJavaDevelopers = new JLabel("Warning: The settings below are for coremod developers only!");
		lblforJavaDevelopers.setBackground(Color.GRAY);
		lblforJavaDevelopers.setForeground(UIManager.getColor("OptionPane.errorDialog.border.background"));
		lblforJavaDevelopers.setBounds(12, 489, 466, 23);
		lblforJavaDevelopers.setOpaque(true);
		frame.getContentPane().add(lblforJavaDevelopers);
		
		createGradleWorkspace = new JCheckBox("Create Gradle Workspace");
		createGradleWorkspace.setBackground(Color.GRAY);
		createGradleWorkspace.setToolTipText("Creates a gradle workspace in the destination directory.");
		createGradleWorkspace.setForeground(UIManager.getColor("OptionPane.errorDialog.border.background"));
		createGradleWorkspace.setBounds(12, 512, 207, 23);
		frame.getContentPane().add(createGradleWorkspace);
		
		forceCopy = new JCheckBox("Force Copy");
		forceCopy.setEnabled(false);
		forceCopy.setBackground(Color.GRAY);
		forceCopy.setForeground(Color.YELLOW);
		forceCopy.setBounds(580, 99, 129, 23);
		frame.getContentPane().add(forceCopy);
		
		overwriteSaves = new JCheckBox("Overwrite Saves");
		overwriteSaves.setBackground(Color.GRAY);
		overwriteSaves.setForeground(UIManager.getColor("OptionPane.errorDialog.border.background"));
		overwriteSaves.setBounds(183, 99, 156, 23);
		frame.getContentPane().add(overwriteSaves);
		
		overwriteModsFolder = new JCheckBox("Overwrite Mods");
		overwriteModsFolder.setForeground(UIManager.getColor("OptionPane.errorDialog.border.background"));
		overwriteModsFolder.setBackground(Color.GRAY);
		overwriteModsFolder.setBounds(183, 74, 156, 23);
		frame.getContentPane().add(overwriteModsFolder);
		
		overwriteCoreGame = new JCheckBox("Overwrite Core Game Files");
		overwriteCoreGame.setForeground(UIManager.getColor("OptionPane.errorDialog.border.background"));
		overwriteCoreGame.setBackground(Color.GRAY);
		overwriteCoreGame.setBounds(343, 74, 231, 23);
		frame.getContentPane().add(overwriteCoreGame);
		
		
		nuke = new JRadioButton("*NUKE Destination Directory*");
		nuke.setEnabled(false);
		nuke.setBackground(UIManager.getColor("OptionPane.errorDialog.titlePane.background"));
		nuke.setForeground(Color.RED);
		nuke.setHorizontalAlignment(SwingConstants.CENTER);
		nuke.setBounds(343, 99, 233, 23);
		frame.getContentPane().add(nuke);
		
		decompile = new JCheckBox("Decompile classes");
		decompile.setEnabled(false);
		decompile.setForeground(UIManager.getColor("OptionPane.errorDialog.border.background"));
		decompile.setBackground(Color.GRAY);
		decompile.setBounds(219, 512, 259, 23);
		frame.getContentPane().add(decompile);
		
		
		/*
		rdbtnAdvancedDecomposition = new JRadioButton("Advanced Decompilation");
		rdbtnAdvancedDecomposition.setToolTipText("Custom decomp settings");
		rdbtnAdvancedDecomposition.setEnabled(false);
		rdbtnAdvancedDecomposition.setForeground(Color.RED);
		rdbtnAdvancedDecomposition.setBounds(51, 532, 228, 23);
		frame.getContentPane().add(rdbtnAdvancedDecomposition);
		*/
		
		updateState();
		Thread t = new Thread(() -> {
			try {
				while(true) {
					Thread.sleep(1500);
					EventQueue.invokeLater(() -> updateState());
				}
			}
			catch(InterruptedException e) {}
		});
		t.setName("GUI Update Thread");
		t.setDaemon(true);
		t.start();
	}
	
	private boolean updateState() {
		
		String text = textArea.getText();
		
		StringBuilder s = new StringBuilder();
		String[] errors = checkErrors();
		String[] warnings = checkWarnings();
		String[] checkInfo = checkInfo();
		if(errors.length != 0) {
			createButton.setEnabled(false);
			append(s, 0, (errors.length > 1 ? "There are " + errors.length + " errors" : "There is " + errors.length + " error") + " that must resolved before installation can begin:");
			for(String e : errors) {
				append(s, 1, e);
			}
			s.append('\n');
		}
		else {
			createButton.setEnabled(true);
		}
		
		if(warnings.length != 0) {
			append(s, 0, (warnings.length > 1 ? "WARNING - there are " + warnings.length + " warnings: " : "WARNING:"));
			for(String w : warnings) {
				append(s, 1, w);
			}
			s.append('\n');
		}
		
		textArea.setText(s.toString());
		
		return errors.length == 0 && text.equals(textArea.getText()); // return false if there is an error or if the ui has updated since the user clicked
	}
	
	public String[] checkErrors() {
		List<String> errors = new ArrayList<String>();
		
		File unmoddedDir = getFile(sourceDirectory.getText());
		File newInstanceDir = getFile(destinationDirectory.getText());
		boolean invalidSourceDir = false;
		boolean invalidDestDir = false;
		
		if(unmoddedDir == null) {
			errors.add("* The source directory has not been selected. This is usually * /Steam/steamapps/common/Wildermyth" );
		}
		else {
			try {
				unmoddedDir.toPath();
			}
			catch (InvalidPathException e) {
				invalidSourceDir = true;
				errors.add("* The source directory is invalid: " + e.getMessage());
			}
			
			if(newInstanceDir == null) {
				errors.add("* The destination directory has not been selected.");
			}
			else if (newInstanceDir.exists()) {
				try {
					newInstanceDir.getPath();
				}
				catch(InvalidPathException e) {
					invalidDestDir = true;
					errors.add("The destination directory is invalid: " + e.getMessage());
				}
				if(!invalidDestDir) {
					if(!newInstanceDir.canWrite()) {
						errors.add("* The destination directory is not writable: " + newInstanceDir);
					}
					if(!newInstanceDir.canRead()) {
						errors.add("* The destination directory is not readable: " + newInstanceDir);
					}
					if(newInstanceDir.isFile()) {
						errors.add("* The destination path chosen is a file, not a directory: " + newInstanceDir);
					}
				}
			}
			
			if(!invalidSourceDir && !invalidDestDir) {
				if(!unmoddedDir.exists()) {
					errors.add("* The source directory does not exist: " + unmoddedDir);
				}
				else {
					if (!unmoddedDir.canRead()) {
						errors.add("* The source directory is not readable: " + unmoddedDir);
					}
					if (unmoddedDir.isFile()) {
						errors.add("* The source path chosen is a file, not a directory: " + unmoddedDir);
					}
					
					File versionFile = new File(unmoddedDir + "/version.txt");
					File modsDir = new File(newInstanceDir + "/mods");
					File savesDir = new File(newInstanceDir + "/players");
					
					boolean showForceCopy = false;
					
					if(versionFile != null && !versionFile.exists()) {
						if(!forceCopy.isSelected()) {
							errors.add("* The source path does not appear to be a Wildermyth installation (No version.txt detected). Confirm that the source path is the root directory of your Wildermyth installation.\n\t\t-If the directory is correct, check 'Force Copy' checkbox above to copy anyway.");
						}
						showForceCopy = true;
					}
					
					
					if(newInstanceDir != null) {
						File[] existingFiles = newInstanceDir.listFiles();
						if (newInstanceDir.exists() && existingFiles != null && existingFiles.length > 0) {
							if(!forceCopy.isSelected()) {
								errors.add("* The destination directory is not empty. Check 'Force Copy' checkbox above to copy anyway.");
								errors.add("\t(" +  newInstanceDir.listFiles().length + " files)");
								for(int i = 0; i < existingFiles.length && i < 5; i++) {
									try {
										errors.add("\t\t" + existingFiles[i].getCanonicalPath());
									} catch (IOException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
								}
							}
							showForceCopy = true;
						}
					}
					
					if(showForceCopy) {
						forceCopy.setEnabled(true);
						forceCopy.setVisible(true);
					}
					else {
						forceCopy.setSelected(false);
						forceCopy.setEnabled(false);
						forceCopy.setVisible(false);
					}
					
					if(modsDir != null && copyModsFolder.isSelected() && modsDir.exists() && !nuke.isSelected() && forceCopy.isSelected()) {
						overwriteModsFolder.setEnabled(true);
						overwriteModsFolder.setVisible(true);
					}
					else {
						overwriteModsFolder.setEnabled(false);
						overwriteModsFolder.setSelected(false);
						overwriteModsFolder.setVisible(false);
					}
					
					if(savesDir != null && copySaveData.isSelected() && savesDir.exists() && !nuke.isSelected() && forceCopy.isSelected()) {
						overwriteSaves.setEnabled(true);
						overwriteSaves.setVisible(true);
					}
					else {
						overwriteSaves.setEnabled(false);
						overwriteSaves.setSelected(false);
						overwriteSaves.setVisible(false);
					}
					
				}
			}
		}
		
		boolean noErrors = errors.size() == 0;
		
		overwriteCoreGame.setVisible(noErrors && forceCopy.isEnabled());
		copyModsFolder.setEnabled(noErrors);
		copySaveData.setEnabled(noErrors);
		if(!overwriteCoreGame.isVisible() || nuke.isSelected()) {
			overwriteCoreGame.setSelected(false);
			overwriteCoreGame.setVisible(false);
		}
		
		/*
		if(noErrors && newInstanceDir.exists() && newInstanceDir.listFiles().length > 0) {
			nuke.setEnabled(true);
			nuke.setVisible(true);
		}
		else {*/
			nuke.setVisible(false);
			nuke.setEnabled(false);
			nuke.setSelected(false);
		//}
		
		
		return errors.toArray(new String[]{});
	}
	
	public String[] checkWarnings() {
		List<String> warnings = new ArrayList<String>();
		
		File unmoddedDir = getFile(sourceDirectory.getText());
		File newInstanceDir = getFile(destinationDirectory.getText());
		
		int unmoddedDirLength = 0;
		int newInstanceDirLength = 0;
		
		try {
			if(unmoddedDir != null) {
				unmoddedDirLength = unmoddedDir.toPath().toAbsolutePath().toString().length();
			}
			if(newInstanceDir != null) {
				newInstanceDirLength = newInstanceDir.toPath().toAbsolutePath().toString().length();
			}
		}
		catch(InvalidPathException e) {
			//swallow
		}
		
		
		if(nuke.isSelected()) {
			warnings.add("*** ALL FILES IN " + newInstanceDir + " WILL BE DELETED ***");
			warnings.add("*** ALL FILES IN " + newInstanceDir + " WILL BE DELETED ***");
			warnings.add("*** ALL FILES IN " + newInstanceDir + " WILL BE DELETED ***");
		}
		
		
		if(unmoddedDirLength >= 200) {
			warnings.add("* Source directory is " + unmoddedDirLength + " characters long... Files might not copy over correctly on windows systems, resulting in an unusable instance.");
		}
		else if (newInstanceDirLength >= 200) {
			warnings.add("* Destination directory is " + newInstanceDirLength + " characters long... Files might not copy over correctly on windows systems, resulting in an unusable instance.");
		}
		
		if(overwriteSaves.isSelected()) {
			warnings.add("* Existing save files in destination directory will be overwritten if they exist in the source directory");
		}
		if(overwriteCoreGame.isSelected()) {
			warnings.add("* All core game files (assets & jar files) will be overwritten if they exist in the source directory");
		}
		if(overwriteModsFolder.isSelected()) {
			warnings.add("* Existing files in destination mod directory will be overwritten if they exist in the source directory");
		}
		
		if(forceCopy.isSelected()) {
			if(!new File(unmoddedDir + "/version.txt").exists()) {
				warnings.add("* Source directory may not be a valid Wildermyth installation. User is force copying anyway.");
			}
			if(newInstanceDir.listFiles() != null && newInstanceDir.listFiles().length > 0) {
				warnings.add("* Destination directory is not empty. User is force copying anyway.");
			}
		}
		
		if(createGradleWorkspace.isSelected()) {
			warnings.add("* Installation will include a gradle development environment.");
			decompile.setEnabled(true);
			if(decompile.isSelected()) {
				warnings.add("* Installer will decompile game classes for the gradle environment");
			}
		}
		else {
			decompile.setEnabled(false);
			decompile.setSelected(false);
		}
		
		return warnings.toArray(new String[]{});
	}
	
	public String[] checkInfo() {
		return new String[0];
	}
	
	private StringBuilder append(StringBuilder b, int indent, String s) {
		for(int i = 0; i < indent; i++) {
			b.append('\t');
		}
		b.append(s).append('\n');
		return b;
	}
	
	private File getFile(String path) {
		if(path != null && !path.isEmpty()) {
			return new File(path);
		}
		return null;
	}

	@Override
	public File getSourceDir() {
		return new File(sourceDirectory.getText());
	}

	@Override
	public File getDestDir() {
		return new File(destinationDirectory.getText());
	}

	@Override
	public boolean copySaves() {
		return copySaveData.isSelected();
	}

	@Override
	public boolean copyMods() {
		return copyModsFolder.isSelected();
	}

	@Override
	public boolean overwriteSaves() {
		return overwriteSaves.isSelected();
	}

	@Override
	public boolean overwriteMods() {
		return overwriteModsFolder.isSelected();
	}

	@Override
	public boolean overwriteGame() {
		return overwriteCoreGame.isSelected();
	}

	@Override
	public boolean forceCopy() {
		return forceCopy.isSelected();
	}

	@Override
	public boolean createGradle() {
		return createGradleWorkspace.isSelected();
	}

	@Override
	public boolean nuke() {
		return nuke.isSelected();
	}

	@Override
	public boolean decompile() {
		return decompile.isSelected();
	}

	@Override
	public WildermythGameInfo getGameInfo() {
		return new WildermythGameInfo(getSourceDir());
	}

	@Override
	public boolean isValid() {
		for(String e : checkErrors()) {
			System.err.println(e);
		}
		return checkErrors().length == 0;
	}

	@Override
	public Installation<WilderInstallationProperties, WildermythGameInfo> getInstallation() {
		return new WilderInstallation(this);
	}

}
