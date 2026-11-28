# Spec: `giis.retorch.profiling.usageprofile.pricing`

## Purpose
Gets the prices (USD) of the cloud objects the cost calculation needs, from the public price APIs of Azure and AWS or from a
JSON cache, and holds them in an immutable `PriceCatalog`. Network is never used in tests.

## Public API
`PriceCatalog` (and its nested `VmOffer`, `ContainerRate`, `StorageRate`, `BrowserRate`, `Builder`), `PriceSource`,
`PricingFetcher`, `PriceProvider`, `AzurePrices`, `AwsPrices`, `DeviceFarmPrices` and `HttpGet`. `PriceCache` and
`AwsReservations` are package-private. Other packages only need `PriceCatalog` (cost, report, plan tests and
`main.UsageProfileRequest`) and `PriceSource` (`main`).

## Price catalog
- `PriceCatalog`: immutable (public final fields, unmodifiable lists, nested `VmOffer`, `ContainerRate`, `StorageRate`,
  `BrowserRate` also immutable); built with `PriceCatalog.builder()` (`fetchedAt`, `regionsKey`,
  `addVm/addContainer/addStorage/addBrowser/addWarning`, `merge`, `replaceVms`, `build`); `merge(other)` and
  `withWarnings(list)` return a new catalog. Fields: `schemaVersion` (constant `SCHEMA_VERSION`, now 3, unchanged: the JSON
  layout did not change), `vms`, `containers`, `storage`, `browsers` (`BrowserRate(provider, region, service, name,
  minuteUsd)`: a browser or device rented by the minute), `warnings`, `fetchedAtEpochMs`, `regionsKey`; JSON load/save
  (Jackson through `@JsonCreator`; missing lists are empty). `VmOffer.reservations` is a list of `dto.ReservationOptionDTO`
  (empty if there is no data). A file without `schemaVersion` loads as 0.

## Providers and fetching
- Price access is split in `PriceProvider` (`name()`, `fetch(region)`), implemented by `AzurePrices`, `AwsPrices` and
  `DeviceFarmPrices` (the last one global: its region argument is ignored), plus `PriceCache` (the JSON file: returns the
  cache only if it has the current schema version and the same regions key) and `HttpGet` (`open(url)` -> `InputStream`,
  `HttpGet.standard()` = the real HTTP). `new PricingFetcher(HttpGet)` wires the three providers; the package-private
  constructor takes the three providers, so every flow is tested with canned responses or fakes.
- `PricingFetcher.fetchOrLoad(azureRegions, awsRegions, cacheFile, maxAgeMs)`: regions are comma separated lists
  (`PricingFetcher.split`), blank disables a provider. The cache is used only if younger than `maxAgeMs`, built for the same
  regions **and** with the current `schemaVersion` (an old cache is ignored and the prices are fetched again); if every
  provider fails and a cache exists, the cache is returned with a warning; if nothing is available it throws `IOException`
  ("No prices ..."). A failing provider/region only adds a warning.
- `PriceSource(azureRegions, awsRegions, cacheFile, maxAgeHours).load()` bundles those four settings (used by the toolbox and
  `UsageProfileMain`).
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

## Tests (`src/test/java/.../usageprofile/pricing`)
- `PricingTests`: Azure parsers ignore Spot and Windows, AWS offer read in streaming and filtered, Azure reservations (all
  upfront per term), AWS `terms.Reserved` (standard only, No/All Upfront, 1/3 years), cache without or with another
  `schemaVersion` ignored, fresh cache avoids the network, `PriceSource` uses the cache for the same regions, comma separated
  regions and blank disables a provider, Device Farm keeps only metered browsers and devices (per-minute price).
- `PricingFetcherTests`: fresh cache without provider calls, expired cache refetched and saved, cache of other regions
  ignored, all providers failing falls back to the expired cache with warnings, nothing available throws, one failing region
  only warns, Device Farm only with AWS and once, Azure with canned HTTP responses, HTTP failure as warnings.
