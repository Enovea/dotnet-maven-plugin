package it.ethiclab.dotnet;

import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "init", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class InitMojo extends AbstractMojo {
	@Parameter(defaultValue = "${project}", readonly = true)
	private MavenProject project;

	public void execute() throws MojoExecutionException
    {
    	Log log = getLog();
    	try {
    		log.info("INIT BEGIN");
    		
    		log.info(project.getGroupId());
    		log.info(project.getArtifactId());
    		log.info(project.getVersion());
    		
    		List<String> sources = project.getCompileSourceRoots();
    		for(String s : sources) {
    			log.info("SOURCE ROOT " + s);
    		}
    		
    		log.info("BASE DIR = " + project.getBasedir());
    	} finally {
    		log.info("INIT END");
    	}
    }
}
