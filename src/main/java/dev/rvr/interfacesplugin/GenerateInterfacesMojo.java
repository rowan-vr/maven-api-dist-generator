package dev.rvr.interfacesplugin;

import net.bytebuddy.ByteBuddy;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.List;

@Mojo(name = "generate-interfaces", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class GenerateInterfacesMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        this.getLog().info("Generating interfaces...");

//        if (true) return;
//        this.getClass().getClassLoader().

        URL[] urls;
        File root = new File(project.getBuild().getOutputDirectory());

        try {
            urls = new URL[]{root.toURI().toURL()};
        } catch (Exception e) {
            this.getLog().error(e);
            throw new MojoFailureException("Failed to generate interfaces", e);
        }

        ClassLoader cl = getClassLoader(project);
        this.getLog().info("Looking for classes in " + root.getAbsolutePath());
        try {
            Files.walk(root.toPath())
                    .peek(file -> this.getLog().info("Found file " + file.getFileName()))
                    .filter(file -> file.getFileName().toString().endsWith(".class"))
                    .peek(file -> this.getLog().info("Found classfile " + file.getFileName()))
                    .forEach(file -> {
                        String className = root.toURI().relativize(file.toUri()).getPath().replaceAll("/", ".").replace(".class", ""); //file.getParent().toa file.getFileName().toString().replace(".class", "");
                        this.getLog().info("Found classfile " + file.getFileName());
                        try {
                            Class<?> clazz = cl.loadClass(className);
                            this.getLog().info("Generating interface for class: " + clazz.getName());
    //                    new ByteBuddy().subclass(clazz).make().saveIn(new File(project.getBuild().getOutputDirectory()));
                        } catch (ClassNotFoundException e) {
                            this.getLog().error(e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
//                this.getLog().info("Found file " + file.getName());
//                if (file.getName().endsWith(".class")){
//                    String className = file.getName().replace(".class", "");
//                    Class<?> clazz = cl.loadClass(className);
//                    this.getLog().info("Generating interface for class: " + clazz.getName());
////                    new ByteBuddy().subclass(clazz).make().saveIn(new File(project.getBuild().getOutputDirectory()+"/interfaces"));
//                }
//        } catch (IOException e) {
//            this.getLog().error(e);
//            throw new MojoFailureException("Failed to generate interfaces", e);
//        }
    }

    private ClassLoader getClassLoader(MavenProject project)
    {
        try
        {
            List classpathElements = project.getCompileClasspathElements();
            classpathElements.add( project.getBuild().getOutputDirectory() );
            classpathElements.add( project.getBuild().getTestOutputDirectory() );
            URL urls[] = new URL[classpathElements.size()];
            for ( int i = 0; i < classpathElements.size(); ++i )
            {
                urls[i] = new File( (String) classpathElements.get( i ) ).toURL();
            }
            return new URLClassLoader( urls, this.getClass().getClassLoader() );
        }
        catch ( Exception e )
        {
            getLog().debug( "Couldn't get the classloader." );
            return this.getClass().getClassLoader();
        }
    }
}
