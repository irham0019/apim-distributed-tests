/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.apim.deployment.lifecycle;


import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.apim.bean.APICreationRequestBean;
import org.wso2.apim.bean.APILifeCycleState;
import org.wso2.apim.bean.APILifeCycleStateRequest;
import org.wso2.apim.clients.APIPublisherRestClient;
import org.wso2.apim.clients.APIStoreRestClient;
import org.wso2.apim.exception.APIManagerIntegrationTestException;
import org.wso2.carbon.apimgt.api.model.APIIdentifier;
import org.wso2.carbon.automation.test.utils.http.client.HttpResponse;
import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.xpath.XPathExpressionException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Publish a API. Copy and create a new version, publish  the new version and deprecate the old version,
 * test invocation of both old and new API versions."
 */
public class AccessibilityOfDeprecatedOldAPIAndPublishedCopyAPITestCase extends APIManagerLifecycleBaseTest {
    private final String API_NAME = "DeprecatedAPITest";
    private final String API_CONTEXT = "DeprecatedAPI";
    private final String API_TAGS = "testTag1, testTag2, testTag3";
    private final String API_END_POINT_POSTFIX_URL = "jaxrs_basic/services/customers/customerservice/";
    private final String API_DESCRIPTION = "This is test API create by API manager integration test";
    private final String API_END_POINT_METHOD = "/customers/123";
    private final String API_RESPONSE_DATA = "<id>123</id><name>John</name></Customer>";
    private final String API_VERSION_1_0_0 = "1.0.0";
    private final String API_VERSION_2_0_0 = "2.0.0";
    private final String APPLICATION_NAME = "AccessibilityOfDeprecatedOldAPIAndPublishedCopyAPITestCase";
    private String apiEndPointUrl;
    private APIIdentifier apiIdentifierAPI1Version1;
    private APIIdentifier apiIdentifierAPI1Version2;
    private String providerName;
    private APICreationRequestBean apiCreationRequestBean;
    private APIPublisherRestClient apiPublisherClientUser1;
    private APIStoreRestClient apiStoreClientUser1;
    private APIStoreRestClient apiStoreClientUser2;

    @BeforeClass(alwaysRun = true)
    public void initialize() throws APIManagerIntegrationTestException, XPathExpressionException, MalformedURLException {
        super.init();
        apiEndPointUrl = getGatewayURLHttp() + API_END_POINT_POSTFIX_URL;
        providerName = user.getUserName();
        apiCreationRequestBean =
                new APICreationRequestBean(API_NAME, API_CONTEXT, API_VERSION_1_0_0,
                        providerName, new URL(apiEndPointUrl));
        apiCreationRequestBean.setTags(API_TAGS);
        apiCreationRequestBean.setDescription(API_DESCRIPTION);
        apiIdentifierAPI1Version1 = new APIIdentifier(providerName, API_NAME, API_VERSION_1_0_0);
        apiIdentifierAPI1Version2 = new APIIdentifier(providerName, API_NAME, API_VERSION_2_0_0);
        String publisherURLHttp = getPublisherURLHttp();
        String storeURLHttp = getStoreURLHttp();
        apiPublisherClientUser1 = new APIPublisherRestClient(publisherURLHttp);
        apiStoreClientUser1 = new APIStoreRestClient(storeURLHttp);
        //Login to API Publisher with  admin
        apiPublisherClientUser1.login(user.getUserName(), user.getPassword());
        //Login to API Store with  admin
        apiStoreClientUser1.login(user.getUserName(), user.getPassword());

        apiStoreClientUser2 = new APIStoreRestClient(storeURLHttp);
        //Login to API Store with  User2
        apiStoreClientUser2.login(
                storeContext.getContextTenant().getTenantUserList().get(0).getUserName(),
                storeContext.getContextTenant().getTenantUserList().get(0).getPassword());
        apiStoreClientUser1.addApplication(APPLICATION_NAME, TIER_UNLIMITED, "", "");
    }


    @Test(groups = {"wso2.am"}, description = "Test subscribe of old API version before deprecate the old version")
    public void testSubscribeOldVersionBeforeDeprecate() throws APIManagerIntegrationTestException {
        //Create and publish API version 1.0.0
        createAndPublishAPI(apiIdentifierAPI1Version1, apiCreationRequestBean, apiPublisherClientUser1, false);
        // Copy to version 2.0.0 and Publish Copied API
        copyAndPublishCopiedAPI(apiIdentifierAPI1Version1, API_VERSION_2_0_0, apiPublisherClientUser1, false);
        HttpResponse oldVersionSubscribeResponse =
                subscribeToAPI(apiIdentifierAPI1Version1, APPLICATION_NAME, apiStoreClientUser1);
        assertEquals(oldVersionSubscribeResponse.getResponseCode(), HTTP_RESPONSE_CODE_OK,
                "Subscribe of old API version request not successful " +
                        getAPIIdentifierString(apiIdentifierAPI1Version1));
        assertEquals(getValueFromJSON(oldVersionSubscribeResponse, "error"), "false",
                "Error in subscribe of old API version" + getAPIIdentifierString(apiIdentifierAPI1Version1) +
                        "Response Data:" + oldVersionSubscribeResponse.getData());
    }


    @Test(groups = {"wso2.am"}, description = "Test subscribe of new API version before deprecate the old version",
            dependsOnMethods = "testSubscribeOldVersionBeforeDeprecate")
    public void testSubscribeNewVersion() throws APIManagerIntegrationTestException {
        HttpResponse newVersionSubscribeResponse =
                subscribeToAPI(apiIdentifierAPI1Version2, APPLICATION_NAME, apiStoreClientUser1);
        assertEquals(newVersionSubscribeResponse.getResponseCode(), HTTP_RESPONSE_CODE_OK,
                "Subscribe of old API version request not successful " +
                        getAPIIdentifierString(apiIdentifierAPI1Version2));
        assertEquals(getValueFromJSON(newVersionSubscribeResponse, "error"), "false",
                "Error in subscribe of old API version" + getAPIIdentifierString(apiIdentifierAPI1Version2) +
                        "Response Data:" + newVersionSubscribeResponse.getData());
    }


    @Test(groups = {"wso2.am"}, description = "Test deprecate old api version",
            dependsOnMethods = "testSubscribeNewVersion")
    public void testDeprecateOldVersion() throws APIManagerIntegrationTestException {

        APILifeCycleStateRequest deprecatedUpdateRequest =
                new APILifeCycleStateRequest(API_NAME, providerName, APILifeCycleState.DEPRECATED);
        deprecatedUpdateRequest.setVersion(API_VERSION_1_0_0);
        HttpResponse deprecateAPIResponse =
                apiPublisherClientUser1.changeAPILifeCycleStatus(deprecatedUpdateRequest);
        assertEquals(deprecateAPIResponse.getResponseCode(), HTTP_RESPONSE_CODE_OK,
                "API deprecate Response code is invalid " + getAPIIdentifierString(apiIdentifierAPI1Version1));
        assertTrue(verifyAPIStatusChange(deprecateAPIResponse,
                        APILifeCycleState.PUBLISHED, APILifeCycleState.DEPRECATED),
                "API deprecate status Change is invalid in" + getAPIIdentifierString(apiIdentifierAPI1Version1) +
                        "Response Data:" + deprecateAPIResponse.getData());

    }

    @AfterClass(alwaysRun = true)
    public void cleanUpArtifacts() throws APIManagerIntegrationTestException {
        apiStoreClientUser1.removeApplication(APPLICATION_NAME);
        deleteAPI(apiIdentifierAPI1Version1, apiPublisherClientUser1);
        deleteAPI(apiIdentifierAPI1Version2, apiPublisherClientUser1);
    }


}
