/*******************************************************************************
 * Copyright (c) 2005, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     EclipseSource Corporation - ongoing enhancements
 *******************************************************************************/
package org.eclipse.pde.internal.ui.launcher;

import java.io.File;
import java.util.*;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.*;
import org.eclipse.debug.core.*;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugModelPresentation;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.window.Window;
import org.eclipse.pde.core.plugin.*;
import org.eclipse.pde.internal.core.*;
import org.eclipse.pde.internal.core.ifeature.*;
import org.eclipse.pde.internal.core.iproduct.*;
import org.eclipse.pde.internal.core.iproduct.IProduct;
import org.eclipse.pde.internal.core.util.CoreUtility;
import org.eclipse.pde.internal.launching.IPDEConstants;
import org.eclipse.pde.internal.launching.launcher.BundleLauncherHelper;
import org.eclipse.pde.internal.launching.launcher.LaunchArgumentsHelper;
import org.eclipse.pde.internal.ui.PDEPlugin;
import org.eclipse.pde.internal.ui.PDEUIMessages;
import org.eclipse.pde.launching.IPDELauncherConstants;
import org.eclipse.pde.launching.PDESourcePathProvider;
import org.eclipse.pde.ui.launcher.EclipseLaunchShortcut;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

public class LaunchAction extends Action {

	private IProduct fProduct;
	private String fMode;
	private String fPath;
	private Map<String, IPluginConfiguration> fPluginConfigurations;

	public LaunchAction(IProduct product, String path, String mode) {
		fProduct = product;
		fMode = mode;
		fPath = path;
		// initialize configurations... we should do this lazily
		// TODO
		fPluginConfigurations = new HashMap<String, IPluginConfiguration>();
		IPluginConfiguration[] configurations = fProduct.getPluginConfigurations();
		for (int i = 0; i < configurations.length; i++) {
			IPluginConfiguration config = configurations[i];
			fPluginConfigurations.put(config.getId(), config);
		}
	}

	@Override
	public void run() {
		try {
			ILaunchConfiguration config = findLaunchConfiguration();
			if (config != null)
				DebugUITools.launch(config, fMode);
		} catch (CoreException e) {
		}
	}

	private ILaunchConfiguration findLaunchConfiguration() throws CoreException {
		ILaunchConfiguration[] configs = getLaunchConfigurations();

		if (configs.length == 0)
			return createConfiguration();

		ILaunchConfiguration config = null;
		if (configs.length == 1) {
			config = configs[0];
		} else {
			// Prompt the user to choose a config.
			config = chooseConfiguration(configs);
		}

		if (config != null) {
			config = refreshConfiguration(config.getWorkingCopy());
		}
		return config;
	}

	private ILaunchConfiguration refreshConfiguration(ILaunchConfigurationWorkingCopy wc) throws CoreException {
		wc.setAttribute(IPDELauncherConstants.PRODUCT, fProduct.getProductId());
		wc.setAttribute(IPDELauncherConstants.APPLICATION, fProduct.getApplication());
		String os = Platform.getOS();
		String arch = Platform.getOSArch();
		wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS, getVMArguments(os, arch));
		wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS, getProgramArguments(os, arch));
		wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_JRE_CONTAINER_PATH, getJREContainer(os));
		StringBuffer wsplugins = new StringBuffer();
		StringBuffer explugins = new StringBuffer();
		IPluginModelBase[] models = getModels();
		for (int i = 0; i < models.length; i++) {
			IPluginModelBase model = models[i];
			if (model.getUnderlyingResource() == null) {
				appendBundle(explugins, model);
			} else {
				appendBundle(wsplugins, model);
			}
		}
		wc.setAttribute(IPDELauncherConstants.SELECTED_WORKSPACE_PLUGINS, wsplugins.toString());
		wc.setAttribute(IPDELauncherConstants.SELECTED_TARGET_PLUGINS, explugins.toString());
		String configIni = getTemplateConfigIni(os);
		wc.setAttribute(IPDELauncherConstants.CONFIG_GENERATE_DEFAULT, configIni == null);
		if (configIni != null)
			wc.setAttribute(IPDELauncherConstants.CONFIG_TEMPLATE_LOCATION, configIni);
		return wc.doSave();
	}

	private void appendBundle(StringBuffer buffer, IPluginModelBase model) {
		IPluginConfiguration configuration = fPluginConfigurations.get(model.getPluginBase().getId());
		String sl = "default"; //$NON-NLS-1$
		String autostart = "default"; //$NON-NLS-1$
		if (configuration != null) {
			sl = Integer.toString(configuration.getStartLevel());
			// ensure we don't have a 0 start level
			sl = sl.equals("0") ? "default" : sl; //$NON-NLS-1$ //$NON-NLS-2$
			autostart = Boolean.toString(configuration.isAutoStart());
		}
		String entry = BundleLauncherHelper.writeBundleEntry(model, sl, autostart);
		buffer.append(entry);
		buffer.append(',');
	}

	private String getProgramArguments(String os, String arch) {
		StringBuffer buffer = new StringBuffer(LaunchArgumentsHelper.getInitialProgramArguments());
		IArgumentsInfo info = fProduct.getLauncherArguments();
		String userArgs = (info != null) ? CoreUtility.normalize(info.getCompleteProgramArguments(os, arch)) : ""; //$NON-NLS-1$
		return concatArgs(buffer, userArgs);
	}

	private String getVMArguments(String os, String arch) {
		StringBuffer buffer = new StringBuffer(LaunchArgumentsHelper.getInitialVMArguments());
		IArgumentsInfo info = fProduct.getLauncherArguments();
		String userArgs = (info != null) ? CoreUtility.normalize(info.getCompleteVMArguments(os, arch)) : ""; //$NON-NLS-1$
		return concatArgs(buffer, userArgs);
	}

	private String concatArgs(StringBuffer initialArgs, String userArgs) {
		List<String> initialArgsList = Arrays.asList(DebugPlugin.splitArguments(initialArgs.toString()));
		if (userArgs != null && userArgs.length() > 0) {
			List<String> userArgsList = Arrays.asList(DebugPlugin.splitArguments(userArgs));
			for (Iterator<String> iterator = userArgsList.iterator(); iterator.hasNext();) {
				Object userArg = iterator.next();
				if (!initialArgsList.contains(userArg)) {
					initialArgs.append(' ');
					initialArgs.append(userArg);
				}
			}
		}
		return initialArgs.toString();
	}

	private String getJREContainer(String os) {
		IJREInfo info = fProduct.getJREInfo();
		if (info != null) {
			IPath jrePath = info.getJREContainerPath(os);
			if (jrePath != null) {
				return jrePath.toPortableString();
			}
		}
		return null;
	}

	private IPluginModelBase[] getModels() {
		Set<IPluginModelBase> launchPlugins = new HashSet<IPluginModelBase>();
		if (fProduct.useFeatures()) {
			IFeatureModel[] features = getUniqueFeatures();
			for (int i = 0; i < features.length; i++) {
				addFeaturePlugins(features[i].getFeature(), launchPlugins);
			}
		} else {
			IProductPlugin[] plugins = fProduct.getPlugins();
			for (int i = 0; i < plugins.length; i++) {
				String id = plugins[i].getId();
				if (id == null)
					continue;
				IPluginModelBase model = PluginRegistry.findModel(id);
				if (model != null && TargetPlatformHelper.matchesCurrentEnvironment(model))
					launchPlugins.add(model);
			}
		}
		return launchPlugins.toArray(new IPluginModelBase[launchPlugins.size()]);
	}

	private IFeatureModel[] getUniqueFeatures() {
		ArrayList<IFeatureModel> list = new ArrayList<IFeatureModel>();
		IProductFeature[] features = fProduct.getFeatures();
		for (int i = 0; i < features.length; i++) {
			String id = features[i].getId();
			String version = features[i].getVersion();
			addFeatureAndChildren(id, version, list);
		}
		return list.toArray(new IFeatureModel[list.size()]);
	}

	private void addFeatureAndChildren(String id, String version, List<IFeatureModel> list) {
		FeatureModelManager manager = PDECore.getDefault().getFeatureModelManager();
		IFeatureModel model = manager.findFeatureModel(id, version);
		if (model == null || list.contains(model))
			return;

		list.add(model);

		IFeatureChild[] children = model.getFeature().getIncludedFeatures();
		for (int i = 0; i < children.length; i++) {
			addFeatureAndChildren(children[i].getId(), children[i].getVersion(), list);
		}
	}

	private void addFeaturePlugins(IFeature feature, Set<IPluginModelBase> launchPlugins) {
		IFeaturePlugin[] plugins = feature.getPlugins();
		for (int i = 0; i < plugins.length; i++) {
			String id = plugins[i].getId();
			String version = plugins[i].getVersion();
			if (id == null || version == null)
				continue;
			IPluginModelBase model = PluginRegistry.findModel(id, version, IMatchRules.EQUIVALENT, null);
			if (model == null)
				model = PluginRegistry.findModel(id);
			if (model != null && !launchPlugins.contains(model) && TargetPlatformHelper.matchesCurrentEnvironment(model))
				launchPlugins.add(model);
		}
	}

	private String getTemplateConfigIni(String os) {
		IConfigurationFileInfo info = fProduct.getConfigurationFileInfo();
		if (info != null) {
			String path = info.getPath(os);
			if (path == null) // if we can't find an os path, let's try the normal one
				path = info.getPath(null);
			if (path != null) {
				String expandedPath = getExpandedPath(path);
				if (expandedPath != null) {
					File file = new File(expandedPath);
					if (file.exists() && file.isFile())
						return file.getAbsolutePath();
				}
			}
		}
		return null;
	}

	private String getExpandedPath(String path) {
		if (path == null || path.length() == 0)
			return null;
		IResource resource = PDEPlugin.getWorkspace().getRoot().findMember(new Path(path));
		if (resource != null) {
			IPath fullPath = resource.getLocation();
			return fullPath == null ? null : fullPath.toOSString();
		}
		return null;
	}

	private ILaunchConfiguration chooseConfiguration(ILaunchConfiguration[] configs) {
		IDebugModelPresentation labelProvider = DebugUITools.newDebugModelPresentation();
		ElementListSelectionDialog dialog = new ElementListSelectionDialog(PDEPlugin.getActiveWorkbenchShell(), labelProvider);
		dialog.setElements(configs);
		dialog.setTitle(PDEUIMessages.RuntimeWorkbenchShortcut_title);
		if (fMode.equals(ILaunchManager.DEBUG_MODE)) {
			dialog.setMessage(PDEUIMessages.RuntimeWorkbenchShortcut_select_debug);
		} else {
			dialog.setMessage(PDEUIMessages.RuntimeWorkbenchShortcut_select_run);
		}
		dialog.setMultipleSelection(false);
		int result = dialog.open();
		labelProvider.dispose();
		if (result == Window.OK) {
			return (ILaunchConfiguration) dialog.getFirstResult();
		}
		return null;
	}

	private ILaunchConfiguration createConfiguration() throws CoreException {
		ILaunchConfigurationType configType = getWorkbenchLaunchConfigType();
		String computedName = getComputedName(new Path(fPath).lastSegment());
		ILaunchConfigurationWorkingCopy wc = configType.newInstance(null, computedName);
		wc.setAttribute(IPDELauncherConstants.LOCATION, LaunchArgumentsHelper.getDefaultWorkspaceLocation(computedName));
		wc.setAttribute(IPDELauncherConstants.USEFEATURES, false);
		wc.setAttribute(IPDELauncherConstants.USE_DEFAULT, false);
		wc.setAttribute(IPDELauncherConstants.DOCLEAR, false);
		wc.setAttribute(IPDEConstants.DOCLEARLOG, false);
		wc.setAttribute(IPDEConstants.APPEND_ARGS_EXPLICITLY, true);
		wc.setAttribute(IPDELauncherConstants.ASKCLEAR, true);
		wc.setAttribute(IPDELauncherConstants.USE_PRODUCT, true);
		wc.setAttribute(IPDELauncherConstants.AUTOMATIC_ADD, false);
		wc.setAttribute(IPDELauncherConstants.PRODUCT_FILE, fPath);
		wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_SOURCE_PATH_PROVIDER, PDESourcePathProvider.ID);
		return refreshConfiguration(wc);
	}

	private String getComputedName(String prefix) {
		ILaunchManager lm = DebugPlugin.getDefault().getLaunchManager();
		return lm.generateLaunchConfigurationName(prefix);
	}

	private ILaunchConfiguration[] getLaunchConfigurations() throws CoreException {
		ArrayList<ILaunchConfiguration> result = new ArrayList<ILaunchConfiguration>();
		ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType type = manager.getLaunchConfigurationType(EclipseLaunchShortcut.CONFIGURATION_TYPE);
		ILaunchConfiguration[] configs = manager.getLaunchConfigurations(type);
		for (int i = 0; i < configs.length; i++) {
			if (!DebugUITools.isPrivate(configs[i])) {
				String path = configs[i].getAttribute(IPDELauncherConstants.PRODUCT_FILE, ""); //$NON-NLS-1$
				if (new Path(fPath).equals(new Path(path))) {
					result.add(configs[i]);
				}
			}
		}
		return result.toArray(new ILaunchConfiguration[result.size()]);
	}

	protected ILaunchConfigurationType getWorkbenchLaunchConfigType() {
		ILaunchManager lm = DebugPlugin.getDefault().getLaunchManager();
		return lm.getLaunchConfigurationType(EclipseLaunchShortcut.CONFIGURATION_TYPE);
	}

}
