package io.github.rkaushikdev.garterhole.http;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Escaping one part of a URL the way a browser does: a space becomes
 * {@code %20} rather than {@code +}, and the handful of punctuation marks a
 * path segment may carry as they are are left alone.
 */
public final class UriComponents {

    private UriComponents() {}

    /**
     * @param text a value to put into a path segment
     * @return the value escaped the way {@code encodeURIComponent} escapes it
     */
    public static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%21", "!")
                .replace("%27", "'")
                .replace("%28", "(")
                .replace("%29", ")")
                .replace("%7E", "~");
    }

    /**
     * @param text a path segment as it arrived
     * @return the segment with its escapes undone, a plus sign left as it is
     * @throws IllegalArgumentException when an escape is malformed
     */
    public static String decode(String text) {
        return URLDecoder.decode(text.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    /**
     * @param text a query string value as it arrived
     * @return the value with its escapes undone and each plus sign read as a space
     * @throws IllegalArgumentException when an escape is malformed
     */
    public static String decodeQuery(String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }
}
