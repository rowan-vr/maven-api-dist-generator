package dev.rvr.apigenerator;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.ExceptionMethod;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.jar.ManifestException;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static net.bytebuddy.matcher.ElementMatchers.is;

@Mojo(name = "generate-api-dist", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.TEST)
public class GenerateApiMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter( defaultValue = "${session}", readonly = true, required = true )
    private MavenSession session;

    @Parameter
    private MavenArchiveConfiguration archive = new MavenArchiveConfiguration();

    @Component
    private MavenProjectHelper projectHelper;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        this.getLog().info("Generating api classes...");

        File root = new File(project.getBuild().getOutputDirectory());

        ClassLoader cl = getClassLoader(project);
        this.getLog().info("Looking for classes in " + root.getAbsolutePath());
        try {
            File apiOutput = new File(project.getBuild().getDirectory() + "/generated-api-classes");

            Files.walk(root.toPath()).peek(file -> this.getLog().info("Found file " + file.getFileName())).filter(file -> file.getFileName().toString().endsWith(".class")).forEach(file -> {
                String className = classNameOfFile(root, file);
                try {
                    Class<?> clazz = cl.loadClass(className);
                    if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
                        return;
                    }

                    var builder = new ByteBuddy().redefine(clazz);
                    builder.name(classNameOfFile(root, file));

                    for (Method method : clazz.getDeclaredMethods()) {
                        builder = builder.method(is(method)).intercept(ExceptionMethod.throwing(UnsupportedOperationException.class, "Methods cannot be called on the API distribution"));
                    }

                    for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                        builder = builder.constructor(is(constructor)).intercept(ExceptionMethod.throwing(UnsupportedOperationException.class, "Methods cannot be called on the API distribution"));
                    }

                    builder.make().saveIn(apiOutput);
                    this.getLog().info("Created api class for " + className);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                    this.getLog().error("Could not find class " + className + "! Resulting api jar may be incomplete!", e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            this.getLog().info("Done creating api classes");

            File jarFile = createArchive();
            projectHelper.attachArtifact(project, "jar", "api", jarFile);


        } catch (IOException e) {
            throw new MojoFailureException(e);
        }
    }

    private String classNameOfFile(File root, Path file) {
        return root.toURI().relativize(file.toUri()).getPath().replaceAll("/", ".").replace(".class", "");
    }

    private ClassLoader getClassLoader(MavenProject project) {
        return getClassLoader(project, false);
    }

    private ClassLoader getClassLoader(MavenProject project, boolean excludeOutput) {
        try {
            List classpathElements = new ArrayList();
            if (!excludeOutput) {
                classpathElements.addAll(project.getCompileClasspathElements());
                classpathElements.add(project.getBuild().getOutputDirectory());
                classpathElements.add(project.getBuild().getTestOutputDirectory());
            }
            Set<Artifact> dependencies = project.getArtifacts();
            for (Artifact artifact : dependencies) {
                classpathElements.add(artifact.getFile().getAbsolutePath());
                this.getLog().debug("Adding dependency: " + artifact.getFile().getAbsolutePath());
            }

            URL urls[] = new URL[classpathElements.size()];
            for (int i = 0; i < classpathElements.size(); ++i) {
                urls[i] = new File((String) classpathElements.get(i)).toURI().toURL();
            }
            return new URLClassLoader(urls);
        } catch (Exception e) {
            getLog().debug("Couldn't get the classloader.");
            return this.getClass().getClassLoader();
        }
    }

    private File createArchive() {
        File jarFile = getJarFile(new File(project.getBuild().getDirectory()), project.getArtifactId()+"-"+project.getVersion(), "api");

        MavenArchiver archiver = new MavenArchiver();
        archiver.setCreatedBy("Maven API Generator", "dev.rvr","maven-interfaces-generator");
        archiver.setArchiver(new JarArchiver());
        archiver.setOutputFile(jarFile);

        try {
            File contentDirectory = new File(project.getBuild().getDirectory() + "/generated-api-classes");
            archiver.getArchiver().addDirectory(contentDirectory);
            archiver.createArchive(session,project,archive);
            return jarFile;
        } catch (DependencyResolutionRequiredException | IOException | ManifestException e) {
            throw new RuntimeException(e);
        }
    }

    protected File getJarFile(File basedir, String resultFinalName, String classifier) {
        if (basedir == null) {
            throw new IllegalArgumentException("basedir is not allowed to be null");
        }
        if (resultFinalName == null) {
            throw new IllegalArgumentException("finalName is not allowed to be null");
        }

        String fileName;
        if (classifier != null) {
            fileName = resultFinalName + "-" + classifier + ".jar";
        } else {
            fileName = resultFinalName + ".jar";
        }

        return new File(basedir, fileName);
    }
}
