/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*   Data In Motion - initial API and implementation
**********************************************************************/
package org.eclipse.sensinact.core.twin.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.URIMappingRegistryImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.sensinact.core.command.impl.ActionHandler;
import org.eclipse.sensinact.core.emf.model.EMFModel;
import org.eclipse.sensinact.core.emf.twin.SensinactEMFProvider;
import org.eclipse.sensinact.core.emf.twin.SensinactEMFService;
import org.eclipse.sensinact.core.emf.util.EMFTestUtil;
import org.eclipse.sensinact.core.model.impl.SensinactModelManagerImpl;
import org.eclipse.sensinact.core.model.nexus.ModelNexus;
import org.eclipse.sensinact.core.notification.impl.NotificationAccumulator;
import org.eclipse.sensinact.model.core.provider.DynamicProvider;
import org.eclipse.sensinact.model.core.provider.Provider;
import org.eclipse.sensinact.model.core.provider.ProviderPackage;
import org.eclipse.sensinact.model.core.provider.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.osgi.util.promise.PromiseFactory;

/**
 * Two APIs take an {@link EClass} for a service, and both ignore it when the
 * model <em>declares</em> the service name. They throw nothing and log nothing,
 * so the caller believes the service is typed, and it is not.
 *
 * <ul>
 * <li>{@code EMFModel.createServiceWithEClass(name, eClass)} builds an
 * unattached {@code ServiceImpl} handle over the EClass and writes nothing into
 * the model EClass. Its one caller in the gateway is {@code SetValueCommand},
 * for a value push that carries a {@code DataUpdateDto.serviceEClass} for a
 * service the model does <em>not</em> declare.</li>
 * <li>{@code SensinactEMFProvider.getOrCreateService(name, eClass)} reaches
 * {@code ModelNexus.createServiceInstance}, which for a declared name
 * instantiates {@code serviceFeature.getEType()} and drops the EClass of its
 * own signature.</li>
 * </ul>
 *
 * {@code admin} is declared by {@code Provider} for every model there is, so no
 * EClass-taking API can retype it. The only route that works is a pushed or
 * updated <em>instance</em>, through {@code EMFCompareUtil.copyOldService}.
 *
 * <p>
 * Each test below accepts <b>either</b> acceptable outcome: an
 * {@link IllegalArgumentException} that refuses the call, or the EClass being
 * honoured. Only the third outcome - a silent success that changes nothing -
 * fails the test. The last test is the contrast and passes today: the same API
 * honours the EClass for a name the model does not declare.
 */
@ExtendWith(MockitoExtension.class)
public class DeclaredServiceEClassTest {

    private static final String TEST_MODEL = "testmodel";
    private static final String TEST_PROVIDER = "testprovider";
    private static final String ADMIN = "admin";

    @Mock
    NotificationAccumulator accumulator;

    @Mock
    ActionHandler actionHandler;

    private final PromiseFactory promiseFactory = new PromiseFactory(PromiseFactory.inlineExecutor());

    private ResourceSet resourceSet;
    private ModelNexus nexus;
    private SensinactModelManagerImpl manager;
    private SensinactDigitalTwinImpl twinImpl;
    private EPackage ePackage;

    @BeforeEach
    void start() throws IOException {
        resourceSet = EMFTestUtil.createResourceSet();
        nexus = new ModelNexus(resourceSet, ProviderPackage.eINSTANCE, () -> accumulator, actionHandler);
        manager = new SensinactModelManagerImpl(nexus);
        twinImpl = new SensinactDigitalTwinImpl(nexus, promiseFactory);

        final URI providerPackageURI = URI.createURI(ProviderPackage.eNS_URI);
        URIMappingRegistryImpl.INSTANCE.put(
                URI.createURI("https://eclipse.org/../../../models/src/main/resources/model/sensinact.ecore"),
                providerPackageURI);

        XMLResource.URIHandler handler = new XMLResource.URIHandler() {

            @Override
            public URI deresolve(URI uri) {
                return uri;
            }

            @Override
            public URI resolve(URI uri) {
                if (uri.lastSegment().equals("sensinact.ecore")) {
                    return providerPackageURI.appendFragment(uri.fragment());
                }
                return uri;
            }

            @Override
            public void setBaseURI(URI uri) {
                // nothing to do
            }
        };

        Resource extendedPackageResource = resourceSet
                .createResource(URI.createURI("https://eclipse.org/sensinact/test/1.0"));
        InputStream in = getClass().getResourceAsStream("/model/extended.ecore");
        assertNotNull(in);
        extendedPackageResource.load(in, Collections.singletonMap(XMLResource.OPTION_URI_HANDLER, handler));
        ePackage = (EPackage) extendedPackageResource.getContents().get(0);
        assertNotNull(ePackage);
    }

    private EClass testAdmin() {
        return (EClass) ePackage.getEClassifier("TestAdmin");
    }

    /**
     * {@code createServiceWithEClass("admin", TestAdmin)} reports success - the
     * handle it returns even carries the {@code testAdmin} resource - and changes
     * nothing. The model keeps the inherited {@code Provider.admin} reference, so
     * every provider of that model still gets a plain {@code Admin}.
     */
    @Test
    void createServiceWithEClass_onTheDeclaredAdmin_mustNotSilentlyDoNothing() {
        EMFModel model = manager.createModel(TEST_MODEL).build();

        try {
            model.createServiceWithEClass(ADMIN, testAdmin());
        } catch (IllegalArgumentException | IllegalStateException e) {
            // acceptable: the call refuses a service name the model declares
            return;
        }

        // it did not refuse, so it must have been honoured
        assertEquals(testAdmin(), manager.getModel(TEST_MODEL).getServices().get(ADMIN).getServiceEClass(),
                "the model still declares the inherited Admin after createServiceWithEClass");

        SensinactEMFProvider provider = twinImpl.createProvider(TEST_MODEL, TEST_PROVIDER);
        assertEquals(testAdmin(), provider.getEMFProvider().getAdmin().eClass(),
                "the provider was built with a plain Admin");
    }

    /**
     * The same for the live provider: {@code getOrCreateService("admin", X)}
     * returns the existing plain {@code Admin} and never looks at X.
     */
    @Test
    void getOrCreateService_onTheDeclaredAdmin_mustNotSilentlyIgnoreTheEClass() {
        manager.createModel(TEST_MODEL).build();
        SensinactEMFProvider provider = twinImpl.createProvider(TEST_MODEL, TEST_PROVIDER);

        SensinactEMFService service;
        try {
            service = provider.getOrCreateService(ADMIN, testAdmin());
        } catch (IllegalArgumentException | IllegalStateException e) {
            // acceptable: the call refuses to retype a declared service
            return;
        }

        assertEquals(testAdmin(), service.getServiceEClass(),
                "getOrCreateService returned a service typed with the declared EClass, not the one asked for");
        assertEquals(testAdmin(), provider.getEMFProvider().getAdmin().eClass(),
                "the stored admin keeps its declared type");
    }

    /**
     * It is not an admin-only defect. {@code testService1} is declared by
     * {@code TemperatureSensor} and does not exist on a fresh provider, so the call
     * really <em>creates</em> the instance - and still drops the EClass of its own
     * signature, although {@code TestServiceWithComplex} is a subtype of the
     * declared {@code TestService1}.
     */
    @Test
    void getOrCreateService_onADeclaredService_mustNotSilentlyIgnoreTheEClass() {
        EClass subType = (EClass) ePackage.getEClassifier("TestServiceWithComplex");

        Provider sensor = (Provider) EcoreUtil.create((EClass) ePackage.getEClassifier("TemperatureSensor"));
        sensor.setId("sensor");
        nexus.save(sensor);

        SensinactEMFProvider provider = twinImpl.getProvider("sensor");
        assertNotNull(provider);

        SensinactEMFService service;
        try {
            service = provider.getOrCreateService("testService1", subType);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // acceptable: the call refuses to retype a declared service
            return;
        }

        assertEquals(subType, service.getServiceEClass(),
                "getOrCreateService instantiated the declared EType and dropped the EClass it was given");
    }

    /**
     * The contrast, and it passes today: for a name the model does <em>not</em>
     * declare, on a {@link DynamicProvider}, the very same method honours the
     * EClass. That branch is the only one in
     * {@code ModelNexus.createServiceInstance} that reads the parameter.
     */
    @Test
    void getOrCreateService_onAnUndeclaredName_honoursTheEClass() {
        EClass serviceType = (EClass) ePackage.getEClassifier("TestService1");

        DynamicProvider sensor = (DynamicProvider) EcoreUtil
                .create((EClass) ePackage.getEClassifier("DynamicTemperatureSensor"));
        sensor.setId("dynamicsensor");
        nexus.save(sensor);

        SensinactEMFProvider provider = twinImpl.getProvider("dynamicsensor");
        assertNotNull(provider);

        SensinactEMFService service = provider.getOrCreateService("extra", serviceType);
        assertEquals(serviceType, service.getServiceEClass());

        Provider stored = provider.getEMFProvider();
        if (!(stored instanceof DynamicProvider)) {
            fail("expected a DynamicProvider");
        }
        Service extra = ((DynamicProvider) stored).getServices().get("extra");
        assertNotNull(extra, "the service was not stored");
        assertEquals(serviceType, extra.eClass());
    }
}
