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

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import com.redhat.quarkus.commons.EnumItem;
import com.redhat.quarkus.commons.ExtendedConfigDescriptionBuildItem;
import com.redhat.quarkus.commons.QuarkusProjectInfo;
import com.redhat.quarkus.ls.commons.BadLocationException;
import com.redhat.quarkus.model.Node;
import com.redhat.quarkus.model.PropertiesModel;
import com.redhat.quarkus.model.Property;
import com.redhat.quarkus.model.PropertyKey;
import com.redhat.quarkus.model.PropertyValue;
import com.redhat.quarkus.services.QuarkusModel;
import com.redhat.quarkus.settings.QuarkusHoverSettings;
import com.redhat.quarkus.utils.DocumentationUtils;
import com.redhat.quarkus.utils.PositionUtils;
import com.redhat.quarkus.utils.QuarkusPropertiesUtils;

/**
 * Retrieves hover documentation and creating Hover object
 */
class QuarkusHover {

	private static final Logger LOGGER = Logger.getLogger(QuarkusHover.class.getName());

	/**
	 * Returns Hover object for the currently hovered token
	 * 
	 * @param document      the properties model document
	 * @param position      the hover position
	 * @param projectInfo   the Quarkus project information
	 * @param hoverSettings the hover settings
	 * @return Hover object for the currently hovered token
	 */
	public Hover doHover(PropertiesModel document, Position position, QuarkusProjectInfo projectInfo,
			QuarkusHoverSettings hoverSettings) {

		Node node = null;
		int offset = -1;
		try {
			node = document.findNodeAt(position);
			offset = document.offsetAt(position);
		} catch (BadLocationException e) {
			LOGGER.log(Level.SEVERE, "In QuarkusHover, position error", e);
			return null;
		}
		if (node == null) {
			return null;
		}

		switch (node.getNodeType()) {
		case COMMENTS:
			// no hover documentation
			return null;
		case ASSIGN:
		case PROPERTY_VALUE:
			// no hover documentation
			return getPropertyValueHover(node, projectInfo, hoverSettings);
		case PROPERTY_KEY:
			PropertyKey key = (PropertyKey) node;
			if (key.isBeforeProfile(offset)) {
				// hover documentation on profile
				return getProfileHover(key, hoverSettings);
			} else {
				// hover documentation on property key
				return getPropertyKeyHover(key, projectInfo, hoverSettings);
			}
			
		default:
			return null;
		}
	}

	/**
	 * Returns the documentation hover for the property key's profile, for the
	 * property key represented by <code>key</code>
	 * 
	 * Returns null if property key represented by <code>key</code> does not 
	 * have a profile
	 * 
	 * @param key          the property key
	 * @param hoverSettings the hover settings
	 * @return the documentation hover for the property key's profile
	 */
	private static Hover getProfileHover(PropertyKey key, QuarkusHoverSettings hoverSettings) {
		boolean markdownSupported = hoverSettings.isContentFormatSupported(MarkupKind.MARKDOWN);
		for (EnumItem profile: QuarkusModel.DEFAULT_PROFILES) {
			if (profile.getName().equals(key.getProfile())) {
				MarkupContent markupContent = DocumentationUtils.getDocumentation(profile, markdownSupported);
				Hover hover = new Hover();
				hover.setContents(markupContent);
				hover.setRange(getProfileHoverRange(key));
				return hover;
			}
		}
		return null;
	}

	/**
	 * Returns the documentation hover for property key represented by the property
	 * key <code>key</code>
	 * 
	 * @param key          the property key
	 * @param offset        the hover offset
	 * @param projectInfo   the Quarkus project information
	 * @param hoverSettings the hover settings
	 * @return the documentation hover for property key represented by token
	 */
	private static Hover getPropertyKeyHover(PropertyKey key, QuarkusProjectInfo projectInfo,
			QuarkusHoverSettings hoverSettings) {
		boolean markdownSupported = hoverSettings.isContentFormatSupported(MarkupKind.MARKDOWN);
		// retrieve Quarkus property from the project information
		String propertyName = key.getPropertyName();

		ExtendedConfigDescriptionBuildItem item = QuarkusPropertiesUtils.getProperty(propertyName, projectInfo);
		if (item != null) {
			// Quarkus property, found, display her documentation as hover
			MarkupContent markupContent = DocumentationUtils.getDocumentation(item, key.getProfile(),
					markdownSupported);
			Hover hover = new Hover();
			hover.setContents(markupContent);
			hover.setRange(PositionUtils.createRange(key));
			return hover;
		}
		return null;
	}

	/**
	 * Returns the documentation hover for property key represented by the property
	 * key <code>node</code>
	 * 
	 * @param node          the property key node
	 * @param projectInfo   the Quarkus project information
	 * @param hoverSettings the hover settings
	 * @return the documentation hover for property key represented by token
	 */
	private static Hover getPropertyValueHover(Node node, QuarkusProjectInfo projectInfo,
			QuarkusHoverSettings hoverSettings) {
		PropertyValue value = ((PropertyValue) node);
		boolean markdownSupported = hoverSettings.isContentFormatSupported(MarkupKind.MARKDOWN);
		// retrieve Quarkus property from the project information
		String propertyValue = value.getValue();
		if (propertyValue == null || propertyValue.isEmpty()) {
			return null;
		}
		String propertyName = ((Property) (value.getParent())).getPropertyName();
		ExtendedConfigDescriptionBuildItem item = QuarkusPropertiesUtils.getProperty(propertyName, projectInfo);
		EnumItem enumItem = item != null ? item.getEnumItem(propertyValue) : null;
		if (enumItem != null) {
			// Quarkus property enumeration item, found, display her documentation as hover
			MarkupContent markupContent = DocumentationUtils.getDocumentation(enumItem, markdownSupported);
			Hover hover = new Hover();
			hover.setContents(markupContent);
			hover.setRange(PositionUtils.createRange(node));
			return hover;
		}
		return null;
	}

	/**
	 * Returns the hover range covering the %profilename in <code>key</code>
	 * Returns range of <code>key</code> if <code>key</code> does not provide a profile
	 * @param key the property key
	 * @return the hover range covering the %profilename in <code>key</code>
	 */
	private static Range getProfileHoverRange(PropertyKey key) {
		Range range = PositionUtils.createRange(key);
		
		if (key.getProfile() == null) {
			return range;
		}

		String profile = key.getProfile();
		Position endPosition = range.getEnd();
		endPosition.setCharacter(range.getStart().getCharacter() + profile.length() + 1);
		range.setEnd(endPosition);
		return range;
	}
}