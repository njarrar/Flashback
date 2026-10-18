# Places

`cities.tsv` lists every city with 50,000 people or more, one per line:

```
name	country code	latitude	longitude	population
```

Both apps ship this file and use it to turn a photo's location, or the phone's rough
location, into a city name, all on the phone. It is sorted by population, and a
city's row number is its ID in saved data, so add new rows only at the end.

## Source

Built from the GeoNames `cities15000` list (as packaged in the `geonamescache` 3.0.2
Python package), keeping cities with a population of 50,000 or more.

GeoNames data is © GeoNames, https://www.geonames.org, under the
[Creative Commons Attribution 4.0 License](https://creativecommons.org/licenses/by/4.0/).
