package com.cx.automation.test.ui.dataanalysis;

import com.cx.automation.adk.database.MsSqlClient;
import com.cx.automation.adk.idgenerator.IdGenerator;
import com.cx.automation.adk.io.FileUtil;
import com.cx.automation.adk.systemtest.testbase.utill.ScreenShotRule;
import com.cx.automation.application.projectscan.project.TeamPath;
import com.cx.automation.application.user.User;
import com.cx.automation.application.user.role.impl.ReviewerRole;
import com.cx.automation.test.dto.ResultLabelType;
import com.cx.automation.test.dto.ResultSeverity;
import com.cx.automation.test.dto.State;
import com.cx.automation.test.ui.datagen.UserDataGen;
import com.cx.automation.test.ws.WSService;
import com.cx.automation.test.ws.datagen.ProjectsScansDataGen;
import com.cx.automation.test.ws.datagen.ViewerDataGen;
import com.cx.automation.test.ws.datagen.WSUserTeamDataGen;
import com.cx.automation.testbase.CXSystemTestBase;
import com.cx.automation.topology.component.ComponentsType;
import com.cx.automation.topology.connectionpoint.ConnectionPointType;
import com.cx.automation.topology.utils.TopologyUtil;
import com.cx.automation.ws.soap.portal.*;
import com.cx.automation.ws.soap.sdk.CurrentStatusEnum;
import org.apache.commons.lang3.StringUtils;
import org.junit.*;
import org.openqa.selenium.By;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.IfProfileValue;
import org.springframework.test.context.ContextConfiguration;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.cx.automation.topology.EnvironmentConstants.*;

/**
 * Created by Dorg on 10/02/2016.
 */

@ContextConfiguration(locations = {"classpath:com/cx/automation/test/ws/web-service-context.xml"})
public class DataAnalysisProjectResultTest extends CXSystemTestBase {

    @Rule
    public ScreenShotRule screenShotRule = new ScreenShotRule();

    public static WSService wsService;

    @Autowired
    public void setWsService(WSService wsService) {
        DataAnalysisProjectResultTest.wsService = wsService;
    }

    private static String sessionId;
    private static List<Long> projectIds = new ArrayList<Long>();
    private static List<String> projectNames = new ArrayList<String>();
    private static List<Long> scanIds = new ArrayList<Long>();
    private static List<CxWSResponseRunID> projectResponses = new ArrayList<CxWSResponseRunID>();
    private static String spName;
    private static String companyName;
    private static String teamName;
    private static User user;

    private static final String SERVER_NAME = "CxServer";
    final static String LOCAL_PROJECT_PATH = "com/cx/automation/test/cpp_22_LOC.zip";
    final static String GET_RESULT_ATTRIBUTES_PER_SIMILARITY = "select [value] from CxDB.dbo.CxComponentConfiguration where [key]='RESULT_ATTRIBUTES_PER_SIMILARITY'";
    private static final String DB_NAME = "CxDB";

    private static MsSqlClient msSqlClient;

    @Before
    public void initTest() throws Exception {

        if (beforeClass) {
            sessionId = portalClient.login(WSUserTeamDataGen.genLogin(envProps.getAdminUserName(), envProps.getAdminUserPassword())).getLoginResult().getSessionId();
            createGroupsAndUser();
            loginAs(envProps.getAdminUserName(), envProps.getAdminUserPassword());
            createProjects();
            verifyValueInDB();
            beforeClass = false;
        }
        updateProjectToAllHighAndConfirmed();
    }

    private void createProjects() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String projectName = "PR" + i + "_" + IdGenerator.generateNumID(4);
            projectNames.add(projectName);
            CxWSResponseRunID pr = wsService.createLocalProject(ProjectsScansDataGen.genProject(
                    projectName,
                    FileUtil.getAbsolutePath(LOCAL_PROJECT_PATH),
                    new TeamPath(SERVER_NAME, spName, companyName, teamName)));

            projectResponses.add(pr);
            projectIds.add(pr.getProjectID());
            Assert.assertTrue("fail in preliminary data creation", wsService.isExpectedStatus(pr.getRunId(), CurrentStatusEnum.FINISHED, 300));
            long scanId = portalClient.getScansDisplayData(ProjectsScansDataGen.genGetScansDisplayData(sessionId, pr.getProjectID())).getGetScansDisplayDataResult().getScanList().getScanDisplayData().get(0).getScanID();
            scanIds.add(scanId);
        }
    }

    private void createGroupsAndUser() {
        spName = "SP" + IdGenerator.generateNumID(4);
        companyName = "Company_" + IdGenerator.generateNumID(4);
        teamName = "A-Team_" + IdGenerator.generateNumID(4);
        user = UserDataGen.createAppUser(teamName, "user_" + IdGenerator.generateNumID(5) + "@cxMail.com", new ReviewerRole());

        Assert.assertTrue("Fail in preliminary data creation {using wm}. Failed to create SP: " + spName, StringUtils.isNotEmpty(wsService.createSp(spName)));
        Assert.assertTrue("Fail in preliminary data creation {using wm}. Failed to create Company: " + companyName, StringUtils.isNotEmpty(wsService.createCompany(companyName, spName)));
        Assert.assertTrue("Fail in preliminary data creation {using wm}. Failed to create Team: " + teamName, StringUtils.isNotEmpty(wsService.createTeam(teamName, companyName, spName)));
        Assert.assertTrue("Fail in preliminary data creation {using wm}. Failed to create User: " + user.getUserName(), wsService.createNewApplicationUser(user));
    }

    private void verifyValueInDB() throws Exception {
        String dbIP = TopologyUtil.getFirstConnectionPointIP(topology.getComponents().getComponentByName(ComponentsType.DATABASE), ConnectionPointType.DATABASE);
        msSqlClient = new MsSqlClient(dbIP, null, envProps.getDataBaseUserName(), envProps.getDataBaseUserPassword(), DB_NAME, true);
        ResultSet queryResult = msSqlClient.executeQuery(GET_RESULT_ATTRIBUTES_PER_SIMILARITY);
        Assert.assertTrue("query result from DB is empty", queryResult.next());
        Assert.assertTrue("value of RESULT_ATTRIBUTES_PER_SIMILARITY in CxDB.dbo.CxComponentConfiguration is not true", "true".equals(queryResult.getString("value")));
    }

    private void loginAs(String userName, String password) throws Exception {
        boolean isLoginSuc = loginService.loginToWebClient(userName, password);
        Assert.assertTrue("Failed to login to Checkmarx web-client.", isLoginSuc);
    }

    //TC 20717
    @Test
    @IfProfileValue(name = ENVIRONMENT, values = {EXTERNAL_ENV, CI_ENV, LUX_ENV1})
    public void changeResultsPerTeam() throws Exception {

        //change one result severity to low
        updateResultState(scanIds.get(0), String.valueOf(ResultSeverity.Low.getValue()), ResultLabelType.SEVERITY, projectIds.get(0), 1);
        //change one result state to not exploitable to low
        updateResultState(scanIds.get(0), String.valueOf(State.Not_Exploitable.getValue()), ResultLabelType.STATE, projectIds.get(0), 2);

        managementService.gotoDataAnalysis();
        dataAnalysisService.filterBy("Project Name", Arrays.asList(projectNames.get(1)));
        Thread.sleep(999);
        String strNumOfLow = testDriver.findElement(By.xpath("//table[contains(@id, 'pivotGrid_HZST')]/tbody/tr[2]/td[2]")).getText();
        String strNumOfHigh = testDriver.findElement(By.xpath("//table[contains(@id, 'pivotGrid_HZST')]/tbody/tr[2]/td[1]")).getText();
        testDriver.findElement(By.xpath("//span[contains(@id, 'chkIncludeNotExploitable')]")).click();

        Thread.sleep(3000);

        String strNumOfHighAfterUnIncludeNotExploitable = testDriver.findElement(By.xpath("//table[contains(@id, 'pivotGrid_HZST')]/tbody/tr[2]/td[1]")).getText();


        int numOfHigh = Integer.valueOf(strNumOfHigh);
        int numOfHighAfterUnIncludeNotExploitable = Integer.valueOf(strNumOfHighAfterUnIncludeNotExploitable);


        Assert.assertEquals("values should match", "1", strNumOfLow);
        Assert.assertEquals("values should match", numOfHighAfterUnIncludeNotExploitable, numOfHigh - 1);

    }

    private void updateResultState(long scanId, String value, ResultLabelType fieldType, long projectId, int index) {

        UpdateResultState updateResultState = ViewerDataGen.genUpdateResultState(sessionId,
                value,
                fieldType.getValue(),
                scanId,
                sessionId,
                "",
                index,
                projectId);

        UpdateResultStateResponse updateResultStateResponse = portalClient.updateResultState(updateResultState);
        Assert.assertTrue(updateResultStateResponse.getUpdateResultStateResult().getErrorMessage(), updateResultStateResponse.getUpdateResultStateResult().isIsSuccesfull());
    }

    private void updateProjectToAllHighAndConfirmed() {

        GetResultsForScan getResultsForScan = ProjectsScansDataGen.genGetResultsForScan(sessionId, scanIds.get(0));
        GetResultsForScanResponse resultsForScan = portalClient.getResultsForScan(getResultsForScan);
        Assert.assertTrue(resultsForScan.getGetResultsForScanResult().getErrorMessage(), resultsForScan.getGetResultsForScanResult().isIsSuccesfull());

        int numResults = resultsForScan.getGetResultsForScanResult().getResults().getCxWSSingleResultData().size();

        for (int k = 0; k < 2; k++) {

            for (int i = 1; i <= numResults; i++) {
                updateResultState(scanIds.get(k), String.valueOf(ResultSeverity.High.getValue()), ResultLabelType.SEVERITY, projectIds.get(k), i);
                updateResultState(scanIds.get(k), String.valueOf(State.Confirmed.getValue()), ResultLabelType.STATE, projectIds.get(k), i);
            }
        }

    }

    @AfterClass
    public static void cleanupTestClass() {

        if (wsService != null) {
            for (long id : projectIds) {
                wsService.deleteProject(id);
            }

            wsService.deleteSP(spName);
        }
    }

}
