/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.pricing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import giis.retorch.profiling.usageprofile.dto.ReservationOptionDTO;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Normalized prices (USD) of the cloud objects that can host the execution of a plan, regardless of the provider
 * they were obtained from. It is serialized as JSON, so it is also the local cache of the dynamically fetched prices.
 * Immutable: it is built with {@link #builder()} and combined with {@link #merge(PriceCatalog)}, which returns a new catalog.
 */
public final class PriceCatalog {

    /** A virtual machine that is billed per hour while it is up. Immutable. */
    public static final class VmOffer {
        public final String provider;
        public final String region;
        public final String name;
        public final double vcpu;
        public final double memGb;
        public final double hourly;
        /** Reservations (1 and 3 years) with their effective hourly price; empty if there is no data. */
        public final List<ReservationOptionDTO> reservations;

        @JsonCreator
        public VmOffer(@JsonProperty("provider") String provider, @JsonProperty("region") String region,
                       @JsonProperty("name") String name, @JsonProperty("vcpu") double vcpu,
                       @JsonProperty("memGb") double memGb, @JsonProperty("hourly") double hourly,
                       @JsonProperty("reservations") List<ReservationOptionDTO> reservations) {
            this.provider = provider;
            this.region = region;
            this.name = name;
            this.vcpu = vcpu;
            this.memGb = memGb;
            this.hourly = hourly;
            this.reservations = immutable(reservations);
        }

        /** A VM without reservations. */
        public VmOffer(String provider, String region, String name, double vcpu, double memGb, double hourly) {
            this(provider, region, name, vcpu, memGb, hourly, null);
        }

        /** A copy with these reservations. */
        public VmOffer withReservations(List<ReservationOptionDTO> newReservations) {
            return new VmOffer(provider, region, name, vcpu, memGb, hourly, newReservations);
        }
    }

    /** A managed container service, billed per vCPU-hour and GB-hour actually requested. Immutable. */
    public static final class ContainerRate {
        public final String provider;
        public final String region;
        public final String service;
        public final double vcpuHour;
        public final double gbHour;

        @JsonCreator
        public ContainerRate(@JsonProperty("provider") String provider, @JsonProperty("region") String region,
                             @JsonProperty("service") String service, @JsonProperty("vcpuHour") double vcpuHour,
                             @JsonProperty("gbHour") double gbHour) {
            this.provider = provider;
            this.region = region;
            this.service = service;
            this.vcpuHour = vcpuHour;
            this.gbHour = gbHour;
        }
    }

    /** A browser (or device) as a service, billed per minute of use, e.g. AWS Device Farm. Immutable. */
    public static final class BrowserRate {
        public final String provider;
        public final String region;
        public final String service;
        /** What is rented: "Chrome", "Firefox", "Edge", "Android devices", "iOS devices". */
        public final String name;
        public final double minuteUsd;

        @JsonCreator
        public BrowserRate(@JsonProperty("provider") String provider, @JsonProperty("region") String region,
                           @JsonProperty("service") String service, @JsonProperty("name") String name,
                           @JsonProperty("minuteUsd") double minuteUsd) {
            this.provider = provider;
            this.region = region;
            this.service = service;
            this.name = name;
            this.minuteUsd = minuteUsd;
        }
    }

    /** A block storage volume, billed per provisioned GB-month. Immutable. */
    public static final class StorageRate {
        public final String provider;
        public final String region;
        public final String name;
        public final double gbMonth;

        @JsonCreator
        public StorageRate(@JsonProperty("provider") String provider, @JsonProperty("region") String region,
                           @JsonProperty("name") String name, @JsonProperty("gbMonth") double gbMonth) {
            this.provider = provider;
            this.region = region;
            this.name = name;
            this.gbMonth = gbMonth;
        }
    }

    /** Version of the layout of the catalog (and of what it contains); a cache with another version is refetched. */
    public static final int SCHEMA_VERSION = 3;

    /** A catalog read from a file that has no version (written before versions existed) gets 0. */
    public final int schemaVersion;
    public final long fetchedAtEpochMs;
    /** Regions the prices were fetched for; a cache built for other regions is not reused. */
    public final String regionsKey;
    public final List<VmOffer> vms;
    public final List<ContainerRate> containers;
    public final List<StorageRate> storage;
    public final List<BrowserRate> browsers;
    /** Non-fatal problems found while building the catalog (e.g. a provider that could not be reached). */
    public final List<String> warnings;

    @JsonCreator
    PriceCatalog(@JsonProperty("schemaVersion") Integer schemaVersion,
                 @JsonProperty("fetchedAtEpochMs") long fetchedAtEpochMs, @JsonProperty("regionsKey") String regionsKey,
                 @JsonProperty("vms") List<VmOffer> vms, @JsonProperty("containers") List<ContainerRate> containers,
                 @JsonProperty("storage") List<StorageRate> storage, @JsonProperty("browsers") List<BrowserRate> browsers,
                 @JsonProperty("warnings") List<String> warnings) {
        this.schemaVersion = schemaVersion == null ? 0 : schemaVersion;
        this.fetchedAtEpochMs = fetchedAtEpochMs;
        this.regionsKey = regionsKey;
        this.vms = immutable(vms);
        this.containers = immutable(containers);
        this.storage = immutable(storage);
        this.browsers = immutable(browsers);
        this.warnings = immutable(warnings);
    }

    private static <T> List<T> immutable(List<T> list) {
        return list == null ? Collections.<T>emptyList() : Collections.unmodifiableList(new ArrayList<>(list));
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A new catalog with the offers and warnings of both; date and regions are those of this catalog. */
    public PriceCatalog merge(PriceCatalog other) {
        return toBuilder().merge(other).build();
    }

    /** A new catalog like this one with more warnings at the end. */
    public PriceCatalog withWarnings(List<String> more) {
        Builder b = toBuilder();
        for (String w : more) {
            b.addWarning(w);
        }
        return b.build();
    }

    private Builder toBuilder() {
        Builder b = new Builder().fetchedAt(fetchedAtEpochMs).regionsKey(regionsKey);
        b.schemaVersion = schemaVersion;
        return b.merge(this);
    }

    /** Collects the offers of a catalog; {@link #build()} makes it immutable. */
    public static final class Builder {
        private int schemaVersion = SCHEMA_VERSION;
        private long fetchedAtEpochMs;
        private String regionsKey;
        private final List<VmOffer> vms = new ArrayList<>();
        private final List<ContainerRate> containers = new ArrayList<>();
        private final List<StorageRate> storage = new ArrayList<>();
        private final List<BrowserRate> browsers = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        private Builder() {
        }

        public Builder fetchedAt(long epochMs) {
            this.fetchedAtEpochMs = epochMs;
            return this;
        }

        public Builder regionsKey(String key) {
            this.regionsKey = key;
            return this;
        }

        public Builder addVm(VmOffer vm) {
            vms.add(vm);
            return this;
        }

        public Builder addContainer(ContainerRate rate) {
            containers.add(rate);
            return this;
        }

        public Builder addStorage(StorageRate rate) {
            storage.add(rate);
            return this;
        }

        public Builder addBrowser(BrowserRate rate) {
            browsers.add(rate);
            return this;
        }

        public Builder addWarning(String warning) {
            warnings.add(warning);
            return this;
        }

        /** The VMs added so far (a copy). */
        public List<VmOffer> vms() {
            return new ArrayList<>(vms);
        }

        /** Replaces all the VMs (e.g. with the same VMs plus their reservations). */
        public Builder replaceVms(List<VmOffer> newVms) {
            List<VmOffer> copy = new ArrayList<>(newVms);
            vms.clear();
            vms.addAll(copy);
            return this;
        }

        /** Adds the offers and warnings of {@code other}. */
        public Builder merge(PriceCatalog other) {
            vms.addAll(other.vms);
            containers.addAll(other.containers);
            storage.addAll(other.storage);
            browsers.addAll(other.browsers);
            warnings.addAll(other.warnings);
            return this;
        }

        public PriceCatalog build() {
            return new PriceCatalog(schemaVersion, fetchedAtEpochMs, regionsKey, vms, containers, storage, browsers,
                    warnings);
        }
    }

    public static PriceCatalog load(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(file);
        return mapper.treeToValue(tree, PriceCatalog.class);
    }

    public void save(File file) throws IOException {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(file, this);
    }
}
