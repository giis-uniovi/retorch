/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import giis.retorch.profiling.usageprofile.dto.ReservationOption;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Normalized prices (USD) of the cloud objects that can host the execution of a plan, regardless of the provider
 * they were obtained from. It is serialized as JSON, so it is also the local cache of the dynamically fetched prices.
 */
public class PriceCatalog {

    /** A virtual machine that is billed per hour while it is up. */
    public static class VmOffer {
        public String provider;
        public String region;
        public String name;
        public double vcpu;
        public double memGb;
        public double hourly;
        /** Reservations (1 and 3 years) with their effective hourly price; empty if there is no data. */
        public List<ReservationOption> reservations = new ArrayList<>();
    }

    /** A managed container service, billed per vCPU-hour and GB-hour actually requested. */
    public static class ContainerRate {
        public String provider;
        public String region;
        public String service;
        public double vcpuHour;
        public double gbHour;
    }

    /** A block storage volume, billed per provisioned GB-month. */
    public static class StorageRate {
        public String provider;
        public String region;
        public String name;
        public double gbMonth;
    }

    /** Version of the layout of the catalog (and of what it contains); a cache with another version is refetched. */
    public static final int SCHEMA_VERSION = 2;

    /** A catalog read from a file that has no version (written before versions existed) gets 0. */
    public int schemaVersion = SCHEMA_VERSION;
    public long fetchedAtEpochMs;
    /** Regions the prices were fetched for; a cache built for other regions is not reused. */
    public String regionsKey;
    public List<VmOffer> vms = new ArrayList<>();
    public List<ContainerRate> containers = new ArrayList<>();
    public List<StorageRate> storage = new ArrayList<>();
    /** Non-fatal problems found while building the catalog (e.g. a provider that could not be reached). */
    public List<String> warnings = new ArrayList<>();

    public void merge(PriceCatalog other) {
        vms.addAll(other.vms);
        containers.addAll(other.containers);
        storage.addAll(other.storage);
        warnings.addAll(other.warnings);
    }

    public static PriceCatalog load(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(file);
        PriceCatalog catalog = mapper.treeToValue(tree, PriceCatalog.class);
        if (!tree.has("schemaVersion")) {
            catalog.schemaVersion = 0;
        }
        return catalog;
    }

    public void save(File file) throws IOException {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(file, this);
    }
}
