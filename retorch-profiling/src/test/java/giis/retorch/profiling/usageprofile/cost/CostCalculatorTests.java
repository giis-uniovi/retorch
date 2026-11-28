/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.cost;

import giis.retorch.profiling.usageprofile.measurement.Load;
import giis.retorch.profiling.usageprofile.measurement.Slice;
import giis.retorch.profiling.usageprofile.measurement.Timeline;
import giis.retorch.profiling.usageprofile.dto.AlternativeDTO;
import giis.retorch.profiling.usageprofile.dto.CostItemDTO;
import giis.retorch.profiling.usageprofile.dto.CostReportDTO;
import giis.retorch.profiling.usageprofile.pricing.PriceCatalog;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static giis.retorch.profiling.usageprofile.TestFixtures.*;

public class CostCalculatorTests {

    @Test
    public void testVmCostIsFullyDistributedAndShowsOversubscription() throws IOException {
        PriceCatalog catalog = PriceCatalog.builder()
                .addVm(vm("P", "big", 4, 8, 3.6))
                .addVm(vm("P", "small", 2, 2, 1.8))
                .addVm(vm("P", "tiny", 1, 1, 0.5)) // cannot hold the peak, must be discarded
                .addStorage(new PriceCatalog.StorageRate("P", "r1", "ssd", 0.1)).build();

        CostReportDTO report = new CostCalculator().calculate(timeline(), catalog);

        assertEquals(2, report.alternatives.size());
        AlternativeDTO small = report.alternatives.get(0);
        assertEquals("small (2 vCPU, 2 GB)", small.label);
        double expectedVm = 81 * 1.8 / 3600.0;
        double expectedDisk = 0.1 * 1 / (30.0 * 86400) * 81;
        assertEquals(expectedVm + expectedDisk, small.total, 1e-9);
        double coiCompute = 0;
        double idleCompute = 0;
        for (CostItemDTO i : small.items) {
            if (CostCalculator.COMPUTE.equals(i.object) && CostCalculator.COI.equals(i.tjob)) coiCompute += i.cost;
            if (CostCalculator.COMPUTE.equals(i.object) && CostCalculator.OVERSUBSCRIPTION.equals(i.phase)) idleCompute += i.cost;
        }
        assertEquals(21 * 1.8 / 3600.0, coiCompute, 1e-9); // 10 s of COI set-up and 11 s of COI tear-down
        assertTrue(idleCompute > 0);
        assertTrue(hasItem(small, "compute", "tjoba", "tjob-testexec"));
        assertTrue(hasItem(small, "storage", "tjoba", "tjob-testexec"));
    }

    @Test
    public void testContainerCostIsPayPerUse() throws IOException {
        // 1 USD per vCPU-second makes the expectation easy to compute
        PriceCatalog catalog = PriceCatalog.builder()
                .addContainer(new PriceCatalog.ContainerRate("P", "r1", "Serverless", 3600, 0)).build();

        AlternativeDTO alt = new CostCalculator().calculate(timeline(), catalog).alternatives.get(0);

        // vCPU-seconds: tjoba uses 1 vCPU for the 10+40+10... the samples hold between 12 s and 70 s
        double cpuSeconds = 0;
        Timeline t = timeline();
        for (Slice s : t.slices) {
            for (Load l : s.loads) cpuSeconds += l.cpu;
        }
        assertEquals(cpuSeconds, alt.total, 1e-6);
        for (CostItemDTO i : alt.items) {
            assertFalse(CostCalculator.OVERSUBSCRIPTION.equals(i.phase));
            assertFalse(CostCalculator.COI.equals(i.tjob));
        }
    }

    @Test
    public void testNoVmFitsProducesWarning() throws IOException {
        PriceCatalog catalog = PriceCatalog.builder().addVm(vm("P", "tiny", 1, 1, 0.5)).build();
        CostReportDTO report = new CostCalculator().calculate(timeline(), catalog);
        assertTrue(report.alternatives.isEmpty());
        assertEquals(1, report.warnings.size());
    }

    @Test
    public void testBrowserServiceAddsBrowserMinutesToTheCheapestAlternative() throws IOException {
        PriceCatalog catalog = smallCatalogBuilder()
                .addBrowser(new PriceCatalog.BrowserRate("AWS", "us-west-2", "AWS Device Farm", "Chrome", 0.6)).build();
        CostReportDTO plain = new CostCalculator().calculate(timeline(), catalog);
        AlternativeDTO cheapest = plain.alternatives.get(0);

        CostReportDTO report = new CostCalculator().calculate(timeline(), catalog, set("TJOBA"));
        AlternativeDTO service = null;
        for (AlternativeDTO a : report.alternatives) {
            if (AlternativeDTO.KIND_BROWSER_SERVICE.equals(a.kind)) {
                service = a;
            }
        }
        assertNotNull(service);
        assertEquals("AWS Device Farm", service.provider);
        assertTrue(service.label.startsWith("Chrome + "));
        double browser = 0;
        for (CostItemDTO item : service.items) {
            if ("browser".equals(item.object)) {
                assertEquals("tjoba", item.tjob);
                assertEquals("tjob-testexec", item.phase);
                browser += item.cost;
            }
        }
        // tjoba runs its tests during 40 s, at 0.6 USD per minute
        assertEquals(0.4, browser, 0.03);
        assertEquals(cheapest.total + browser, service.total, 1e-9);
        assertEquals(plain.alternatives.size() + 1, report.alternatives.size());

        CostReportDTO noBrowsers = new CostCalculator().calculate(timeline(), catalog, new HashSet<String>());
        assertEquals(plain.alternatives.size(), noBrowsers.alternatives.size());
        assertTrue(noBrowsers.warnings.toString().contains("Browser services not calculated"));
    }
}
