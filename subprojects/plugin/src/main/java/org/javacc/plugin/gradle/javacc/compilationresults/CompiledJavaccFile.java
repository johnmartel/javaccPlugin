package org.javacc.plugin.gradle.javacc.compilationresults;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.gradle.api.file.FileTree;
import org.gradle.api.logging.Logger;

public class CompiledJavaccFile {
    private static final Pattern PACKAGE_DECLARATION_PATTERN = Pattern.compile("package\\s+([^\\s.;]+(\\.[^\\s.;]+)*)\\s*;");

    private File compiledJavaccFile;
    private File outputDirectory;
    private FileTree customAstClassesDirectory;
    private File targetDirectory;
    private Logger logger;

    CompiledJavaccFile(File file, File outputDirectory, FileTree customAstClassesDirectory, File targetDirectory, Logger logger) {
        this.compiledJavaccFile = file;
        this.outputDirectory = outputDirectory;
        this.customAstClassesDirectory = customAstClassesDirectory;
        this.targetDirectory = targetDirectory;
        this.logger = logger;
    }

    public boolean customAstClassExists() {
        return customAstClassExists(customAstClassesDirectory);
    }

    public boolean customAstClassExists(FileTree fileTree) {
        File customAstClassInputFile = getCustomAstClassInputFile(fileTree);

        return (customAstClassInputFile != null) && customAstClassInputFile.exists();
    }

    private File getCustomAstClassInputFile(FileTree fileTree) {
        String compiledJavaccFilePackage = getPackageName(compiledJavaccFile);

        if (fileTree != null) {
            Collection<File> sourceFiles = fileTree.getFiles();
            return scanSourceFiles(compiledJavaccFilePackage, sourceFiles);
        } else {
            return null;
        }
    }

    private File scanSourceFiles(String compiledJavaccFilePackage, Collection<File> sourceFiles) {
        for (File sourceFile : sourceFiles) {
            logger.debug("Scanning source file [{}] looking for a file named [{}] in package [{}]", sourceFile, compiledJavaccFile.getName(), compiledJavaccFilePackage);
            if (sourceFile.isDirectory()) {
                Collection<File> childFiles = FileUtils.listFiles(sourceFile, FileFilterUtils.suffixFileFilter(".java"), TrueFileFilter.TRUE);
                File matchingChildFile = scanSourceFiles(compiledJavaccFilePackage, childFiles);
                if (matchingChildFile != null) {
                    return matchingChildFile;
                }
            } else {
                if (FilenameUtils.isExtension(sourceFile.getName(), "java") && compiledJavaccFile.getName().equals(sourceFile.getName())) {
                    String packageName = getPackageName(sourceFile);

                    if (compiledJavaccFilePackage.equals(packageName)) {
                        return sourceFile;
                    }
                }
            }
        }

        return null;
    }

    private String getPackageName(File file) {
        String fileContents = "";
        try {
            fileContents = FileUtils.readFileToString(file, Charsets.UTF_8.name());
        } catch (IOException e) {
            logger.warn("Could not read file contents for file [{}]", file);
        }

        Matcher matcher = PACKAGE_DECLARATION_PATTERN.matcher(fileContents);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return "";
    }

    public void copyCompiledFileToTargetDirectory() {
        logger.info("Custom AST class not found");

        File destination = new File(compiledJavaccFile.getAbsolutePath().replace(outputDirectory.getAbsolutePath(), targetDirectory.getAbsolutePath()));
        logger.info("Copying compiled file {} to {}", compiledJavaccFile, destination);

        try {
            FileUtils.copyFile(compiledJavaccFile, destination);
        } catch (IOException e) {
            String errorMessage = String.format("Could not copy %s from %s to %s", compiledJavaccFile, outputDirectory, targetDirectory);
            throw new CompiledJavaccFileOperationException(errorMessage, e);
        }
    }

    public void copyCustomAstClassToTargetDirectory(FileTree sourceTree) {
        logger.info("Not copying compiled file {} from {} to {} because it is overridden by the custom AST class {}", compiledJavaccFile, outputDirectory, targetDirectory,
            getCustomAstClassInputFile(sourceTree));

        String packagePath = getPackageName(compiledJavaccFile).replaceAll("\\.", Matcher.quoteReplacement(File.separator));
        File destination = new File(targetDirectory.getAbsolutePath() + File.separator + packagePath, compiledJavaccFile.getName());
        logger.info("Copying custom AST class [{}] to [{}]", getCustomAstClassInputFile(sourceTree), destination);

        try {
            FileUtils.copyFile(getCustomAstClassInputFile(sourceTree), destination);
        } catch (IOException e) {
            String errorMessage = String.format("Could not copy %s to %s", getCustomAstClassInputFile(sourceTree), targetDirectory);
            throw new CompiledJavaccFileOperationException(errorMessage, e);
        }
    }

    public void ignoreCompiledFileAndUseCustomAstClassFromJavaSourceTree(FileTree javaSourceTree) {
        logger.info("Ignoring compiled file {} because it is overridden by the custom AST class in Java source tree {}", compiledJavaccFile,
            getCustomAstClassInputFile(javaSourceTree));
    }

    @Override
    public String toString() {
        return compiledJavaccFile.getAbsolutePath();
    }
}
