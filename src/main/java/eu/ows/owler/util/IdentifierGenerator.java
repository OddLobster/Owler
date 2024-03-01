package eu.ows.owler.util;

import java.net.URI;
import java.net.URISyntaxException;
import org.apache.commons.codec.digest.DigestUtils;

public class IdentifierGenerator {

    public static String generateUrlId(String url) throws URISyntaxException {
        String normalizedURL = normalizeURL(url);
        return DigestUtils.sha256Hex(normalizedURL);
    }

    private static String normalizeURL(String url) throws URISyntaxException {
        try {
            URI uri = URI.create(url);
            String url_no_fragment = uri.getScheme() + ":" + uri.getRawSchemeSpecificPart();
            URI uri_no_fragment = URI.create(url_no_fragment);
            return uri_no_fragment.normalize().toString();
        } catch (IllegalArgumentException e) {
            throw new URISyntaxException(url, e.getMessage());
        }
    }
}
