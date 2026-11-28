# Spec: `giis.retorch.profiling.usageprofile.pricing`

## Purpose
Gets the prices (USD) of the cloud objects the cost calculation needs, from the public price APIs of Azure, AWS, Oracle Cloud and IBM Cloud or from a
JSON cache, and holds them in an immutable `PriceCatalog`. Network is never used in tests.

## Public API
`PriceCatalog` (and its nested `VmOffer`, `ContainerRate`, `StorageRate`, `BrowserRate`, `Builder`), `PriceSource`,
`PricingFetcher`, `PriceProvider`, `AbstractPriceProvider`, `PriceProviderRegistry`, `ProviderRegions`, `AzurePrices`,
`AwsPrices`, `DeviceFarmPrices`, `OracleCloudPrices`, `IbmCloudPrices` and `HttpGet`. `PriceCache` and
`AwsReservations` are package-private. Other packages only need `PriceCatalog` (cost, report, plan tests and
`main.UsageProfileRequest`) and `PriceSource` (`main`).

## Price catalog
- `PriceCatalog`: immutable (public final fields, unmodifiable lists, nested `VmOffer`, `ContainerRate`, `StorageRate`,
  `BrowserRate` also immutable); built with `PriceCatalog.builder()` (`fetchedAt`, `regionsKey`,
  `addVm/addContainer/addStorage/addBrowser/addWarning`, `merge`, `replaceVms`, `build`); `merge(other)` and
  `withWarnings(list)` return a new catalog. Fields: `schemaVersion` (constant `SCHEMA_VERSION`, now 3, unchanged: the JSON
  layout did not change), `vms`, `containers`, `storage`, `browsers` (`BrowserRate(provider, region, service, name,
  minuteUsd)`: a browser or device rented by the minute), `warnings`, `fetchedAtEpochMs`, `regionsKey`; JSON load/save
  (Jackson through `@JsonCreator`; missing lists are empty). `VmOffer.reservations` is a list of `dto.cost.ReservationOptionDTO`
  (empty if there is no data). A file without `schemaVersion` loads as 0.

## Design: providers are pluggable
Patterns: **Strategy** (`PriceProvider`), **Template Method** (`AbstractPriceProvider`) and **Registry/Factory**
(`PriceProviderRegistry`). `PricingFetcher`, `PriceSource`, `PriceCache`, the CLI argument handling and `PriceCatalog` know
no concrete provider; adding one never edits them.
- `PriceProvider` (Strategy): `id()` (unique, e.g. `azure`, `aws`, `aws-devicefarm`; the key of its regions), `name()` (used in
  warnings), `configId()` (the region setting it reads; default `id()`; Device Farm returns `aws`), `regionsFor(ProviderRegions)`
  (default: the regions of `configId()`; empty = disabled; Device Farm overrides it: `[global]` only if AWS has regions),
  `fetch(region)` and `describe(region)` (warning text).
- `ProviderRegions`: immutable map provider id -> regions (`none()`, `of(azure, aws)`, `with(id, "r1,r2")`, `regions(id)`);
  comma separated lists, blanks ignored, blank = disabled.
- `AbstractPriceProvider` (Template Method): `fetch(region)` is final (builds the catalog around the abstract
  `collect(region, builder)`); shared plumbing: `open`, `readJson`, `pages(url, itemsField, nextLinkField)` (paged APIs),
  `readOffer` / `attr` / `firstUsdPrice` (streaming of the AWS-style offer files with sections `products`, `terms.OnDemand`,
  `terms.Reserved`), `number(text, fallback)` (safe parsing of "8 GiB", "1,024", else the fallback: an EC2 type with
  non-numeric vCPU/memory is skipped instead of failing the region), reservation helpers (`termYears` for "1 Year"/"3yr" ->
  1 or 3 else 0, `addCheapest` per (term, payment), `sortByTerm`) and the factories `vm`, `container`, `storage`, `browser`
  that label the entries with the provider label given to the constructor (`id`, `name`, `catalogProvider`; Device Farm's
  entries are labelled `AWS`). A concrete provider contains only its own filters and mapping.
- `PriceProviderRegistry`: ordered list of providers (`register(provider)`, unique `id`, `providers()`) and
  `standard(HttpGet)` = Azure, AWS, Device Farm, Oracle Cloud, IBM Cloud (the only place that lists them; explicit list, no `ServiceLoader`, which is
  fragile in the shaded CLI jar). `regionsKey(ProviderRegions)` builds the cache key generically: one `id=r1,r2` entry per
  distinct `configId()` in registration order, joined with `;`: `azure=a,b;aws=c` (same as before, so caches keep working
  and `SCHEMA_VERSION` stays 3); a registered extra provider appends `;id=regions` and so invalidates older caches. A provider that is `optional()` (default false;
  Oracle and IBM) is in the key only when it has regions, so with nothing enabled the key is unchanged (`azure=a;aws=` even
  though they are registered) and with them it is `azure=a;aws=b;oracle=global;ibm=us-south,eu-de`.
- Recipe to add a provider (e.g. GCP): 1. `final class GcpPrices extends AbstractPriceProvider` with a constructor calling
  `super(http, "gcp", "GCP", "GCP")` and `collect(region, out)` that reads with `pages`/`readJson`/`readOffer` and adds entries
  built with `vm/container/storage/browser`; 2. `.register(new GcpPrices(http))` in `PriceProviderRegistry.standard`;
  3. a test with canned HTTP (see `PriceProviderExtensibilityTests`). Regions are then given with
  `PriceSource.builder().regions("gcp", "europe-west1")` (the CLI keeps its two region arguments; an `optional()` provider is
  enabled from the CLI with the system property `usageprofile-regions-<id>`, see below).
- Invariants: no concrete provider outside the registry's `standard` factory; ids are unique; a provider with no regions is
  not called; a failing provider or region only adds a warning and never affects the others; the catalog JSON layout does not
  depend on the provider.

## Providers and fetching
- Providers `AzurePrices`, `AwsPrices` and `DeviceFarmPrices` (the last one global: its region argument is ignored) extend
  `AbstractPriceProvider`; `PriceCache` is the JSON file (returns the cache only if it has the current schema version and the
  same regions key) and `HttpGet` (`open(url)` -> `InputStream`, `HttpGet.standard()` = the real HTTP). `new
  PricingFetcher(HttpGet)` uses `PriceProviderRegistry.standard(http)`; `new PricingFetcher(PriceProviderRegistry)` takes any
  registry, so every flow is tested with canned responses or fakes.
- `PricingFetcher.fetchOrLoad(ProviderRegions, cacheFile, maxAgeMs)` (and the classic `fetchOrLoad(azureRegions, awsRegions,
  cacheFile, maxAgeMs)`, which delegates with `ProviderRegions.of`): regions are comma separated lists
  (`PricingFetcher.split` = `ProviderRegions.split`), blank disables a provider; each registered provider is asked for the
  regions it declares in `regionsFor`, in registration order. The cache is used only if younger than `maxAgeMs`, built for the same
  regions **and** with the current `schemaVersion` (an old cache is ignored and the prices are fetched again); if every
  provider fails and a cache exists, the cache is returned with a warning; if nothing is available it throws `IOException`
  ("No prices ..."). A failing provider/region only adds a warning.
- `PriceSource(azureRegions, awsRegions, cacheFile, maxAgeHours).load()` bundles those four settings (used by the toolbox and
  `UsageProfileMain`). `PriceSource.builder().regions(providerId, "r1,r2")...cacheFile(path).maxAgeHours(h)
  .providers(registry).build()` is the generic form (default registry = standard, default cache lifetime 24 h). `Builder.systemPropertyRegions()` also reads, for every `optional()`
  provider of the registry, the system property `usageprofile-regions-<id>` (`-Dusageprofile-regions-oracle=global`,
  `-Dusageprofile-regions-ibm=us-south,eu-de`); unset or blank leaves it disabled (`PriceSource.regionsFromSystemProperties`).
  `UsageProfileMain` uses it, so the opt-in providers are enabled by system properties of the java call.
- Azure: Retail Prices API (public). VM sizes come from a fixed list in the class (the API gives no vCPU/RAM); Spot, Low
  Priority and Windows prices are ignored. Container Instances vCPU/GB-hour; Standard SSD E10 disk -> GB-month.
  Reservations: a second query with `priceType eq 'Reservation'` for the same sizes; each item has `reservationTerm`
  (`1 Year` / `3 Years`) and `retailPrice` = the total price of the whole term (`unitOfMeasure` is still `1 Hour`), so it
  becomes an `ALL_UPFRONT` option with `effectiveHourly = retailPrice / (years * 8760)`; the cheapest per (VM, term); other
  terms and Windows products are ignored. Azure publishes no hourly-paid (no upfront) reservation in this API.
- AWS: Price List bulk files, parsed in streaming (EC2 is ~450 MB per region): current-generation Linux shared on-demand
  instances, EBS gp3, Fargate (x86 Linux). Reservations come from the same stream (`terms.Reserved`, which follows
  `OnDemand`; class `AwsReservations`): only `OfferingClass` = `standard`, `LeaseContractLength` `1yr`/`3yr`, and
  `PurchaseOption` `No Upfront` (dimension with unit `Hrs` -> `NO_UPFRONT`, `effectiveHourly` = that rate) or `All Upfront`
  (dimension with unit `Quantity` = upfront fee -> `ALL_UPFRONT`, `effectiveHourly` = fee / (years * 8760)); Partial Upfront
  and convertible terms are ignored. Only the skus of the accepted products are loaded (low memory).
- AWS Device Farm (only when AWS is enabled; one global offer `AWSDeviceFarm`, fetched once whatever the regions; a failure
  only adds a warning): `DeviceFarmPrices.parse` keeps the metered on-demand per-minute products with a price > 0: Windows
  desktop browsers Chrome, Firefox and Edge (`*-Windows-<browser>-automated-minute`, `BrowserRate.name` = the browser) and
  `Android` / `iOS` metered device minutes (`name` = `Android devices` / `iOS devices`); free, trial, unmetered,
  remote-access, Internet Explorer and monthly-slot products are ignored. Region = `regionCode` of the product.

## Opt-in providers (Oracle Cloud and IBM Cloud)
Both extend `AbstractPriceProvider`, are `optional()` (disabled unless their regions are given), need no credentials, and add
only `VmOffer`s and a `StorageRate` (no container service, no reservations, so `CostCalculator` works unchanged).
| Provider (id, label) | Fetches | Regions | Caveats |
|---|---|---|---|
| `OracleCloudPrices` (`oracle`, `Oracle Cloud`) | one GET of the Cost Estimator API `apexapps.oracle.com/pls/apex/cetools/api/v1/products/?currencyCode=USD` | prices are global: the pseudo region is any name, use `global` | flexible shapes: OCPUs 1,2,4,8,16,32 x 8 GB/OCPU and 16 GB/OCPU (memory optimised), priced `ocpu * OCPU-hour + gb * GB-hour` from the items `Compute - Standard - <E3,E4,E5,E6,X9> - OCPU` (metric `OCPU Per Hour`) and `... - Memory` (`Gigabyte(s) Per Hour`), category `Compute - Virtual Machine`; a family needs both items, the cheapest family wins per shape and names the VM (`VM.Flex.E3.4ocpu-32gb`); 1 OCPU = 2 vCPU (x86 hyper-threads); ARM (A1/A2/A4), `Ax`, Optimized, Cloud@Customer and free tier items are ignored; storage = `Storage - Block Volume - Storage` (`Gigabyte Storage Capacity Per Month`). Missing compute or block volume items fail the region with a message (warning) |
| `IbmCloudPrices` (`ibm`, `IBM Cloud`) | public Global Catalog (no auth, `Accept: application/json` only): plan list of `is.instance` (paged, `next`), per chosen profile the deployment list of the plan and the pricing of the deployment of the region; plan list of `is.volume` | IBM region names (`us-south`, `eu-de`, `eu-gb`...), one deployment per region | the price is on the deployment, not on the plan (`/<planId>/pricing` answers 404): metric `part-is.instance-hours-<profile>` with unit `Instance-Hour`, USD, first tier; profiles `bxf`/`cxf`/`mxf` (Flex, the generation present in all regions) with 2,4,8,16,32 vCPU, vCPU/GB parsed from `family-VCPUxGB`; the Gen4 `bx4/cx4/mx4` plans only exist in us-south and the old `bx2/cx2/mx2` are gone from the catalog; 2 HTTP calls per profile and region (about 30) plus the plan lists; a profile without deployment or price in the region is a warning, no priced profile fails the region; block storage = plan `gen2-volume-general-purpose`, metric `is.volume.generalpurpose.GB` per GB-hour x 730 (missing = warning); the catalog repeats plans across pages (first wins) |

## Tests (`src/test/java/.../usageprofile/pricing`)
- `PricingTests`: Azure parsers ignore Spot and Windows, AWS offer read in streaming and filtered, Azure reservations (all
  upfront per term), AWS `terms.Reserved` (standard only, No/All Upfront, 1/3 years), cache without or with another
  `schemaVersion` ignored, fresh cache avoids the network, `PriceSource` uses the cache for the same regions, comma separated
  regions and blank disables a provider, Device Farm keeps only metered browsers and devices (per-minute price).
- `PricingFetcherTests`: fresh cache without provider calls, expired cache refetched and saved, cache of other regions
  ignored, all providers failing falls back to the expired cache with warnings, nothing available throws, one failing region
  only warns, Device Farm only with AWS and once, Azure with canned HTTP responses, HTTP failure as warnings.
- `PriceProviderExtensibilityTests`: a fake provider (one class extending `AbstractPriceProvider`) registered next to the
  standard ones contributes VMs, browser rates and warnings; a failing region/provider does not affect the others; it is part
  of the cache key (and a cache of other regions is ignored); it is not called when it declares no regions; `PriceSource`
  builder with a custom registry; duplicated ids rejected; Device Farm follows the AWS regions; helpers: `pages`, streaming
  `readOffer`, `number`, `termYears` / `addCheapest` / `sortByTerm`, entry factories.
- `OptInProvidersTests` (fixtures `src/test/resources/pricing/*.json`, trimmed from the real APIs): Oracle shapes/prices/vCPU, cheapest
  family, block volume, ignored variants, missing compute or block volume items fail with a clear message; IBM profiles and prices
  (paged plan lists, duplicated plans, real eu-de pricing), a profile without deployment is a warning, a region without profiles
  fails while others work; providers registered but not called without regions; cache key unchanged when nothing is enabled and
  extended when enabled; `PriceSource` reads `usageprofile-regions-<id>`.
