/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.core.build;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.pde.core.build.IBuildModel;
import org.eclipse.pde.internal.core.PDECore;

public class BuildObject {
	private IBuildModel model;

public BuildObject() {
}
protected void ensureModelEditable() throws CoreException {
	if (!model.isEditable()) {
		throwCoreException("Illegal attempt to change read-only build.properties");
	}
}
public IBuildModel getModel() {
	return model;
}
void setModel(IBuildModel newModel) {
	model = newModel;
}
protected void throwCoreException(String message) throws CoreException {
	Status status =
		new Status(IStatus.ERROR, PDECore.getPluginId(), IStatus.OK, message, null);
	throw new CoreException(status);
}
}
