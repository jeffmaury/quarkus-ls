/*******************************************************************************
* Copyright (c) 2019 Red Hat Inc. and others.
* All rights reserved. This program and the accompanying materials
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v20.html
*
* Contributors:
*     Red Hat Inc. - initial API and implementation
*******************************************************************************/
package com.redhat.quarkus.ls;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import com.redhat.quarkus.commons.QuarkusProjectInfoParams;
import com.redhat.quarkus.commons.QuarkusPropertiesChangeEvent;
import com.redhat.quarkus.ls.commons.ModelTextDocument;
import com.redhat.quarkus.ls.commons.ModelTextDocuments;
import com.redhat.quarkus.model.PropertiesModel;
import com.redhat.quarkus.services.QuarkusLanguageService;
import com.redhat.quarkus.settings.QuarkusFormattingSettings;
import com.redhat.quarkus.settings.QuarkusSymbolSettings;
import com.redhat.quarkus.settings.QuarkusValidationSettings;
import com.redhat.quarkus.settings.SharedSettings;

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;

/**
 * Quarkus text document service.
 *
 */
public class QuarkusTextDocumentService implements TextDocumentService {

	private final ModelTextDocuments<PropertiesModel> documents;

	private QuarkusProjectInfoCache projectInfoCache;

	private final QuarkusLanguageServer quarkusLanguageServer;

	private final SharedSettings sharedSettings;

	private boolean hierarchicalDocumentSymbolSupport;

	private boolean definitionLinkSupport;

	public QuarkusTextDocumentService(QuarkusLanguageServer quarkusLanguageServer) {
		this.quarkusLanguageServer = quarkusLanguageServer;
		this.documents = new ModelTextDocuments<PropertiesModel>((document, cancelChecker) -> {
			return PropertiesModel.parse(document);
		});
		this.sharedSettings = new SharedSettings();
	}

	/**
	 * Update shared settings from the client capabilities.
	 * 
	 * @param capabilities the client capabilities
	 */
	public void updateClientCapabilities(ClientCapabilities capabilities) {
		TextDocumentClientCapabilities textDocumentClientCapabilities = capabilities.getTextDocument();
		if (textDocumentClientCapabilities != null) {
			sharedSettings.getCompletionSettings().setCapabilities(textDocumentClientCapabilities.getCompletion());
			sharedSettings.getHoverSettings().setCapabilities(textDocumentClientCapabilities.getHover());
			hierarchicalDocumentSymbolSupport = textDocumentClientCapabilities.getDocumentSymbol() != null
					&& textDocumentClientCapabilities.getDocumentSymbol().getHierarchicalDocumentSymbolSupport() != null
					&& textDocumentClientCapabilities.getDocumentSymbol().getHierarchicalDocumentSymbolSupport();
			definitionLinkSupport = textDocumentClientCapabilities.getDefinition() != null
					&& textDocumentClientCapabilities.getDefinition().getLinkSupport() != null
					&& textDocumentClientCapabilities.getDefinition().getLinkSupport();
		}
	}

	@Override
	public void didOpen(DidOpenTextDocumentParams params) {
		ModelTextDocument<PropertiesModel> document = documents.onDidOpenTextDocument(params);
		triggerValidationFor(document);
	}

	@Override
	public void didChange(DidChangeTextDocumentParams params) {
		ModelTextDocument<PropertiesModel> document = documents.onDidChangeTextDocument(params);
		triggerValidationFor(document);
	}

	@Override
	public void didClose(DidCloseTextDocumentParams params) {
		documents.onDidCloseTextDocument(params);
		TextDocumentIdentifier document = params.getTextDocument();
		String uri = document.getUri();
		quarkusLanguageServer.getLanguageClient()
				.publishDiagnostics(new PublishDiagnosticsParams(uri, new ArrayList<Diagnostic>()));
	}

	@Override
	public void didSave(DidSaveTextDocumentParams params) {

	}

	@Override
	public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
		// Get Quarkus project information which stores all available Quarkus
		// properties
		QuarkusProjectInfoParams projectInfoParams = createProjectInfoParams(params.getTextDocument(), null);
		return getProjectInfoCache().getQuarkusProjectInfo(projectInfoParams).thenComposeAsync(projectInfo -> {
			if (projectInfo.getProperties().isEmpty()) {
				return CompletableFuture.completedFuture(Either.forRight(new CompletionList()));
			}
			// then get the Properties model document
			return getPropertiesModel(params.getTextDocument(), (cancelChecker, document) -> {
				// then return completion by using the Quarkus project information and the
				// Properties model document
				CompletionList list = getQuarkusLanguageService().doComplete(document, params.getPosition(),
						projectInfo, sharedSettings.getCompletionSettings(), null);
				return Either.forRight(list);
			});
		});
	}

	@Override
	public CompletableFuture<Hover> hover(TextDocumentPositionParams params) {
		// Get Quarkus project information which stores all available Quarkus
		// properties
		QuarkusProjectInfoParams projectInfoParams = createProjectInfoParams(params.getTextDocument(), null);
		return getProjectInfoCache().getQuarkusProjectInfo(projectInfoParams).thenComposeAsync(projectInfo -> {
			if (projectInfo.getProperties().isEmpty()) {
				return null;
			}
			// then get the Properties model document
			return getPropertiesModel(params.getTextDocument(), (cancelChecker, document) -> {
				// then return hover by using the Quarkus project information and the
				// Properties model document
				return getQuarkusLanguageService().doHover(document, params.getPosition(), projectInfo,
						sharedSettings.getHoverSettings());
			});
		});
	}

	@Override
	public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
			DocumentSymbolParams params) {
		return getPropertiesModel(params.getTextDocument(), (cancelChecker, document) -> {
			if (hierarchicalDocumentSymbolSupport && sharedSettings.getSymbolSettings().isShowAsTree()) {
				return getQuarkusLanguageService().findDocumentSymbols(document, cancelChecker) //
						.stream() //
						.map(s -> {
							Either<SymbolInformation, DocumentSymbol> e = Either.forRight(s);
							return e;
						}) //
						.collect(Collectors.toList());
			}
			return getQuarkusLanguageService().findSymbolInformations(document, cancelChecker) //
					.stream() //
					.map(s -> {
						Either<SymbolInformation, DocumentSymbol> e = Either.forLeft(s);
						return e;
					}) //
					.collect(Collectors.toList());
		});
	}

	@Override
	public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
			TextDocumentPositionParams params) {
		QuarkusProjectInfoParams projectInfoParams = createProjectInfoParams(params.getTextDocument(), null);
		return getProjectInfoCache().getQuarkusProjectInfo(projectInfoParams).thenComposeAsync(projectInfo -> {
			if (projectInfo.getProperties().isEmpty()) {
				return null;
			}
			// then get the Properties model document
			return getDocument(params.getTextDocument().getUri()).getModel().thenComposeAsync(document -> {
				return getQuarkusLanguageService().findDefinition(document, params.getPosition(), projectInfo,
						quarkusLanguageServer.getLanguageClient(), definitionLinkSupport);
			});
		});
	}

	@Override
	public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
		return getPropertiesModel(params.getTextDocument(), (cancelChecker, document) -> {
			return getQuarkusLanguageService().doFormat(document, sharedSettings.getFormattingSettings());
		});
	}

	@Override
	public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
		return getPropertiesModel(params.getTextDocument(), (cancelChecker, document) -> {
			return getQuarkusLanguageService().doRangeFormat(document, params.getRange(),
					sharedSettings.getFormattingSettings());
		});
	}

	private static QuarkusProjectInfoParams createProjectInfoParams(TextDocumentIdentifier id,
			List<String> documentationFormat) {
		return createProjectInfoParams(id.getUri(), documentationFormat);
	}

	private static QuarkusProjectInfoParams createProjectInfoParams(String uri, List<String> documentationFormat) {
		return new QuarkusProjectInfoParams(uri, documentationFormat);
	}

	private QuarkusLanguageService getQuarkusLanguageService() {
		return quarkusLanguageServer.getQuarkusLanguageService();
	}

	private void triggerValidationFor(ModelTextDocument<PropertiesModel> document) {
		// Get Quarkus project information which stores all available Quarkus
		// properties
		QuarkusProjectInfoParams projectInfoParams = createProjectInfoParams(document.getUri(), null);
		getProjectInfoCache().getQuarkusProjectInfo(projectInfoParams).thenComposeAsync(projectInfo -> {
			if (projectInfo.getProperties().isEmpty()) {
				return null;
			}
			// then get the Properties model document
			return getPropertiesModel(document, (cancelChecker, model) -> {
				// then return do validation by using the Quarkus project information and the
				// Properties model document
				List<Diagnostic> diagnostics = getQuarkusLanguageService().doDiagnostics(model, projectInfo,
						getSharedSettings().getValidationSettings(), cancelChecker);
				quarkusLanguageServer.getLanguageClient()
						.publishDiagnostics(new PublishDiagnosticsParams(model.getDocumentURI(), diagnostics));
				return null;
			});
		});
	}

	/**
	 * Returns the text document from the given uri.
	 * 
	 * @param uri the uri
	 * @return the text document from the given uri.
	 */
	public ModelTextDocument<PropertiesModel> getDocument(String uri) {
		return documents.get(uri);
	}

	/**
	 * Returns the properties model for a given uri in a future and then apply the
	 * given function.
	 * 
	 * @param <R>
	 * @param documentIdentifier the document indetifier.
	 * @param code               a bi function that accepts a {@link CancelChecker}
	 *                           and parsed {@link PropertiesModel} and returns the
	 *                           to be computed value
	 * @return the properties model for a given uri in a future and then apply the
	 *         given function.
	 */
	public <R> CompletableFuture<R> getPropertiesModel(TextDocumentIdentifier documentIdentifier,
			BiFunction<CancelChecker, PropertiesModel, R> code) {
		return getPropertiesModel(getDocument(documentIdentifier.getUri()), code);
	}

	/**
	 * Returns the properties model for a given uri in a future and then apply the
	 * given function.
	 * 
	 * @param <R>
	 * @param documentIdentifier the document indetifier.
	 * @param code               a bi function that accepts a {@link CancelChecker}
	 *                           and parsed {@link PropertiesModel} and returns the
	 *                           to be computed value
	 * @return the properties model for a given uri in a future and then apply the
	 *         given function.
	 */
	public <R> CompletableFuture<R> getPropertiesModel(ModelTextDocument<PropertiesModel> document,
			BiFunction<CancelChecker, PropertiesModel, R> code) {
		return computeModelAsync(document.getModel(), code);
	}

	private static <R, M> CompletableFuture<R> computeModelAsync(CompletableFuture<M> loadModel,
			BiFunction<CancelChecker, M, R> code) {
		CompletableFuture<CancelChecker> start = new CompletableFuture<>();
		CompletableFuture<R> result = start.thenCombineAsync(loadModel, code);
		CancelChecker cancelIndicator = () -> {
			if (result.isCancelled())
				throw new CancellationException();
		};
		start.complete(cancelIndicator);
		return result;
	}

	public void quarkusPropertiesChanged(QuarkusPropertiesChangeEvent event) {
		Collection<String> uris = getProjectInfoCache().quarkusPropertiesChanged(event);
		for (String uri : uris) {
			ModelTextDocument<PropertiesModel> document = getDocument(uri);
			if (document != null) {
				triggerValidationFor(document);
			}
		}
	}

	public void updateSymbolSettings(QuarkusSymbolSettings newSettings) {
		QuarkusSymbolSettings symbolSettings = sharedSettings.getSymbolSettings();
		symbolSettings.setShowAsTree(newSettings.isShowAsTree());
	}

	public void updateValidationSettings(QuarkusValidationSettings newValidation) {
		// Update validation settings
		QuarkusValidationSettings validation = sharedSettings.getValidationSettings();
		validation.update(newValidation);
		// trigger validation for all opened application.properties
		documents.all().stream().forEach(document -> {
			triggerValidationFor(document);
		});
	}

	/**
	 * Updates Quarkus formatting settings configured from the client.
	 * @param newFormatting the new Quarkus formatting settings
	 */
	public void updateFormattingSettings(QuarkusFormattingSettings newFormatting) {
		QuarkusFormattingSettings formatting = sharedSettings.getFormattingSettings();
		formatting.setSurroundEqualsWithSpaces(newFormatting.isSurroundEqualsWithSpaces());
	}

	public SharedSettings getSharedSettings() {
		return sharedSettings;
	}

	private QuarkusProjectInfoCache getProjectInfoCache() {
		if (projectInfoCache == null) {
			createProjectInfoCache();
		}
		return projectInfoCache;
	}

	private synchronized void createProjectInfoCache() {
		if (projectInfoCache != null) {
			return;
		}
		projectInfoCache = new QuarkusProjectInfoCache(quarkusLanguageServer.getLanguageClient());
	}

}