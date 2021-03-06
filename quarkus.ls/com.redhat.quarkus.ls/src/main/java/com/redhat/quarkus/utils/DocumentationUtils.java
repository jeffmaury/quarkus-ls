/*******************************************************************************
* Copyright (c) 2019 Red Hat Inc. and others.
* All rights reserved. This program and the accompanying materials
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v20.html
*
* Contributors:
*     Red Hat Inc. - initial API and implementation
*******************************************************************************/

package com.redhat.quarkus.utils;

import static com.redhat.quarkus.utils.QuarkusPropertiesUtils.formatPropertyForMarkdown;

import com.redhat.quarkus.commons.EnumItem;
import com.redhat.quarkus.commons.ExtendedConfigDescriptionBuildItem;

import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

/**
 * Utility for documentation.
 *
 */
public class DocumentationUtils {

	private DocumentationUtils() {

	}

	/**
	 * Returns the documentation of the given Quarkus property.
	 * 
	 * @param item     the Quarkus property.
	 * @param profile  the profile
	 * @param markdown true if documentation must be formatted as markdown and false
	 *                 otherwise.
	 * @return the documentation of the given Quarkus property.
	 */
	public static MarkupContent getDocumentation(ExtendedConfigDescriptionBuildItem item, String profile,
			boolean markdown) {

		StringBuilder documentation = new StringBuilder();

		// Title
		if (markdown) {
			documentation.append("**");
		}
		documentation.append(markdown ? formatPropertyForMarkdown(item.getPropertyName()) : item.getPropertyName());
		if (markdown) {
			documentation.append("**");
		}
		documentation.append(System.lineSeparator());

		// Javadoc
		String javaDoc = item.getDocs();
		if (javaDoc != null) {
			documentation.append(System.lineSeparator());
			documentation.append(javaDoc);
			documentation.append(System.lineSeparator());
		}

		// Profile
		addParameter("Profile", profile, documentation, markdown);

		// Type
		addParameter("Type", item.getType(), documentation, markdown);

		// Default value
		addParameter("Default", item.getDefaultValue(), documentation, markdown);

		// Config Phase
		addParameter("Phase", getPhaseLabel(item.getPhase()), documentation, markdown);

		// Extension name
		addParameter("Extension", item.getExtensionName(), documentation, markdown);

		return new MarkupContent(markdown ? MarkupKind.MARKDOWN : MarkupKind.PLAINTEXT, documentation.toString());
	}

	private static String getPhaseLabel(int phase) {
		switch (phase) {
		case ExtendedConfigDescriptionBuildItem.CONFIG_PHASE_BUILD_TIME:
			return "buildtime";
		case ExtendedConfigDescriptionBuildItem.CONFIG_PHASE_RUN_TIME:
			return "runtime";
		case ExtendedConfigDescriptionBuildItem.CONFIG_PHASE_BUILD_AND_RUN_TIME_FIXED:
			return "buildtime & runtime";
		default:
			return null;
		}
	}

	private static void addParameter(String name, String value, StringBuilder documentation, boolean markdown) {
		if (value != null) {
			documentation.append(System.lineSeparator());
			if (markdown) {
				documentation.append(" * ");
			}
			documentation.append(name);
			documentation.append(": ");
			if (markdown) {
				documentation.append("`");
			}
			documentation.append(value);
			if (markdown) {
				documentation.append("`");
			}
		}
	}

	/**
	 * Returns the documentation of the given enumeration item.
	 * 
	 * @param item     the enumeration item
	 * @param markdown true if documentation must be formatted as markdown and false
	 *                 otherwise.
	 * @return the documentation of the given Quarkus property.
	 */
	public static MarkupContent getDocumentation(EnumItem item, boolean markdown) {

		StringBuilder documentation = new StringBuilder();

		// Title
		if (markdown) {
			documentation.append("**");
		}
		documentation.append(markdown ? formatPropertyForMarkdown(item.getName()) : item.getName());
		if (markdown) {
			documentation.append("**");
		}
		documentation.append(System.lineSeparator());

		// Javadoc
		String javaDoc = item.getDocs();
		if (javaDoc != null) {
			documentation.append(System.lineSeparator());
			documentation.append(javaDoc);
			documentation.append(System.lineSeparator());
		}

		return new MarkupContent(markdown ? MarkupKind.MARKDOWN : MarkupKind.PLAINTEXT, documentation.toString());
	}

	/**
	 * Returns the documentation content of <code>documentation</code>.
	 * Returns null if documentation content does not exist.
	 * @param documentation contains documentation content
	 * @return the documentation content of <code>documentation</code>.
	 */
	public static String getDocumentationTextFromEither(Either<String, MarkupContent> documentation) {

		if (documentation == null) {
			return null;
		}

		if (documentation.isRight()) {
			return documentation.getRight().getValue();
		}

		return documentation.getLeft();
	}
}
