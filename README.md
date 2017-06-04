# zimdroid
This is a lightweight Android library for working with ZIM files, which are compilations of HTML
and media content most often used for bundling Wikipedia articles for offline use.  The library
provides random access functionality to retrieve articles and their associated media from ZIM
compilations, as well as searching of titles in the ZIM file (by prefix), and retrieval of random
titles.

### Include in your project

Add the following to your build.gradle:

`compile 'com.dmitrybrant:zimdroid:0.0.5'`

### Basic usage

The class `ZIMReader` contains all the functions necessary for parsing and extracting content from
a `ZIMFile`. It can be used like this:

```
ZIMReader reader = new ZIMReader(new ZIMFile("/path/to/file.zim"));
List<String> results = reader.searchByPrefix("cat", 10);
String randomTitle = reader.getRandomTitle();
String html = reader.getHtmlForTitle(randomTitle);
```

...and so on.

### Uploading to Bintray

(For my own reference, and sanity)

When ready to publish a new release to Bintray, run the following:

`./gradlew build install bintrayUpload`

The `build` step builds the project, the `install` step invokes Maven to generate the POM file and
install the project to your local repository, and the `bintrayUpload` step actually uploads it to
Bintray. In order to upload successfully, make sure to add these lines to `local.properties` in the
root directory of the project:

```
bintray.user=<your username on bintray>
bintray.apikey=<your API key from bintray>
```
