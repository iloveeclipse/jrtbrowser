/*******************************************************************************
 * Copyright (c) 2016 Andrey Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor: Andrey Loskutov - initial API and implementation
 *******************************************************************************/
package de.loskutov.jrtbrowser;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ProviderNotFoundException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author andrey
 */
public class JrtAccess {
	Map<String, FileSystem> systems = new HashMap<>();
	Map<FileSystem, Map<String, String>> packages = new HashMap<>();

	public static void main(String[] args) throws Exception {
		if(args.length == 0){
			System.out.println("Please provide valid Java 9 home directory path");
			return;
		}
		JrtAccess ja = new JrtAccess();
		FileSystem fs = ja.createJfs(args[0]);
		if(fs == null){
			System.out.println(args[0] + " is not a valid Java 9 home directory path");
			return;
		}
		Map<String, String> moduleMap = ja.createPackageToModuleMap(fs);
		moduleMap.forEach((x, y) -> System.out.println(x + " -> " + y));
	}

	public FileSystem createJfs(String jdkHome) throws IOException {
		Path path = Paths.get(jdkHome, "lib/jrt-fs.jar");
		if(!Files.isRegularFile(path)){
			return null;
		}
		URL url = path.toUri().toURL();
		URLClassLoader loader = new URLClassLoader(new URL[] { url });
		FileSystem fs = FileSystems.newFileSystem(URI.create("jrt:/"),
				Collections.emptyMap(),
				loader);
		systems.put(jdkHome, fs);
		//		Path top = fs.getPath("/");
		//		Files.walk(top).filter(Files::isRegularFile).forEach(System.out::println);
		//		Files.walk(top).filter(Files::isDirectory).forEach(System.out::println);
		return fs;
	}

	public FileSystem createCtsym(String jdkHome) throws IOException {
		Path path = Paths.get(jdkHome, "lib/ct.sym");
		if(!Files.isRegularFile(path)){
			return null;
		}
		FileSystem fst = null;
		URI uri = URI.create("jar:file:" + path.toUri().getRawPath()); //$NON-NLS-1$
		try {
			fst = FileSystems.getFileSystem(uri);
		} catch (Exception fne) {
			// Ignore and move on
		}
		if (fst == null) {
			try {
				fst = FileSystems.newFileSystem(uri, new HashMap<>(), ClassLoader.getSystemClassLoader());
			} catch (FileSystemAlreadyExistsException e) {
				fst = FileSystems.getFileSystem(uri);
			} catch (ProviderNotFoundException e) {
				throw new IOException("Failed to create ct.sym file system for " + path, e); //$NON-NLS-1$
			}
		}
		systems.put(jdkHome, fst);
		//		Path top = fs.getPath("/");
		//		Files.walk(top).filter(Files::isRegularFile).forEach(System.out::println);
		//		Files.walk(top).filter(Files::isDirectory).forEach(System.out::println);
		return fst;
	}

	public Map<String, String> createPackageToModuleMap(FileSystem fs) throws IOException{
		HashMap<String, String> packageToModule = new LinkedHashMap<>();
		Path path = fs.getPath("packages");
		Files.list(path).forEach(p -> {
			try {
				Files.list(p).findFirst().ifPresent(c -> packageToModule.put(fileName(p), fileName(c)));
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
		return packageToModule;
	}

	public void dispose() {
		systems.values().forEach(fs -> {
			try {
				fs.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}

	static String fileName(Path p){
		Path fileName = p.getFileName();
		return fileName == null? "/" : fileName.toString();
	}
}
