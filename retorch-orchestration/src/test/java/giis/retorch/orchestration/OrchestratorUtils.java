package giis.retorch.orchestration;

import giis.retorch.orchestration.model.Activity;
import giis.retorch.orchestration.model.TJob;
import giis.retorch.orchestration.model.TestCase;
import giis.retorch.orchestration.testdata.AggregatorClassTests;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * {@code OrchestratorUtils} class contains several support methods that create different lists of ordered {@code Activity} in
 * order to use them during the {@code Orchestrator} unitary test cases.
 */
public class OrchestratorUtils extends GenericUtils {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorUtils.class);

    public List<Activity> getListComplexActivities() {
        Activity actZero = this.getFullFillActivity("ActZero");
        Activity actOne = this.getFullFillActivity("ActOne");
        actOne.addPredecessor(actZero);
        Activity actTwo = this.getFullFillActivity("ActTwo");
        actTwo.addPredecessor(actZero);
        Activity actThree = this.getFullFillActivity("ActThree");
        actThree.addPredecessor(actZero);
        Activity actFour = this.getEmptyActivity();
        actFour.addPredecessor(actOne);
        actFour.addPredecessor(actTwo);
        actFour.addPredecessor(actThree);
        Activity actFive = this.getFullFillActivity("ActFive");
        actFive.addPredecessor(actFour);
        Activity actSix = this.getFullFillActivity("ActSix");
        actSix.addPredecessor(actFour);
        Activity actSeven = this.getFullFillActivity("ActSeven");
        actSeven.addPredecessor(actFive);
        actSeven.addPredecessor(actSix);
        Activity actEight = this.getFullFillActivity("ActEight");
        actEight.addPredecessor(actSeven);
        Activity actNine = this.getFullFillActivity("ActNine");
        actNine.addPredecessor(actEight);
        Activity actTen = this.getFullFillActivity("ActTen");
        actTen.addPredecessor(actEight);

        return new LinkedList<>(Arrays.asList(actZero, actOne, actTwo, actThree, actFour, actFive, actSix, actSeven, actEight, actNine, actTen));
    }

    private Activity getFullFillActivity(String initialParticleTestCases) {
        TJob tJobTwo = new TJob();
        tJobTwo.addTestCase(new TestCase("t" + initialParticleTestCases + "FiveSch", AggregatorClassTests.class, Arrays.asList(
                this.getAccessModeLightElasticResource(), this.getMediumInelasticAccessMode())));
        tJobTwo.addTestCase(new TestCase("t" + initialParticleTestCases + "tFourSch", AggregatorClassTests.class, Arrays.asList(
                this.getMediumElasticAccessMode(), this.getMediumInelasticAccessMode())));
        tJobTwo.addTestCase(new TestCase("t" + initialParticleTestCases + "tThreeSch", AggregatorClassTests.class, Arrays.asList(
                this.getMediumElasticAccessMode(), this.getMediumInelasticAccessMode())));
        tJobTwo.addResource(this.getMediumInelasticResource());
        tJobTwo.addResource(this.getMediumElasticResource());

        return new Activity(tJobTwo);
    }

    private Activity getEmptyActivity() {

        return new Activity(new TJob());
    }

    public List<Activity> getListSimpleActivities() {
        List<Activity> listOutputActivities = new LinkedList<>();
        listOutputActivities.add(this.getFullFillActivity("ActSimpleOne"));
        listOutputActivities.add(this.getFullFillActivity("ActSimpleTwo"));
        listOutputActivities.add(this.getFullFillActivity("ActSimpleThree"));
        listOutputActivities.add(this.getFullFillActivity("ActSimpleFour"));
        listOutputActivities.add(this.getFullFillActivity("ActSimpleFive"));

        return listOutputActivities;
    }

    public List<Activity> getNotValidActivities() {
        List<Activity> listOutputActivities = new LinkedList<>();
        Activity actOne = this.getFullFillActivity("ActNotValidOne");
        Activity actTwo = this.getFullFillActivity("ActNotValidOne");
        actOne.addPredecessor(actTwo);
        actTwo.addPredecessor(actOne);
        listOutputActivities.add(actOne);
        listOutputActivities.add(actTwo);

        return listOutputActivities;
    }

    public static void putOutputToFile(String filePath, String output) throws IOException {
        log.debug("Put output of test case to {}", filePath);
        try (FileOutputStream outputStream = new FileOutputStream(filePath)) {
            byte[] strToBytes = output.getBytes();
            outputStream.write(strToBytes);
        }
    }

    public static void testFilesForPath(String expectedOutputPath, String outputPath) throws IOException {
        String expectedOutput = FileUtils.readFileToString(new File(expectedOutputPath), ENCODING).replace("\r\n", "\n");
        String actualOutput = FileUtils.readFileToString(new File(outputPath), ENCODING).replace("\r\n", "\n");
        assertEquals("The avg file generated differs from expected", expectedOutput, actualOutput);

    }
}