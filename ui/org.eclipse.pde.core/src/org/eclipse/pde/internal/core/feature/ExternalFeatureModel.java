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
package org.eclipse.pde.internal.core.feature;

import org.eclipse.core.runtime.CoreException;

/**
 * @author dejan
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 */
public class ExternalFeatureModel extends AbstractFeatureModel {
	private String location;
	/**
	 * @see AbstractModel#updateTimeStamp()
	 */
	protected void updateTimeStamp() {
	}

	/**
	 * @see IModel#isInSync()
	 */
	public boolean isInSync() {
		return true;
	}
	
	public boolean isEditable() {
		return false;
	}

	/**
	 * @see IModel#load()
	 */
	public void load() throws CoreException {
	}
	
	public void setInstallLocation(String location) {
		this.location = location;
	}
	public String getInstallLocation() {
		return location;
	}

}
