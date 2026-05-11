package giis.retorch.profiling;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import giis.retorch.orchestration.classifier.EmptyInputException;
import giis.retorch.orchestration.model.ExecutionPlan;
import giis.retorch.orchestration.orchestrator.NoFinalActivitiesException;
import giis.retorch.profiling.model.CloudObjectInstance;
import giis.retorch.profiling.model.UsageProfile;
import giis.retorch.profiling.profilegeneration.ProfileGenerator;
import giis.retorch.profiling.profilegeneration.ProfilePlotter;
import giis.retorch.profiling.report.UsageProfileReportGenerator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Verifies that {@code ProfilePlotter} and {@code UsageProfileReportGenerator} produce the expected output
 * files on disk (PNG charts and the consolidated PDF report).
 */
public class UsageProfilerOutputFilesTests {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private static final String OUT_BASE    = "target/test-outputs/profiler-files";
    private static final String IN_BASE     = "src/test/java/giis/retorch/profiling/testdata/profilegen";
    private static final String CHARTS_DIR  = OUT_BASE + "/charts";

    private ProfileGenerator generator;
    private ProfilerDataGenerationUtils dataUtils;
    private ExecutionPlan plan;

    @Rule
    public TestName testName = new TestName();

    @Before
    public void setUp() throws NoFinalActivitiesException, EmptyInputException, IOException {
        log.info("****** Running test: {} ******", testName.getMethodName());
        ProfilerTestUtils.ensureDirectoryExists(OUT_BASE, log);
        ProfilerTestUtils.ensureDirectoryExists(CHARTS_DIR, log);
        generator  = new ProfileGenerator();
        dataUtils  = new ProfilerDataGenerationUtils();
        plan       = dataUtils.generateExecutionPlan();
        plan.setName("files" + plan.getName());
        generator.generateExecutionPlanCapacitiesUsage(plan,
                IN_BASE + "/imp_avg_dataset.csv",
                OUT_BASE + "/raw_profile.csv", 3600, 4);
    }

    @Test
    public void testVMPngFilesGenerated() throws IOException {
        CloudObjectInstance vm = dataUtils.generateVMCloudObjectInstances();
        generator.writeCOIContractedCapacitiesCSV(
                OUT_BASE + "/raw_profile.csv", OUT_BASE + "/profile_vm.csv", vm);
        new ProfilePlotter(OUT_BASE + "/profile_vm.csv")
                .generateTotalTJobUsageProfileCharts(CHARTS_DIR, CHARTS_DIR, plan.getName(), "vm");

        assertValidPng(CHARTS_DIR + "/" + plan.getName() + "-vm-memory.png");
        assertValidPng(CHARTS_DIR + "/" + plan.getName() + "-vm-processor.png");
        assertValidPng(CHARTS_DIR + "/" + plan.getName() + "-vm-storage.png");
        assertValidPng(CHARTS_DIR + "/" + plan.getName() + "-vm-slots.png");
    }

    @Test
    public void testContainerPngFilesGenerated() throws IOException {
        CloudObjectInstance containers = dataUtils.generateContainersCloudObjectInstances();
        generator.writeCOIContractedCapacitiesCSV(
                OUT_BASE + "/raw_profile.csv", OUT_BASE + "/profile_containers.csv", containers);
        new ProfilePlotter(OUT_BASE + "/profile_containers.csv")
                .generateTotalTJobUsageProfileCharts(CHARTS_DIR, CHARTS_DIR, plan.getName(), "containers");

        // Slots quantity=0 for containers — no chart expected
        assertValidPng(CHARTS_DIR + "/" + plan.getName() + "-containers-memory.png");
        assertValidPng(CHARTS_DIR + "/" + plan.getName() + "-containers-processor.png");
        assertValidPng(CHARTS_DIR + "/" + plan.getName() + "-containers-storage.png");
        assertFalse("Slots PNG should not exist for containers (quantity=0)",
                new File(CHARTS_DIR + "/" + plan.getName() + "-containers-slots.png").exists());
    }

    @Test
    public void testServicesPngFilesGenerated() throws IOException {
        CloudObjectInstance services = dataUtils.generateBrowserServiceCloudObjectInstances();
        generator.writeCOIContractedCapacitiesCSV(
                OUT_BASE + "/raw_profile.csv", OUT_BASE + "/profile_services.csv", services);
        new ProfilePlotter(OUT_BASE + "/profile_services.csv")
                .generateTotalTJobUsageProfileCharts(CHARTS_DIR, CHARTS_DIR, plan.getName(), "services");

        // Selenoid: only slots; memory/processor/storage quantity=0
        assertValidPng(CHARTS_DIR + "/" + plan.getName() + "-services-slots.png");
        assertFalse("Memory PNG should not exist for services (quantity=0)",
                new File(CHARTS_DIR + "/" + plan.getName() + "-services-memory.png").exists());
    }

    @Test
    public void testPdfReportGenerated() throws IOException {
        List<UsageProfile> profiles = new ArrayList<>();

        for (CloudObjectInstance coi : Arrays.asList(
                dataUtils.generateVMCloudObjectInstances(),
                dataUtils.generateContainersCloudObjectInstances(),
                dataUtils.generateBrowserServiceCloudObjectInstances())) {
            String coiName = coi.getName().replace(" ", "_");
            String coiCsv  = OUT_BASE + "/profile_pdf_" + coiName + ".csv";
            generator.writeCOIContractedCapacitiesCSV(OUT_BASE + "/raw_profile.csv", coiCsv, coi);
            ProfilePlotter plotter = new ProfilePlotter(coiCsv);
            plotter.generateTotalTJobUsageProfileCharts(CHARTS_DIR, CHARTS_DIR, plan.getName(), coiName);
            profiles.add(plotter.getUsageProfile());
        }

        new UsageProfileReportGenerator().generateReport(profiles, OUT_BASE + "/", plan.getName());

        String pdfPath = OUT_BASE + "/" + plan.getName() + "-UsageProfile.pdf";
        File pdfFile   = new File(pdfPath);
        assertTrue("PDF report must exist at: " + pdfPath, pdfFile.exists());
        assertTrue("PDF report must have non-zero size", pdfFile.length() > 0);

        try (PdfDocument pdf = new PdfDocument(new PdfReader(pdfPath))) {
            int pages = pdf.getNumberOfPages();
            assertTrue("PDF must have at least 4 pages (cover + 3 COI sections), got: " + pages, pages >= 4);
        }
    }

    private void assertValidPng(String path) throws IOException {
        File file = new File(path);
        assertTrue("PNG must exist at: " + path, file.exists());
        assertTrue("PNG must have non-zero size: " + path, file.length() > 0);
        BufferedImage img = ImageIO.read(file);
        assertNotNull("PNG must be a readable image: " + path, img);
        assertEquals("PNG width must be 1200 px", 1200, img.getWidth());
        assertEquals("PNG height must be 400 px",  400, img.getHeight());
    }
}
