/*******************************************************************************
* Copyright (c) 2019 Red Hat Inc. and others.
* All rights reserved. This program and the accompanying materials
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v20.html
*
* Contributors:
*     Red Hat Inc. - initial API and implementation
*******************************************************************************/
package com.redhat.quarkus.services;

/**
 * Quarkus validation types.
 * 
 * @author Angelo ZERR
 *
 */
public enum ValidationType {

	syntax, unknown, duplicate, value, required;
}
