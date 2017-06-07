package it.ethiclab.dotnet;

import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.List;

@Mojo(name = "version")
public class VersionMojo extends AbstractMojo {
	
	/**
     * @since <since-text>
     */
    @Parameter( property = "dotnet.newVersion", defaultValue = "${project.version}", required = false )
    private String newVersion;
	
	@Parameter(defaultValue = "${project}", readonly = true)
	private MavenProject project;

	private static final String av = "[assembly: AssemblyVersion(\"";
	private static final String afv = "[assembly: AssemblyFileVersion(\"";
	private static final String aiv = "[assembly: AssemblyInformationalVersion(\"";
	private static final String suffix = "\")]";
	
	public void execute() throws MojoExecutionException
    {
    	Log log = getLog();
    	try {
    		log.info("VERSION BEGIN");
    		
    		log.info(project.getGroupId());
    		log.info(project.getArtifactId());
    		log.info(project.getVersion());
    		
    		List<String> sources = project.getCompileSourceRoots();
    		for(String s : sources) {
    			log.info("SOURCE ROOT " + s);
    		}
    		
    		log.info("BASE DIR = " + project.getBasedir());
    		
    		try {
				visit(project.getBasedir(), new FileProcessor() {
					
					@Override
					public void onFile(File f) throws IOException {
						processSourceFile(f);
					}
				});
			} catch (IOException e) {
				throw new MojoExecutionException(e.toString(), e);
			}
    		
    		try {
				visit(project.getBasedir(), new FileProcessor() {
					
					@Override
					public void onFile(File f) throws IOException {
						processVersionChangedFile(f);
					}
				});
			} catch (IOException e) {
				throw new MojoExecutionException(e.toString(), e);
			}
    		
    		/**
    		 * TODO: on commit like versions:commit
    		 */
    		try {
				visit(project.getBasedir(), new FileProcessor() {
					
					@Override
					public void onFile(File f) throws IOException {
						processVersionBackupFile(f);
					}
				});
			} catch (IOException e) {
				throw new MojoExecutionException(e.toString(), e);
			}
    		
    	} finally {
    		log.info("SOURCE CHARSET = " + getSourceEncoding());
    		log.info("VERSION END");
    	}
    }

	private void visit(File f, FileProcessor fp) throws IOException {
		if (f.isDirectory()) {
			if (f.getName().compareToIgnoreCase("bin") == 0) {
				return;
			}
			if (f.getName().compareToIgnoreCase("obj") == 0) {
				return;
			}
			if (f.getName().compareToIgnoreCase("target") == 0) {
				return;
			}
			if (f.list() != null) {
				for (String s : f.list()) {
					File ff = Paths.get(f.getAbsolutePath(), s).toFile();
					if (ff.isFile()) {
						fp.onFile(ff);
					} else {
						visit(ff, fp);
					}
				}
			}
		}
	}

	private void processSourceFile(File f) throws IOException {
		if (f.getName().endsWith(".cs")) {
			getLog().info("processing " + f.getAbsolutePath());
			if (hasAssemblyVersionInfo(f))
			{
				changeVersion(f);
			}
		}
	}
	
	private void processVersionBackupFile(File f) throws IOException {
		if (f.getName().endsWith(".versionsBackup")) {
			String fap = f.getAbsolutePath();
			getLog().info("processing " + fap);
			f.delete();
		}
	}
	
	private void processVersionChangedFile(File f) throws IOException {
		if (f.getName().endsWith(".versionsChanged")) {
			String fap = f.getAbsolutePath();
			getLog().info("processing " + fap);
			File ff = new File(fap.substring(0, fap.lastIndexOf('.')));
			f.renameTo(ff);
		}
	}

	private void changeVersion(File f) throws IOException {
		File backup = new File(f.getAbsolutePath() + ".versionsBackup");
		File changed = new File(f.getAbsolutePath() + ".versionsChanged");
		f.renameTo(backup);
		changeVersion(changed, backup);
	}
	
	private String changeVersion(String line, String versionPrefix, String newVersion) {
		String oldVersion = getVersion(line, versionPrefix);
		return line.replace(oldVersion, newVersion);
	}
	
	private String getVersion(String line, String versionPrefix) {
		int ix = line.indexOf(versionPrefix) + versionPrefix.length();
		int fx = line.indexOf(suffix);
		return line.substring(ix, fx);
	}

	private void changeVersion(File f, File backup) throws IOException {
		BOMInputStream is = new BOMInputStream(new FileInputStream(backup));
		BufferedReader br = new BufferedReader(new InputStreamReader(is, getSourceEncoding()));
		OutputStream os = new FileOutputStream(f);
		PrintWriter pw = new PrintWriter(
				new BufferedWriter(
						new OutputStreamWriter(os, getSourceEncoding())
				)
		);
		try
		{
			String line = null;
			ByteOrderMark bom = is.getBOM();
			if (bom != null) {
				os.write(bom.getBytes());
			}
			do {
				line = br.readLine();
				if (line != null) {
					if (hasVersion(line, av)) {
						line = changeVersion(line, av, Utils.getDotNetCanonicalVersion(newVersion));
						pw.println(line);
						getLog().info(line);
					}
					else if (hasVersion(line, afv)) {
						line = changeVersion(line, afv, Utils.getDotNetCanonicalVersion(newVersion));
						pw.println(line);
						getLog().info(line);
					}
					else if (hasVersion(line, aiv)) {
						line = changeVersion(line, aiv, newVersion);
						pw.println(line);
						getLog().info(line);
					} else {
						pw.println(line);
					}
				}
				
			} while (line != null);
			pw.flush();
		}
		finally
		{
			br.close();
			pw.close();
		}
	}

	private String getSourceEncoding() {
		return project.getProperties().getProperty("project.build.sourceEncoding", Charset.defaultCharset().name());
	}

	private boolean hasAssemblyVersionInfo(File f) throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(new BOMInputStream(new FileInputStream(f)), getSourceEncoding()));
		try
		{
			String line = null;
			do {
				line = br.readLine();
				if (line != null) {
					if (hasVersion(line, av)) {
						getLog().info(line);
						return true;
					}
					else if (hasVersion(line, afv)) {
						getLog().info(line);
						return true;
					}
					else if (hasVersion(line, aiv)) {
						getLog().info(line);
						return true;
					}
				}
				
			} while (line != null);
			return false;
		}
		finally
		{
			br.close();
		}
	}
	
	private boolean hasVersion(String line, String versionStart) {
		if (line == null) {
			return false;
		}
		String l = line.trim();
		if (l.length() == 0) {
			return false;
		}
		if (l.startsWith("//")) {
			return false;
		}
		if (l.startsWith("/*")) {
			return false;
		}
		int ix = l.indexOf(versionStart);
		int iy = l.indexOf("//");
		int iz = l.indexOf("/*");
		if (ix >= 0) {
			if (iy < 0 && iz < 0)
				return true;
			if (iy < ix)
				return false;
			if (iz < ix)
				return false;
			return true;
		}
		return false;
	}
}
