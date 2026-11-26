<!-- PROJECT LOGO -->
<br />
<p align="center">
  <a href="https://github.com/IHTSDO/snap2snomed">
    <img src="images/SNOMED_Snap_2_01-286.png" alt="Logo" height="200">
  </a>

  <p align="center">
    A tool for creating and maintaining simple maps to SNOMED CT
  </p>
</p>

## About The Project
This project is a mapping tool designed to allow users to collaboratively create and maintain simple maps to SNOMED CT.

Below is the current vision for the project
<img src="images/vision.png" alt="Vision">

### Built With

* [Angular](https://angular.io/)
* [Spring Boot](https://spring.io/projects/spring-boot)
* [Maven](https://maven.apache.org/)
* [FHIR Terminology Module](https://www.hl7.org/fhir/terminology-module.html)

## Getting Started

To get a local copy up and running follow these simple steps.

### Prerequisites

This is an example of how to list things you need to use the software and how to install them.
* Maven - refer to [Maven's installation instructions](https://maven.apache.org/download.cgi)

### Installation

1. Clone the repo
   ```sh
   git clone https://github.com/IHTSDO/snap2snomed.git
   ```
2. Run the Maven build
   ```sh
   mvn clean package
   ```


### Running locally without AWS Cognito

You can run the whole stack on one machine, with no AWS account:
- **Database:** MySQL in Docker.
- **API:** started with the `devauth` profile, which makes it its own (fake) Cognito-compatible identity
  provider.
- **UI:** the Angular dev server.

**Never enable `devauth` in a deployed environment**: it accepts any user.

#### Prerequisites

- Docker
- JDK 21 or later, and Maven 3.9
- Node 20.19 or later
- yarn 1. `npx yarn@1.22.22` works without installing it.

#### 1. Database

```sh
cd api && docker compose up -d db
```

This starts MySQL 8 on port 3306, with an empty `s2s` database and a passwordless `root` user. The data is
kept in the `mysql_db` Docker volume. To start from scratch, run `docker compose down -v`. The API creates
and migrates the schema (Flyway) when it starts.

#### 2. API

From the repository root:

```sh
mvn -pl api spring-boot:run -Dspring-boot.run.profiles=local,devauth \
  -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:aws-wrapper:mysql://localhost:3306/s2s --spring.datasource.username=root --snap2snomed.defaultTerminologyServer.url=https://implementation-demo.snomedtools.org/fhir"
```

The API listens on <http://localhost:8080>. The last argument chooses the FHIR terminology server; the UI
also uses it, since it reads it from the API's `/config` endpoint. Without that argument, the default is
CSIRO's public Ontoserver.

The maps from SNOMED CT (see [docs/snomed-to-local](docs/snomed-to-local/implementation.md)) were only
tested with the SnowstormX server shown above. They need ECL expansion and a POST `$expand` with an inline
value set.

#### 3. UI

The first time:

```sh
cd ui/snapclient
cp .env-sample .env     # then set API_BASE_URL=http://localhost:8080 in .env
```

`@csiro/shrimp-hierarchy-view` is a private package. A plain `yarn install` fails with a 401 from CSIRO's
package feed. A stub of it is kept in `dev-stubs/shrimp-hierarchy-view`; it shows a placeholder in the
alternative "shrimp" hierarchy view.

Install with the stub:
1. Point the dependency at the stub (`yarn add` alone would still download the locked package).
2. Install.
3. Restore the two files this changes, so they are not committed.

```sh
sed -i.bak 's#"@csiro/shrimp-hierarchy-view": "^0.0.8"#"@csiro/shrimp-hierarchy-view": "file:./dev-stubs/shrimp-hierarchy-view"#' package.json
npx yarn@1.22.22 install
git checkout -- package.json yarn.lock && rm package.json.bak
```

Repeat these three commands whenever you need to reinstall the dependencies.

Then start the dev server. `--ssl=false` is needed because the dev certificate is self-signed:

```sh
npx yarn@1.22.22 start --ssl=false
```

The UI is then at <http://localhost:4200>.

#### 4. Log in and get started

Click **Login**. With `devauth`, the API shows its own login page:
- Pick any email and name, and whether the user is an admin.
- The same email always maps to the same user.

The database starts empty. To try the tool:
1. Create a map.
2. Import a code set (a CSV or TSV file with a code and a display column) as its source.
3. For a map from SNOMED CT, import the local code set as its target.

#### Troubleshooting

- **The UI is blank or asks you to log in again after the API restarts.** Tokens are signed with a key
  generated when the API starts. Go to the home page and log in again.
- **`yarn install` fails with 401.** This is the private package: see step 3.
- **Port 3306 or 8080 is in use.** Another MySQL or API is already running. Stop it, or change the port
  mapping and the datasource URL.


<!-- CONTRIBUTING -->
## Contributing

Contributions are what make the open source community such an amazing place to be learn, inspire, and create. Any contributions you make are **greatly appreciated**.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

This project uses the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html), please stick to this style to avoid unnecessary churn in the codebase. You can get style formatters for [Eclipse](https://github.com/google/styleguide/blob/gh-pages/eclipse-java-google-style.xml) and [IntelliJ](https://github.com/google/styleguide/blob/gh-pages/intellij-java-google-style.xml).

<!-- LICENSE -->
## License

Distributed under the Apache 2.0 License. See `LICENSE` for more information.



<!-- CONTACT -->
## Contact

Project Link: [https://github.com/IHTSDO/snap2snomed](https://github.com/IHTSDO/snap2snomed)



<!-- ACKNOWLEDGEMENTS -->
## Acknowledgements

* [SNOMED International](https://www.snomed.org/)
