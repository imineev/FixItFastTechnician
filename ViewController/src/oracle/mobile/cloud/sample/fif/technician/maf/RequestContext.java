package oracle.mobile.cloud.sample.fif.technician.maf;

import java.util.HashMap;

import oracle.adfmf.dc.ws.rest.RestServiceAdapter;


/**
 * The RequestContext class wraps the REST Service Adapter request configuration, including the
 * http method, the payload, header parameters, rest uri and MAF REST connection name. It helps
 * developer to assemble the information needed for a successful REST call.
 *
 * @author Frank Nimphius
 * @copyright Copyright (c) 2015 Oracle. All rights reserved.
 */
public class RequestContext {

    /**
     * ENUM class to select http method from to use with REST call
     */
    public enum HttpMethod {
        GET  {
            @Override
            public String toString() {
                return RestServiceAdapter.REQUEST_TYPE_GET;
            }
        },
        PUT  {
            @Override
            public String toString() {
                return RestServiceAdapter.REQUEST_TYPE_PUT;
            }
        },
        POST  {
            @Override
            public String toString() {
                return RestServiceAdapter.REQUEST_TYPE_POST;
            }
        },
        DELETE  {
            @Override
            public String toString() {
                return RestServiceAdapter.REQUEST_TYPE_DELETE;
            }
        },
        PATCH  {
            @Override
            public String toString() {
                return "PATCH";
            }
        }
    };

    private Object payload = "";
    private String connectionName = "";
    private String requestURI = "";

    private HttpMethod httpMethod = HttpMethod.GET;
    private HashMap httpHeaders = new HashMap();

    private int retryLimit = 0;

    public RequestContext() {
        super();
    }

    /**
     * Payload can be of type String or byte[]
     * @param payload
     */
    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public Object getPayload() {
        return payload;
    }

    /**
     * The MAF REST connection name is require. The REST connection name is defined in the connections.xml file in MAF and
     * holds the root URL for the REST service. For MCS this usually is the mobile backend REST root URL.
     * @param connectionName
     */
    public void setConnectionName(String connectionName) {
        this.connectionName = connectionName;
    }

    public String getConnectionName() {
        return connectionName;
    }

    /**
     * The relative addressing that is appended the REST root URL to build the full REST URL pointing to a resource. The
     * URI contains Query Parameters if needed.
     * @param requestURI
     */
    public void setRequestURI(String requestURI) {
        this.requestURI = requestURI;
    }

    public String getRequestURI() {
        return requestURI;
    }

    /**
     * GET, POST, PUT, DELETE and PATCH method to define the operation to be executed on a MCS REST resource. MAF 2.1
     * @param httpMethod
     */
    public void setHttpMethod(RequestContext.HttpMethod httpMethod) {
        this.httpMethod = httpMethod;
    }

    public RequestContext.HttpMethod getHttpMethod() {
        return httpMethod;
    }

    public void setHttpHeaders(HashMap httpHeaders) {
        this.httpHeaders = httpHeaders;
    }

    public HashMap getHttpHeaders() {
        return httpHeaders;
    }

    public void setRetryLimit(int retryLimit) {
        this.retryLimit = retryLimit;
    }

    public int getRetryLimit() {
        return retryLimit;
    }
}
