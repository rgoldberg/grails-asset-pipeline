package asset.pipeline.grails


import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.mapping.DefaultLinkGenerator
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsWebRequest
import grails.util.Environment

import asset.pipeline.AssetHelper
import static asset.pipeline.AssetPipelineConfigHolder.manifest
import static asset.pipeline.grails.UrlBase.*
import static asset.pipeline.grails.utils.net.HttpServletRequests.getBaseUrlWithScheme
import static asset.pipeline.grails.utils.text.StringBuilders.ensureEndsWith
import static asset.pipeline.utils.net.Urls.hasAuthority
import static org.apache.commons.lang.StringUtils.trimToEmpty
import static org.codehaus.groovy.grails.web.servlet.mvc.GrailsWebRequest.lookup


class AssetProcessorService {

	static transactional = false


	def grailsApplication
	def grailsLinkGenerator


	/**
	 * Retrieves the asset path from the property [grails.assets.mapping] which is used by the url mapping and the
	 * taglib.  The property cannot contain <code>/</code>, and must be one level deep
	 *
	 * @return the path
	 * @throws IllegalArgumentException if the path contains <code>/</code>
	 */
	String getAssetMapping() {
		final def mapping = grailsApplication.config?.grails?.assets?.mapping ?: 'assets'
		if (mapping.contains('/')) {
			throw new IllegalArgumentException(
				'The property [grails.assets.mapping] can only be one level deep.  ' +
				"For example, 'foo' and 'bar' would be acceptable values, but 'foo/bar' is not"
			)
		}
		return mapping
	}



	String getAssetPath(final String path, final ConfigObject conf = grailsApplication.config.grails.assets) {
		final String relativePath = trimLeadingSlash(path)
		return manifest?.getProperty(relativePath,relativePath) ?: relativePath
	}


	String getResolvedAssetPath(final String path, final ConfigObject conf = grailsApplication.config.grails.assets) {
		final String relativePath = trimLeadingSlash(path)
		if(manifest) {
			manifest?.getProperty(relativePath)
		} else {
			AssetHelper.fileForFullName(relativePath) != null ? relativePath : null
		}
		
	}


	boolean isAssetPath(final String path) {
		final String relativePath = trimLeadingSlash(path)
		return relativePath && (manifest ? manifest.getProperty(relativePath) : AssetHelper.fileForFullName(relativePath) != null)
	}


	String asset(final Map attrs, final DefaultLinkGenerator linkGenerator) {
		String url = getResolvedAssetPath(attrs.file ?: attrs.src)

		if (! url) {
			return null
		}

		url = assetBaseUrl(null, NONE) + url
		if (! hasAuthority(url)) {
			def absolutePath = linkGenerator.handleAbsolute(attrs)

			if (absolutePath == null) {
				final String contextPath = attrs.contextPath?.toString() ?: linkGenerator.contextPath
				absolutePath = contextPath ?: linkGenerator.handleAbsolute(attrs) ?: ''
			}
			url = absolutePath + url
		}

		return url
	}

	String getConfigBaseUrl(final HttpServletRequest req, final ConfigObject conf = grailsApplication.config.grails.assets) {
		final def url = conf.url
		if(url instanceof Closure) {
			return url(req)
		}
		return url ?: null
	}

	String assetBaseUrl(final HttpServletRequest req, final UrlBase urlBase, final ConfigObject conf = grailsApplication.config.grails.assets) {
		final String url = getConfigBaseUrl(req, conf)
		if (url) {
			return url
		}

		final String mapping = assetMapping

		final String baseUrl
		switch (urlBase) {
			case SERVER_BASE_URL:
				baseUrl = grailsLinkGenerator.serverBaseURL ?: ''
				break
			case CONTEXT_PATH:
				baseUrl = trimToEmpty(grailsLinkGenerator.contextPath)
				break
			case NONE:
				baseUrl = ''
				break
		}

		def finalUrl = ensureEndsWith(new StringBuilder(baseUrl.length() + mapping.length() + 2).append(baseUrl), '/' as char)
				.append(mapping)
				.append('/' as char)
				.toString()
		return finalUrl
	}


	String makeServerURL(final DefaultLinkGenerator linkGenerator) {
		String serverUrl = linkGenerator.configuredServerBaseURL
		if (! serverUrl) {
			final GrailsWebRequest req = lookup()
			if (req) {
				serverUrl = getBaseUrlWithScheme(req.currentRequest).toString()
				if (!serverUrl && !Environment.isWarDeployed()) {
					serverUrl = "http://localhost:${System.getProperty('server.port') ?: '8080'}${linkGenerator.contextPath ?: ''}"
				}
			}
		}
		return serverUrl
	}


	private static String trimLeadingSlash(final String s) {
		if(!s || s[0] != '/') {
			return s
		}
		return s.substring(1)
	}
}
