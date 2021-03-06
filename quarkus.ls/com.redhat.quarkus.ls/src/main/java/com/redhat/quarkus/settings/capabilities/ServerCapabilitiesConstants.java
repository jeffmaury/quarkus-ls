/**
 *  Copyright (c) 2018 Red Hat, Inc. and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v2.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v20.html
 *
 *  Contributors:
 *  Red Hat Inc. - initial API and implementation
 */
package com.redhat.quarkus.settings.capabilities;

import java.util.Arrays;
import java.util.UUID;

import org.eclipse.lsp4j.CompletionOptions;

/**
 * Server Capabilities Constants
 */
public class ServerCapabilitiesConstants {

	private ServerCapabilitiesConstants() {
	}

	public static final String TEXT_DOCUMENT_COMPLETION = "textDocument/completion";
	public static final String TEXT_DOCUMENT_HOVER = "textDocument/hover";
	public static final String TEXT_DOCUMENT_DOCUMENT_SYMBOL = "textDocument/documentSymbol";
	public static final String TEXT_DOCUMENT_DEFINITION = "textDocument/definition";
	public static final String TEXT_DOCUMENT_FORMATTING = "textDocument/formatting";
	public static final String TEXT_DOCUMENT_RANGE_FORMATTING = "textDocument/rangeFormatting";

	public static final String COMPLETION_ID = UUID.randomUUID().toString();
	public static final String HOVER_ID = UUID.randomUUID().toString();
	public static final String DOCUMENT_SYMBOL_ID = UUID.randomUUID().toString();
	public static final String DEFINITION_ID = UUID.randomUUID().toString();
	public static final String FORMATTING_ID = UUID.randomUUID().toString();
	public static final String RANGE_FORMATTING_ID = UUID.randomUUID().toString();

	public static final CompletionOptions DEFAULT_COMPLETION_OPTIONS = new CompletionOptions(false,
			Arrays.asList(".", "%", "="));
}