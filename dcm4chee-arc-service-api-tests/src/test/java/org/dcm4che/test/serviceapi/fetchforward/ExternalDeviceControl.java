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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;

import org.dcm4che.test.common.TestToolFactory;
import org.dcm4che.test.tool.externaldevice.BehavioralCStoreSCP;
import org.dcm4che.test.tool.externaldevice.BehavioralCStoreSCP.InterceptableCStoreSCPImpl;
import org.dcm4che.test.tool.externaldevice.BehavioralStgCmtSCP;
import org.dcm4che.test.tool.externaldevice.BehavioralStgCmtSCP.BehavioralStgCmtSCPImpl;
import org.dcm4che.test.tool.externaldevice.ExternalDeviceTool;
import org.dcm4che.test.tool.externaldevice.ExternalDeviceToolConfig;
import org.dcm4che3.conf.api.DicomConfiguration;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.dicom.DicomConfigurationBuilder;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.IncompatibleConnectionException;
import org.dcm4che3.tool.storescu.test.StoreTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to control configuring and starting external device used by tests.
 * 
 * @author Alexander Hoermandinger <alexander.hoermandinger@agfa.com>
 */
public class ExternalDeviceControl {
    private static final Logger LOG = LoggerFactory.getLogger(ExternalDeviceControl.class);
    
    private static final String extDeviceName = "dcmext";
    private static final String extDeviceAeTitle = "DCMEXT";
    
    private static final String archiveDeviceName = "DCM4CHEE";
    private static final String archiveDeviceAeTitle = "DCM4CHEE_FETCH";
    
    private static ExternalDeviceTool runningExtDevice;
    
    public static void setupExternalDevice() throws Exception {
        startExternalDevice();
        sendDataToExternalDevice();
    }
    
    public static void startExternalDevice() throws IOException, ConfigurationException {
        LOG.info("Starting external device");
        
        Path testStorageDir = Files.createTempDirectory("fetchForwardTest");
        DicomConfiguration dicomCfg = getDicomConfig();
        ExternalDeviceToolConfig extDeviceToolCfg = TestToolFactory.createExternalDeviceToolConfig(
                dicomCfg, testStorageDir.toFile(), extDeviceName, extDeviceAeTitle,
                archiveDeviceName, archiveDeviceAeTitle);

        BehavioralStgCmtSCPImpl stgCmtSCP = new BehavioralStgCmtSCP.Builder()
                .qrSCPConfig(extDeviceToolCfg)
                .build();

        InterceptableCStoreSCPImpl cStoreSCP = new BehavioralCStoreSCP.Builder()
                .qrSCPConfig(extDeviceToolCfg)
                .build();

        ExternalDeviceTool extDevice = new ExternalDeviceTool.Builder()
                .toolConfig(extDeviceToolCfg)
                .cStoreSCP(cStoreSCP)
                .stgCmtSCP(stgCmtSCP)
                .build();
        extDevice.start();
        runningExtDevice = extDevice;
    }
    
    public static void stopExternalDevice() {
        LOG.info("Stopping external device");
        
        if(runningExtDevice != null) {
          runningExtDevice.stop();
      }
    }
    
    private static DicomConfiguration getDicomConfig() throws IOException, ConfigurationException {
        File localConfigFile = Files.createTempFile("tempdefaultconfig", "json").toFile();
        
        Files.copy(FetchForwardServiceTest.class.getClassLoader()
                .getResourceAsStream("defaultConfig.json"), 
                localConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        localConfigFile.deleteOnExit();
        return DicomConfigurationBuilder.newJsonConfigurationBuilder(localConfigFile.getPath()).build();
    }
    
    public static void sendDataToExternalDevice() throws ConfigurationException, IOException, InterruptedException, IncompatibleConnectionException, GeneralSecurityException {
        Device storeToolDevice = getDicomConfig().findDevice("storescu");
        Connection storeToolConn = storeToolDevice.connectionWithEqualsRDN(new Connection("dicom", "0.0.0.0"));
        
        String userDir = System.getProperty("user.dir");
        File baseStoreToolDir = new File(userDir, "src/test/resources/testdata");
        StoreTool storeScuTool = new StoreTool("localhost", 11122, "DCMEXT", baseStoreToolDir, storeToolDevice, "STORESCU", storeToolConn);
        storeScuTool.store("Store test instance to external device tool", "DB700C26_original.dcm");
    }
}
