/*
 * Copyright 2014 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at http://www.eclipse.org/legal/epl-v10.html
 */
package io.liveoak.filesystem;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.liveoak.filesystem.extension.FilesystemExtension;
import io.liveoak.testtools.AbstractHTTPResourceTestCase;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Bob McWhirter
 */
public class HTTPFilesystemResourceTest extends AbstractHTTPResourceTestCase {

    static {
        setProjectRoot(HTTPFilesystemResourceTest.class);
        applicationDirectory = projectRoot;
        try {
            installTestApp();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @BeforeClass
    public static void setup() throws Exception {
        loadExtension("fs", new FilesystemExtension());
        installTestAppResource("fs", "files", JsonNodeFactory.instance.objectNode());
    }

    @Test
    public void testEnumerateRoot() throws Exception {

        HttpGet get = new HttpGet("http://localhost:8080/testApp/files");
        get.addHeader("Accept", "application/json");

        try {
            System.err.println("DO GET");
            CloseableHttpResponse result = httpClient.execute(get);
            System.err.println("=============>>>");
            System.err.println(result);

            HttpEntity entity = result.getEntity();
            if (entity.getContentLength() > 0) {
                entity.writeTo(System.err);
            }
            System.err.println("\n<<<=============");
            assertThat(result.getStatusLine().getStatusCode()).isEqualTo(200);

        } finally {
            httpClient.close();
        }

    }

    @Test
    public void testReadChild() throws Exception {
        HttpGet get = new HttpGet("http://localhost:8080/testApp/files/test-file1.txt");
        get.addHeader("Accept", "text/*");

        try {
            System.err.println("DO GET");
            CloseableHttpResponse result = httpClient.execute(get);
            System.err.println("=============>>>");
            System.err.println(result);

            HttpEntity entity = result.getEntity();
            if (entity.getContentLength() > 0) {
                entity.writeTo(System.err);
            }
            System.err.println("\n<<<=============");
            assertThat(result.getStatusLine().getStatusCode()).isEqualTo(200);

        } finally {
            httpClient.close();
        }
    }

}
