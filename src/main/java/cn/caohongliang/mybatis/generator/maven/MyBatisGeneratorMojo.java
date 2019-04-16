/**
 * Copyright 2006-2018 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.caohongliang.mybatis.generator.maven;

import cn.caohongliang.mybatis.generator.maven.plugin.EntityPlugin;
import cn.caohongliang.mybatis.generator.maven.plugin.MapperPlugin;
import lombok.Getter;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.mybatis.generator.api.MyBatisGenerator;
import org.mybatis.generator.api.Plugin;
import org.mybatis.generator.api.ShellCallback;
import org.mybatis.generator.config.Configuration;
import org.mybatis.generator.config.PluginConfiguration;
import org.mybatis.generator.config.xml.ConfigurationParser;
import org.mybatis.generator.exception.InvalidConfigurationException;
import org.mybatis.generator.exception.XMLParserException;
import org.mybatis.generator.internal.ObjectFactory;
import org.mybatis.generator.internal.util.ClassloaderUtility;
import org.mybatis.generator.internal.util.messages.Messages;
import org.mybatis.generator.logging.LogFactory;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * Mybatis生成从这里启动
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        requiresDependencyResolution = ResolutionScope.TEST)
@Getter
public class MyBatisGeneratorMojo extends AbstractMojo {

    private ThreadLocal<ClassLoader> savedClassloader = new ThreadLocal<>();
    private List<Class<? extends Plugin>> defaultPluginTypes = Arrays.asList(EntityPlugin.class, MapperPlugin.class);

    /**
     * 当前项目
     */
    @Parameter(property = "project", required = true, readonly = true)
    private MavenProject project;

    /**
     * 输出目录.
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/mybatis-generator", required = true)
    private File outputDirectory;

    /**
     * 配置文件路径
     */
    @Parameter(property = "mybatis.generator.configurationFile",
            defaultValue = "${project.basedir}/src/main/resources/generatorConfig.xml", required = true)
    private File configurationFile;

    /**
     * 指定mojo是否将进度消息写入日志。
     */
    @Parameter(defaultValue = "false")
    private boolean verbose;

    /**
     * 指定mojo是否覆盖现有Java文件。默认值为false。
     * 请注意，XML文件始终是合并的。
     */
    @Parameter(defaultValue = "true")
    private boolean overwrite;

    /**
     * Skip generator.
     */
    @Parameter(defaultValue = "false")
    private boolean skip;

    /**
     * 如果为true，那么范围compile，provided和系统范围中的依赖项将被*添加到生成器的类路径中。
     * 将搜索这些依赖项，JDBC驱动程序，根类，根接口，生成器插件等。
     */
    @Parameter(defaultValue = "false")
    private boolean includeCompileDependencies;

    /**
     * 如果为true，则所有范围中的依赖项将添加到生成器的类路径中。
     * 将搜索这些依赖项，JDBC驱动程序，根类，根接口，生成器插件等
     */
    @Parameter(defaultValue = "false")
    private boolean includeAllDependencies;

    /**
     * 是否使用swagger，如果为true，则在生成的entity中会给字段使用@ApiModelProperty注解
     */
    @Parameter(defaultValue = "true")
    private boolean entityUseSwagger;

    /**
     * 多主键时的Key的类存放位置
     */
    @Parameter(defaultValue = "key")
    private String entityKeyPackage;

    /**
     * example存放的位置
     */
    @Parameter(defaultValue = "example")
    private String entityExamplePackage;

    /**
     * Dao接口基类的类全名（有主键）
     */
    @Parameter(required = true)
    private String daoRootInterface;

    /**
     * Dao接口基类的类全名（无主键）
     */
    @Parameter
    private String daoRootInterfaceNotPrimaryKey;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("MyBatis generator is skipped.");
            return;
        }

        saveClassLoader();

        LogFactory.setLogFactory(new MavenLogFactory(this));

        calculateClassPath();

        // add resource directories to the classpath.  This is required to support
        // use of a properties file in the build.  Typically, the properties file
        // is in the project's source tree, but the plugin classpath does not
        // include the project classpath.
        List<Resource> resources = project.getResources();
        List<String> resourceDirectories = new ArrayList<>();
        for (Resource resource : resources) {
            resourceDirectories.add(resource.getDirectory());
        }
        ClassLoader cl = ClassloaderUtility.getCustomClassloader(resourceDirectories);
        ObjectFactory.addExternalClassLoader(cl);

        if (configurationFile == null) {
            throw new MojoExecutionException(
                    Messages.getString("RuntimeError.0")); //$NON-NLS-1$
        }

        List<String> warnings = new ArrayList<>();

        if (!configurationFile.exists()) {
            throw new MojoExecutionException(Messages.getString(
                    "RuntimeError.1", configurationFile.toString())); //$NON-NLS-1$
        }

        try {
            ConfigurationParser cp = new ConfigurationParser(
                    project.getProperties(), warnings);
            Configuration config = cp.parseConfiguration(configurationFile);
            //添加默认的plugins
            addDefaultPlugins(config);
            //设置Plugins的配置
            setPluginsConfig();

            ShellCallback callback = new MavenShellCallback(this, overwrite);

            MyBatisGenerator myBatisGenerator = new MyBatisGenerator(config,
                    callback, warnings);


            myBatisGenerator.generate(new MavenProgressCallback(getLog(), verbose));

        } catch (XMLParserException e) {
            for (String error : e.getErrors()) {
                getLog().error(error);
            }

            throw new MojoExecutionException(e.getMessage());
        } catch (SQLException | IOException e) {
            throw new MojoExecutionException(e.getMessage());
        } catch (InvalidConfigurationException e) {
            for (String error : e.getErrors()) {
                getLog().error(error);
            }

            throw new MojoExecutionException(e.getMessage());
        } catch (InterruptedException e) {
            // ignore (will never happen with the DefaultShellCallback)
        }

        for (String error : warnings) {
            getLog().warn(error);
        }

        if (project != null && outputDirectory != null
                && outputDirectory.exists()) {
            project.addCompileSourceRoot(outputDirectory.getAbsolutePath());

            Resource resource = new Resource();
            resource.setDirectory(outputDirectory.getAbsolutePath());
            resource.addInclude("**/*.xml");
            project.addResource(resource);
        }

        restoreClassLoader();
    }

    private void setPluginsConfig() {
        EntityPlugin.useSwagger = this.entityUseSwagger;
        EntityPlugin.keyPackage = this.entityKeyPackage;
        EntityPlugin.examplePackage = this.entityExamplePackage;

        MapperPlugin.rootInterface = this.daoRootInterface;
        MapperPlugin.rootInterfaceNotPrimaryKey = this.daoRootInterfaceNotPrimaryKey;

    }

    private void addDefaultPlugins(Configuration config) {
        config.getContexts().forEach(context -> defaultPluginTypes.forEach(pluginType -> {
            PluginConfiguration pluginConfiguration = new PluginConfiguration();
            pluginConfiguration.setConfigurationType(pluginType.getName());
            context.addPluginConfiguration(pluginConfiguration);
        }));
    }

    private void calculateClassPath() throws MojoExecutionException {
        if (includeCompileDependencies || includeAllDependencies) {
            try {
                // add the project compile classpath to the plugin classpath,
                // so that the project dependency classes can be found
                // directly, without adding the classpath to configuration's classPathEntries
                // repeatedly.Examples are JDBC drivers, root classes, root interfaces, etc.
                Set<String> entries = new HashSet<>();
                if (includeCompileDependencies) {
                    entries.addAll(project.getCompileClasspathElements());
                }

                if (includeAllDependencies) {
                    entries.addAll(project.getTestClasspathElements());
                }

                // remove the output directories (target/classes and target/test-classes)
                // because this mojo runs in the generate-sources phase and
                // those directories have not been created yet (typically)
                entries.remove(project.getBuild().getOutputDirectory());
                entries.remove(project.getBuild().getTestOutputDirectory());

                ClassLoader contextClassLoader = ClassloaderUtility.getCustomClassloader(entries);
                Thread.currentThread().setContextClassLoader(contextClassLoader);
            } catch (DependencyResolutionRequiredException e) {
                throw new MojoExecutionException("Dependency Resolution Required", e);
            }
        }
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    private void saveClassLoader() {
        savedClassloader.set(Thread.currentThread().getContextClassLoader());
    }

    private void restoreClassLoader() {
        Thread.currentThread().setContextClassLoader(savedClassloader.get());
    }
}