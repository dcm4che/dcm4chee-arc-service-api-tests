//
/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4che.test.serviceapi.fetchforward;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import javax.inject.Inject;

import org.dcm4che.test.serviceapi.fetchforward.ArquillianExtendedLifecycleMethodExecuter.AfterUnDeploy;
import org.dcm4che.test.serviceapi.fetchforward.ArquillianExtendedLifecycleMethodExecuter.BeforeDeploy;
import org.dcm4che3.conf.api.DicomConfiguration;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.io.SAXReader;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Connection.Protocol;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.TCGroupConfigAEExtension;
import org.dcm4che3.net.TCGroupConfigAEExtension.DefaultGroup;
import org.dcm4che3.net.service.BasicCStoreSCUResp;
import org.dcm4chee.archive.conf.ArchiveAEExtension;
import org.dcm4chee.archive.conf.StoreAction;
import org.dcm4chee.archive.conf.StoreParam;
import org.dcm4chee.archive.dto.ArchiveInstanceLocator;
import org.dcm4chee.archive.dto.ExternalLocationTuple;
import org.dcm4chee.archive.dto.GenericParticipant;
import org.dcm4chee.archive.fetch.forward.FetchForwardCallBack;
import org.dcm4chee.archive.fetch.forward.FetchForwardService;
import org.dcm4chee.archive.store.StoreContext;
import org.dcm4chee.archive.store.StoreService;
import org.dcm4chee.archive.store.StoreSession;
import org.dcm4chee.storage.conf.Availability;
import org.dcm4chee.storage.conf.StorageSystem;
import org.dcm4chee.storage.conf.StorageSystemGroup;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Alexander Hoermandinger <alexander.hoermandinger@agfa.com>
 *
 */
@RunWith(Arquillian.class)
public class FetchForwardServiceTest {
    private static final Logger LOG = LoggerFactory.getLogger(FetchForwardServiceTest.class);
   
    @Inject
    private FetchForwardService fetchForwardService;
    
    @Inject
    private StoreService storeService;
    
    @Inject
    private DicomConfiguration config;
    
    @Inject
    private Device device;
    
    private static final String SOURCE_AET = "SOURCE_AET";
    
    private static final String INSTANCE_HEADER = "testdata/DB700C26_original.xml";
    
    
    @BeforeDeploy
    public static void beforeDeploy() {
        setupExternalDevice();
    }
    
    @AfterUnDeploy
    public static void afterUnDeploy() {
        stopExternalDevice();
    }
    
    @Deployment
    public static WebArchive createDeployment() {
        WebArchive war= ShrinkWrap.create(WebArchive.class, "test.war");
        war.addClass(FetchForwardServiceTest.class);
        war.addClass(ParamFactory.class);
        ITHelper.addDefaultDependenciesToWebArchive(war);
        war.addAsResource(INSTANCE_HEADER);
        
        war.as(ZipExporter.class).exportTo(
                new File("test.war"), true);
        return war;
    }
    
    private static void setupExternalDevice() {
        executeExtDeviceMethod("setupExternalDevice");
    }
    
    private static void stopExternalDevice() {
        executeExtDeviceMethod("stopExternalDevice");
    }
    
    /*
     * Calls methods on the ExternalDeviceControl class using reflection.
     * Reflection is used because the test class MUST NOT have any dependencies on ExternalDeviceControl.
     * If it would have dependencies then we would need to add the testing API (and everything related) to the
     * created container deployment as the test class is executed by Arquillian within the container.
     */
    private static void executeExtDeviceMethod(String name) {
        try {
            Class<?> c = Class.forName("org.dcm4che.test.serviceapi.fetchforward.ExternalDeviceControl");
            Method extDeviceMethod = c.getDeclaredMethod(name);
            extDeviceMethod.invoke(c);
        } catch (Exception e) {
            LOG.error("Error while executing external device method using reflection", e);
            throw new RuntimeException(e);
        }
    }
  
    @Test
    public void fetchInstanceFromExteralDevice() throws Exception {
        configureExtDevice("extdcm", "DCMEXT", "localhost", 11122);
        
        storeInstanceToDB();
        
        final List<ArchiveInstanceLocator> fetchedInstances = new ArrayList<>();
        
        FetchForwardCallBack fetchCallBack = new FetchForwardCallBack() {
            @Override
            public void onFetch(Collection<ArchiveInstanceLocator> instances,
                    BasicCStoreSCUResp basicCStoreSCUresp) {
                fetchedInstances.addAll(instances);
            }
        };

        List<ExternalLocationTuple> extLocationTuples = Arrays.asList(new ExternalLocationTuple("extdcm", Availability.NEARLINE));
        List<ArchiveInstanceLocator> refs = new ArrayList<ArchiveInstanceLocator>(Arrays.asList(
                new ArchiveInstanceLocator.Builder(
                        "1.2.840.10008.5.1.4.1.1.1", 
                        "1.2.40.0.13.1.1.1.10.231.161.39.20121128094010212.35980", 
                        "1.2.840.10008.1.2")
                .externalLocators(extLocationTuples)
                .studyInstanceUID("1.2.40.0.13.1.1.1.10.231.161.39.20121128094010212.35981")
                .build()));
        
        List<ArchiveInstanceLocator> failedInstances = fetchForwardService.fetchForward("DCM4CHEE", refs, fetchCallBack, fetchCallBack);
        
        Assert.assertTrue(failedInstances.isEmpty());
        Assert.assertEquals(1, fetchedInstances.size());
    }
    
    private void storeInstanceToDB() throws Exception {
        StoreParam storeParam = ParamFactory.createStoreParam();
        StoreSession session = storeService.createStoreSession(storeService); 
        session.setStoreParam(storeParam);
        StorageSystem storageSystem = new StorageSystem();
        storageSystem.setStorageSystemID("test_ss");        
        StorageSystemGroup grp = new StorageSystemGroup();
        grp.setGroupID("test_grp");
        grp.addStorageSystem(storageSystem);
        session.setStorageSystem(storageSystem);
        session.setSource(new GenericParticipant("localhost", "testidentity"));
        session.setRemoteAET(SOURCE_AET);
        session.setArchiveAEExtension(device.getApplicationEntity("DCM4CHEE")
                .getAEExtension(ArchiveAEExtension.class));

        StoreContext storeContext = storeService.createStoreContext(session);
        storeContext.setAttributes(load(INSTANCE_HEADER));
        storeContext.setStoreAction(StoreAction.UPDATEDB);
        storeService.updateDB(storeContext);
    }
    
    private static Attributes load(String name) throws Exception {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return SAXReader.parse(cl.getResource(name).toString());
    }
    
    private void configureExtDevice(String newDevice, String aet, String hostname, int port) throws ConfigurationException {
        LOG.debug("Create Connection dicom");
        Connection dicom = new Connection("dicom", hostname, port);
        dicom.setProtocol(Protocol.DICOM);
        dicom.setConnectionInstalled(true);
        dicom.setTlsProtocols(new String[] { "TLSv1", "SSLv3" });
        LOG.debug("Connection dicom:\n{}", dicom);

        LOG.debug("Create ApplicationEntity {}", hostname);
        ApplicationEntity applicationEntity = new ApplicationEntity(aet);
        applicationEntity.setAssociationAcceptor(true);
        applicationEntity.setAssociationInitiator(true);
        List<Connection> listConnections = new ArrayList<Connection>();
        listConnections.add(dicom);
        applicationEntity.setConnections(listConnections);
        EnumSet<DefaultGroup> scpGroups = EnumSet.of(DefaultGroup.QUERY, DefaultGroup.RETRIEVE, DefaultGroup.STORAGE,
                DefaultGroup.STORAGE_COMMITMENT);
        EnumSet<DefaultGroup> scuGroups = EnumSet.of(DefaultGroup.STORAGE, DefaultGroup.STORAGE_COMMITMENT);
        TCGroupConfigAEExtension tCGroupConfigAEExtension = new TCGroupConfigAEExtension(scpGroups, scuGroups);
        applicationEntity.addAEExtension(tCGroupConfigAEExtension);
        LOG.debug("ApplicationEntity {}:\n{}", newDevice.toUpperCase(), applicationEntity);

        LOG.debug("Create Device {}", newDevice);
        Device extDevice = new Device(newDevice);
        extDevice.addConnection(dicom);
        extDevice.addApplicationEntity(applicationEntity);
        LOG.debug("Device {}:\n{}", newDevice, extDevice);

        config.merge(extDevice);
    }
   
}
