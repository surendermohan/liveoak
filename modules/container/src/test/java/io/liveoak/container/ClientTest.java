/*
 * Copyright 2014 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at http://www.eclipse.org/legal/epl-v10.html
 */
package io.liveoak.container;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.liveoak.common.DefaultReturnFields;
import io.liveoak.common.codec.DefaultResourceState;
import io.liveoak.container.tenancy.InternalApplication;
import io.liveoak.container.tenancy.InternalApplicationExtension;
import io.liveoak.spi.RequestContext;
import io.liveoak.spi.ReturnFields;
import io.liveoak.spi.client.Client;
import io.liveoak.spi.client.ClientResourceResponse;
import io.liveoak.spi.state.ResourceState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;

/**
 * @author Bob McWhirter
 */
public class ClientTest {

    private LiveOakSystem system;
    private Client client;
    private InternalApplication application;


    @Before
    public void setUp() throws Exception {
        this.system = LiveOakFactory.create();
        this.client = this.system.client();

        // LIVEOAK-295 ... make sure system services have all started before performing programmatic application deployment
        this.system.awaitStability();

        this.application = this.system.applicationRegistry().createApplication("testApp", "Test Application");
        this.system.extensionInstaller().load("db", new InMemoryDBExtension());
        this.application.extend("db");

        InMemoryDBResource db = (InMemoryDBResource) this.system.service(InMemoryDBExtension.resource("testApp", "db"));
        db.addMember(new InMemoryCollectionResource(db, "people"));
        db.addMember(new InMemoryCollectionResource(db, "dogs"));

    }

    @After
    public void shutdown() throws Exception {
        this.system.stop();
    }

    @Test
    public void tests() throws Throwable {
        System.out.println("TEST #1 - Read");
        ReturnFields fields = new DefaultReturnFields("*,members(*,members(*))");

        RequestContext requestContext = new RequestContext.Builder().returnFields(fields).build();
        ResourceState result = this.client.read(requestContext, "/testApp");
        assertThat(result).isNotNull();

        List<ResourceState> members = result.members();
        assertThat(members).isNotEmpty();

        ResourceState db = members.stream().filter((e) -> e.id().equals("db")).findFirst().get();
        assertThat(db).isNotNull();

        ResourceState people = db.members().stream().filter((e) -> e.id().equals("people")).findFirst().get();
        assertThat(people).isNotNull();

        ResourceState dogs = db.members().stream().filter((e) -> e.id().equals("dogs")).findFirst().get();
        assertThat(dogs).isNotNull();


        System.out.println("TEST #2 - Create");
        fields = new DefaultReturnFields("*");

        requestContext = new RequestContext.Builder().returnFields(fields).build();
        ResourceState bob = new DefaultResourceState("bob");
        bob.putProperty("name", "Bob McWhirter");

        result = this.client.create(requestContext, "/testApp/db/people", bob);
        assertThat(result).isNotNull();
        assertThat(result.getProperty("name")).isEqualTo("Bob McWhirter");

        people = this.client.read(requestContext, "/testApp/db/people");
        assertThat(people).isNotNull();

        ResourceState foundBob = people.members().stream().filter((e) -> e.id().equals("bob")).findFirst().get();
        assertThat(foundBob).isNotNull();
        assertThat(foundBob.id()).isEqualTo("bob");
        assertThat(foundBob.getPropertyNames()).hasSize(0);

        foundBob = this.client.read(requestContext, "/testApp/db/people/bob");
        assertThat(foundBob).isNotNull();
        assertThat(foundBob.getProperty("name")).isEqualTo("Bob McWhirter");


        System.out.println("TEST #3 - Update");
        foundBob = this.client.read(requestContext, "/testApp/db/people/bob");
        assertThat(foundBob).isNotNull();
        assertThat(foundBob.getProperty("name")).isEqualTo("Bob McWhirter");

        bob = new DefaultResourceState("bob");
        bob.putProperty("name", "Robert McWhirter");

        result = this.client.update(requestContext, "/testApp/db/people/bob", bob);
        assertThat(result).isNotNull();
        assertThat(result.getProperty("name")).isEqualTo("Robert McWhirter");

        foundBob = this.client.read(requestContext, "/testApp/db/people/bob");
        assertThat(foundBob).isNotNull();
        assertThat(foundBob.getProperty("name")).isEqualTo("Robert McWhirter");


        System.out.println("TEST #4 - Read from Resource");
        requestContext = new RequestContext.Builder().build();
        bob = new DefaultResourceState("proxybob");
        bob.putProperty("name", "Bob McWhirter");
        ResourceState createdBob = this.client.create(requestContext, "/testApp/db/people", bob);
        assertThat(createdBob).isNotNull();

        this.system.extensionInstaller().load("proxy", new ProxyExtension());
        InternalApplicationExtension ext = this.application.extend("proxy", JsonNodeFactory.instance.objectNode().put("blocking", false));
        this.system.awaitStability();

        result = this.client.read(requestContext, "/testApp/proxy");
        assertThat(result.getPropertyNames()).contains("name");
        assertThat(result.getProperty("name")).isEqualTo("Bob McWhirter");

        ext.remove();
        this.system.awaitStability();


        System.out.println("TEST #5 - Read from BlockingResource");
        requestContext = new RequestContext.Builder().build();
        bob = new DefaultResourceState("blockingproxybob");
        bob.putProperty("name", "Bob McWhirter");
        createdBob = this.client.create(requestContext, "/testApp/db/people", bob);
        assertThat(createdBob).isNotNull();
        ext = this.application.extend("proxy", JsonNodeFactory.instance.objectNode().put("blocking", true));
        this.system.awaitStability();

        result = this.client.read(requestContext, "/testApp/proxy");
        assertThat(result.getPropertyNames()).contains("name");
        assertThat(result.getProperty("name")).isEqualTo("Bob McWhirter");

        ext.remove();
        this.system.awaitStability();


        System.out.println("TEST #6 - Simple Async");
        requestContext = new RequestContext.Builder().build();
        CompletableFuture<ClientResourceResponse> future = new CompletableFuture<>();

        this.client.read(requestContext, "/testApp/db/dogs", clientResourceResponse -> future.complete(clientResourceResponse));

        ClientResourceResponse response = future.get();
        assertThat(response).isNotNull();
        assertThat(response.state().id()).isEqualTo("dogs");


        System.out.println("TEST #7 - Nested Async");
        RequestContext reqCtxt = new RequestContext.Builder().build();
        CompletableFuture<ClientResourceResponse> nestedFuture = new CompletableFuture<>();

        this.client.read(reqCtxt, "/testApp/db/dogs", clientResourceResponse1 -> {
            assertThat(clientResourceResponse1.state().id()).isEqualTo("dogs");

            this.client.read(reqCtxt, "/testApp/db/people", clientResourceResponse2 -> {
                assertThat(clientResourceResponse2.state().id()).isEqualTo("people");

                this.client.read(reqCtxt, "/testApp/db/dogs", clientResourceResponse3 -> {
                    assertThat(clientResourceResponse3.state().id()).isEqualTo("dogs");

                    this.client.read(reqCtxt, "/testApp/db/people", clientResourceResponse4 -> {
                        nestedFuture.complete(clientResourceResponse4);
                    });
                });
            });


        });

        response = nestedFuture.get();
        assertThat(response).isNotNull();
        assertThat(response.state().id()).isEqualTo("people");


        System.out.println("TEST #8 - Nested Async");
        RequestContext nestedCtx = new RequestContext.Builder().build();
        CompletableFuture<ResourceState> nestedFuture2 = new CompletableFuture<>();

        this.client.read(nestedCtx, "/testApp/db/dogs", clientResourceResponse1 -> {
            assertThat(clientResourceResponse1.state().id()).isEqualTo("dogs");

            this.client.read(nestedCtx, "/testApp/db/people", clientResourceResponse2 -> {
                assertThat(clientResourceResponse2.state().id()).isEqualTo("people");

                this.client.read(nestedCtx, "/testApp/db/dogs", clientResourceResponse3 -> {
                    assertThat(clientResourceResponse3.state().id()).isEqualTo("dogs");

                    try {
                        nestedFuture2.complete(this.client.read(nestedCtx, "/testApp/db/people"));
                    } catch (Exception e) {
                        e.printStackTrace();
                        fail();
                    }
                });
            });
        });


        ResourceState state = nestedFuture2.get();

        assertThat(state).isNotNull();
        assertThat(state.id()).isEqualTo("people");
    }

}
