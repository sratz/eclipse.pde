package org.eclipse.pde;
/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */

import java.util.*;
import org.eclipse.core.runtime.*;
import org.eclipse.jdt.core.*;
import org.eclipse.core.resources.*;
import org.eclipse.pde.internal.wizards.*;
import org.eclipse.pde.internal.*;
import org.eclipse.pde.internal.preferences.*;
import java.io.*;
import org.eclipse.pde.model.plugin.*;
import org.eclipse.pde.model.build.*;
/**
 * A utility class that can be used by plug-in project
 * wizards to set up the Java build path. The actual
 * entries of the build path are not known in the
 * master wizard. The client wizards need to
 * add these entries depending on the code they
 * generate and the plug-ins they need to reference.
 * This class is typically used from within
 * a plug-in content wizard.
 */
public class BuildPathUtil {
	/**
	 * The default constructor.
	 */
	public BuildPathUtil() {
		super();
	}

	private static void ensureFolderExists(IProject project, IPath folderPath)
		throws CoreException {
		IWorkspace workspace = project.getWorkspace();
		if (!workspace.getRoot().exists(folderPath)) {
			IFolder folder = workspace.getRoot().getFolder(folderPath);
			folder.create(true, true, null);
		}
	}
	/**
	 * Sets the Java build path of the project
	 * using plug-in structure data and
	 * provided entries. These entries are
	 * created in plug-in content wizards
	 * based on the plug-ins required by
	 * the generated code.
	 * @param project the plug-in project handle
	 * @param data structure data passed in by the master wizard
	 * @param libraries an array of the library entries to be set
	 * @param monitor for reporting progress
	 */
	public static void setBuildPath(
		IProject project,
		IPluginStructureData data,
		IClasspathEntry[] libraries,
		IProgressMonitor monitor)
		throws JavaModelException, CoreException {

		// Set output folder
		IJavaProject javaProject = JavaCore.create(project);
		IPath path = project.getFullPath().append(data.getJavaBuildFolderName());
		javaProject.setOutputLocation(path, monitor);

		// Set classpath
		Vector result = new Vector();
		// Source folder first
		addSourceFolder(data.getSourceFolderName(), project, result);
		// Then the libraries
		for (int i = 0; i < libraries.length; i++) {
			result.add(libraries[i]);
		}
		// add implicit libraries
		addImplicitLibraries(result, data.getPluginId());
		// JRE the last
		addJRE(result);
		IClasspathEntry[] entries = new IClasspathEntry[result.size()];
		result.copyInto(entries);
		javaProject.setRawClasspath(entries, monitor);
	}

	/**
	 * Sets the Java build path of the provided plug-in model.
	 * The model is expected to come from the workspace
	 * and should have an underlying resource.
	 * 
	 * @param model the plug-in project handle
	 * @param monitor for reporting progress
	 */

	public static void setBuildPath(
		IPluginModelBase model,
		IProgressMonitor monitor)
		throws JavaModelException, CoreException {

		IProject project = model.getUnderlyingResource().getProject();
		IJavaProject javaProject = JavaCore.create(project);
		// Set classpath
		Vector result = new Vector();
		IBuildModel buildModel = model.getBuildModel();
		if (buildModel != null)
			addSourceFolders(buildModel, result);
		else {
			// just keep the source folders
			keepExistingSourceFolders(javaProject, result);
		}

		// add own libraries, if present
		addLibraries(project, model, false, result);

		// add dependencies
		addDependencies(project, model.getPluginBase().getImports(), result);

		// if fragment, add referenced plug-in
		if (model instanceof IFragmentModel) {
			addFragmentPlugin((IFragmentModel) model, result);
		} else {
			addFragmentLibraries((IPluginModel) model, result, monitor);
		}
		// add implicit libraries
		addImplicitLibraries(result, model.getPluginBase().getId());
		addJRE(result);
		IClasspathEntry[] entries = new IClasspathEntry[result.size()];
		result.copyInto(entries);

		javaProject.setRawClasspath(entries, monitor);
	}

	private static void addImplicitLibraries(Vector result, String id) {
		boolean addRuntime = true;
		if (id.equals("org.eclipse.core.boot"))
			return;
		if (id.equals("org.eclipse.core.runtime") || id.equals("org.apache.xerces"))
			addRuntime = false;
		PluginPathUpdater.addImplicitLibraries(result, addRuntime);
	}

	private static void addSourceFolders(IBuildModel model, Vector result)
		throws CoreException {
		IBuild build = model.getBuild();
		IBuildEntry[] entries = build.getBuildEntries();
		for (int i = 0; i < entries.length; i++) {
			IBuildEntry entry = entries[i];
			if (entry.getName().startsWith("source.")) {
				String[] folders = entry.getTokens();
				for (int j = 0; j < folders.length; j++) {
					addSourceFolder(folders[j], model.getUnderlyingResource().getProject(), result);
				}
			}
		}
	}

	private static void keepExistingSourceFolders(
		IJavaProject jproject,
		Vector result)
		throws CoreException {
		IClasspathEntry[] entries = jproject.getRawClasspath();
		for (int i = 0; i < entries.length; i++) {
			IClasspathEntry entry = entries[i];
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE
				&& entry.getContentKind() == IPackageFragmentRoot.K_SOURCE) {
				result.add(entry);
			}
		}
	}

	private static void addSourceFolder(
		String name,
		IProject project,
		Vector result)
		throws CoreException {
		IPath path = project.getFullPath().append(name);
		ensureFolderExists(project, path);
		IClasspathEntry entry = JavaCore.newSourceEntry(path);
		result.add(entry);
	}

	private static void addLibraries(
		IProject project,
		IPluginModelBase model,
		boolean unconditionallyExport,
		Vector result) {
		IPluginBase pluginBase = model.getPluginBase();
		IPluginLibrary[] libraries = pluginBase.getLibraries();
		IPath rootPath = getRootPath(model);

		for (int i = 0; i < libraries.length; i++) {
			IPluginLibrary library = libraries[i];
			IClasspathEntry entry =
				PluginPathUpdater.createLibraryEntry(library, rootPath, unconditionallyExport);
			if (exists(model, entry))
				result.add(entry);
			else {
				// missing entry - search fragments
				if (!model.isFragmentModel()) {
					resolveLibraryInFragments(model, library, result);
				}
			}
		}
	}

	private static void resolveLibraryInFragments(
		IPluginModelBase model,
		IPluginLibrary library,
		Vector result) {
		IPlugin plugin = (IPlugin) model.getPluginBase();
		IResource resource = model.getUnderlyingResource();

		IFragmentModel[] fmodels;

		if (resource != null)
			fmodels =
				PDEPlugin.getDefault().getWorkspaceModelManager().getWorkspaceFragmentModels();
		else
			fmodels =
				PDEPlugin.getDefault().getExternalModelManager().getFragmentModels(null);
		for (int i = 0; i < fmodels.length; i++) {
			IFragmentModel fmodel = fmodels[i];
			if (fmodel.isEnabled() == false)
				continue;

			IFragment fragment = fmodel.getFragment();
			if (PDEPlugin
				.compare(
					fragment.getPluginId(),
					fragment.getPluginVersion(),
					plugin.getId(),
					plugin.getVersion(),
					fragment.getRule())) {

				IClasspathEntry entry =
					PluginPathUpdater.createLibraryEntry(library, getRootPath(fmodel), false);
				if (exists(fmodel, entry)) {
					result.add(entry);
					// we resolved the missing library - no
					// need to search any more
					break;
				}
			}
		}
	}

	private static IPath getRootPath(IPluginModelBase model) {
		IResource resource = model.getUnderlyingResource();
		IProject project = resource != null ? resource.getProject() : null;

		if (project != null)
			return project.getFullPath();
		else
			return PluginPathUpdater.getExternalPath(model);
	}

	private static boolean exists(IPluginModelBase model, IClasspathEntry entry) {
		IResource resource = model.getUnderlyingResource();
		IProject project = resource != null ? resource.getProject() : null;
		IPath path = entry.getPath();
		if (project == null) {
			File file = path.toFile();
			return file.exists();
		} else {
			IWorkspaceRoot root = project.getWorkspace().getRoot();
			return root.findMember(path) != null;
		}
	}

	private static void addDependencies(
		IProject project,
		IPluginImport[] imports,
		Vector result) {
		Vector checkedPlugins = new Vector();
		for (int i = 0; i < imports.length; i++) {
			IPluginImport iimport = imports[i];
			String id = iimport.getId();
			String version = iimport.getVersion();
			int match = iimport.getMatch();
			IPlugin ref = PDEPlugin.getDefault().findPlugin(id, version, match);
			if (ref != null) {
				checkedPlugins.add(new PluginPathUpdater.CheckedPlugin(ref, true));
			}
		}
		PluginPathUpdater ppu =
			new PluginPathUpdater(project, checkedPlugins.iterator());
		ppu.addClasspathEntries(result);
	}

	private static void addFragmentPlugin(IFragmentModel model, Vector result) {
		IFragment fragment = model.getFragment();
		String id = fragment.getPluginId();
		String version = fragment.getPluginVersion();
		int match = fragment.getRule();

		IPlugin plugin = PDEPlugin.getDefault().findPlugin(id, version, match);
		if (plugin != null) {
			IProject project = plugin.getModel().getUnderlyingResource().getProject();
			Vector checkedPlugins = new Vector();
			checkedPlugins.add(new PluginPathUpdater.CheckedPlugin(plugin, true));
			PluginPathUpdater ppu =
				new PluginPathUpdater(project, checkedPlugins.iterator());
			ppu.addClasspathEntries(result);
		}
	}

	private static void addFragmentLibraries(
		IPluginModel model,
		Vector result,
		IProgressMonitor monitor) {
		IPlugin plugin = model.getPlugin();
		addFragmentLibraries(
			plugin,
			PDEPlugin.getDefault().getWorkspaceModelManager().getWorkspaceFragmentModels(),
			result);
		addFragmentLibraries(
			plugin,
			PDEPlugin.getDefault().getExternalModelManager().getFragmentModels(monitor),
			result);
	}
	private static void addFragmentLibraries(
		IPlugin plugin,
		IFragmentModel[] models,
		Vector result) {
		for (int i = 0; i < models.length; i++) {
			if (models[i].isEnabled() == false)
				continue;
			IFragment fragment = models[i].getFragment();
			if (PDEPlugin
				.compare(
					fragment.getPluginId(),
					fragment.getPluginVersion(),
					plugin.getId(),
					plugin.getVersion(),
					fragment.getRule())) {
				IResource resource = models[i].getUnderlyingResource();
				IProject project = resource != null ? resource.getProject() : null;

				addLibraries(project, models[i], true, result);
			}
		}
	}

	private static void addJRE(Vector result) {
		IPath jrePath = new Path("JRE_LIB");
		IPath[] annot = new IPath[2];
		annot[0] = new Path("JRE_SRC");
		annot[1] = new Path("JRE_SRCROOT");
		if (jrePath != null)
			result.add(JavaCore.newVariableEntry(jrePath, annot[0], annot[1]));
	}

}