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
package org.eclipse.pde.internal.core;
import java.net.URL;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.pde.core.IModelChangeProvider;
 
public class WorkspaceResourceHelper extends NLResourceHelper
implements IResourceChangeListener {
	private IFile file;
	private IModelChangeProvider changeProvider;

	/**
	 * Constructor for WorkspaceResourceHelper
	 */
	public WorkspaceResourceHelper(String name, URL [] locations) {
		super(name, locations);
		PDECore.getWorkspace().addResourceChangeListener(this);
	}
	
	public void dispose() {
		PDECore.getWorkspace().removeResourceChangeListener(this);
		super.dispose();
	}
	
	public void setFile(IFile file) {
		this.file = file;
	}
	
	public IFile getFile() {
		return file;
	}
	/**
	 * @see IResourceChangeListener#resourceChanged(IResourceChangeEvent)
	 */
	public void resourceChanged(IResourceChangeEvent event) {
		try {
		   IResourceDelta delta = event.getDelta();
		   if (delta!=null) {
		      event.getDelta().accept(new IResourceDeltaVisitor() {
		         public boolean visit(IResourceDelta delta) {
				    return WorkspaceResourceHelper.this.visit(delta);
			     }
		      });
		   }
		}
		catch (CoreException e) {
		}
	}
	
	private boolean visit(IResourceDelta delta) {
		//IResource resource = delta.getResource();
		//if (resource.equals(file)) {
			//bundle = null;
			/*
			if (changeProvider!=null) {
				// fire 'world changed' in the model
				// to force views to refresh because
				// translatable names changed
				ModelChangedEvent e= new ModelChangedEvent(IModelChangedEvent.WORLD_CHANGED, null, null);
				changeProvider.fireModelChanged(e);
			}
			*/
		//}
		return true;
	}
	
	public void setModelChangeProvider(IModelChangeProvider changeProvider) {
		this.changeProvider = changeProvider;
	}
}
