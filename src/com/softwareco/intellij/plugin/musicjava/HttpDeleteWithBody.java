package com.softwareco.intellij.plugin.musicjava;

import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;

import java.net.URI;

public class HttpDeleteWithBody extends HttpEntityEnclosingRequestBase {

    public static final String METHOD_NAME = "DELETE";

    public HttpDeleteWithBody() {
    }

    public HttpDeleteWithBody(final URI uri) { this.setURI(uri); }

    public HttpDeleteWithBody(final String uri) {
        this.setURI(URI.create(uri));
    }

    public String getMethod() { return "DELETE"; }
}
