package org.visallo.web;

import org.visallo.webster.Handler;
import org.visallo.webster.HandlerChain;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.visallo.webster.RequestResponseHandler;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import net.sf.uadetector.ReadableUserAgent;
import net.sf.uadetector.UserAgentStringParser;
import net.sf.uadetector.VersionNumber;
import net.sf.uadetector.service.UADetectorServiceFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class UserAgentFilter implements RequestResponseHandler {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(UserAgentFilter.class);

    private static final Map<String, VersionNumber> MINIMUM_VERSION_BROWSERS = new HashMap<String, VersionNumber>();

    static {
        MINIMUM_VERSION_BROWSERS.put("IE", new VersionNumber("11"));
        MINIMUM_VERSION_BROWSERS.put("Firefox", new VersionNumber("52"));
    }

    private final UserAgentStringParser parser = UADetectorServiceFactory.getResourceModuleParser();

    private final Cache<String, String> cache = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(2, TimeUnit.HOURS)
            .build();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse httpServletResponse, HandlerChain handlerChain) throws Exception {
        String message = isUnsupported(request.getHeader("User-Agent"));
        if (!message.equals("")) {
            httpServletResponse.setContentType("text/plain");
            PrintWriter writer = httpServletResponse.getWriter();
            writer.println(message);
            writer.close();
            writer.flush();
            return;
        }
        handlerChain.next(request, httpServletResponse);
    }

    private String isUnsupported(String userAgentString) {
        String message = cache.getIfPresent(userAgentString);
        if (message == null) {
            ReadableUserAgent userAgent = parser.parse(userAgentString);
            message = isUnsupported(userAgent);
            cache.put(userAgentString, message);
        }
        return message;
    }

    private String isUnsupported(ReadableUserAgent userAgent) {
        if (MINIMUM_VERSION_BROWSERS.containsKey(userAgent.getName())) {
            VersionNumber minimumVersion = MINIMUM_VERSION_BROWSERS.get(userAgent.getName());
            if (userAgent.getVersionNumber().compareTo(minimumVersion) < 0) {
                String message = getUnsupportedMessage(userAgent);
                LOGGER.warn(message);
                return message;
            }
        }
        return "";
    }

    private String getUnsupportedMessage(ReadableUserAgent userAgent) {
        VersionNumber minimumVersion = MINIMUM_VERSION_BROWSERS.get(userAgent.getName());
        if (minimumVersion != null) {
            return userAgent.getName() + " " + userAgent.getVersionNumber().toVersionString() + " is not supported. Please upgrade to at least version " + minimumVersion.toVersionString() + ".";
        }
        return userAgent.getName() + " " + userAgent.getVersionNumber().toVersionString() + " is not supported.";
    }
}
