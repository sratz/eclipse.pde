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

import org.eclipse.pde.core.*;

public class ModelProviderEvent implements IModelProviderEvent {
	private int types;
	private Object source;
	private IModel [] added;
	private IModel [] removed;
	private IModel [] changed;

public ModelProviderEvent(Object source, int types, IModel [] added, IModel [] removed, IModel [] changed) {
	this.source = source;
	this.types = types;
	this.added = added;
	this.removed = removed;
	this.changed = changed;
}

public IModel [] getAddedModels() {
	return added;
}

public IModel [] getRemovedModels() {
	return removed;
}

public IModel [] getChangedModels() {
	return changed;
}

public int getEventTypes() {
	return types;
}

public Object getEventSource() {
	return source;
}
}
