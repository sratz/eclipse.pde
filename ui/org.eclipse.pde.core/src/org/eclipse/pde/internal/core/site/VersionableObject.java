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
package org.eclipse.pde.internal.core.site;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.pde.internal.core.ifeature.IVersionable;
import org.w3c.dom.Node;

public class VersionableObject
	extends IdentifiableObject
	implements IVersionable {
	protected String version;

	public String getVersion() {
		return version;
	}

	protected void parse(Node node) {
		super.parse(node);
		version = getNodeAttribute(node, "version");
	}

	public void setVersion(String version) throws CoreException {
		ensureModelEditable();
		Object oldValue = this.version;
		this.version = version;
		firePropertyChanged(this, P_VERSION, oldValue, version);
	}
	
	public void restoreProperty(String name, Object oldValue, Object newValue)
		throws CoreException {
		if (name.equals(P_VERSION)) {
			setVersion(newValue != null ? newValue.toString() : null);
		}
		else super.restoreProperty(name, oldValue, newValue);
	}

	protected void reset() {
		super.reset();
		version = null;
	}
}
