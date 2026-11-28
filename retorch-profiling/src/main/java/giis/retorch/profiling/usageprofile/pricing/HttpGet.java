/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.pricing;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;

/** Opens a URL and returns its body; injectable so the price providers can be tested without network. */
@FunctionalInterface
public interface HttpGet {

    /** The body of a successful (HTTP 200) response; the caller closes it. */
    InputStream open(String url) throws IOException;

    /** The real thing, over {@link HttpURLConnection}. */
    static HttpGet standard() {
        return url -> {
            HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
            c.setConnectTimeout(15_000);
            c.setReadTimeout(120_000);
            c.setRequestProperty("Accept", "application/json");
            if (c.getResponseCode() != 200) {
                throw new IOException("HTTP " + c.getResponseCode() + " from " + url.split("\\?")[0]);
            }
            return c.getInputStream();
        };
    }
}
