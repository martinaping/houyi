package com.ruoran.houyi.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class InternalApiTokenFilterTest {

    @Test
    void keepsLegacyBehaviorWhenTokenIsNotConfigured() throws Exception {
        InternalApiTokenFilter filter = new InternalApiTokenFilter("");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/open/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void rejectsProtectedEndpointWithoutToken() throws Exception {
        InternalApiTokenFilter filter = new InternalApiTokenFilter("s3cret-token");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/open/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void acceptsProtectedEndpointWithToken() throws Exception {
        InternalApiTokenFilter filter = new InternalApiTokenFilter("s3cret-token");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/open/status");
        request.addHeader(InternalApiTokenFilter.HEADER, "s3cret-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void leavesStatusEndpointPublic() throws Exception {
        InternalApiTokenFilter filter = new InternalApiTokenFilter("s3cret-token");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }
}
