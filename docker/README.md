# Docker Setup

This directory holds the docker deployment and a docker-compose setup to start the project locally.

The lovely gradle plugin is used for deployment and docker builds, pls read
 https://github.com/lovelysystems/lovely-gradle-plugin before continuing.

## Deployment

1. Prepare the release:

Add a valid release entry into CHANGES.rst. Then commit "prepare release x.y.z" and push.

2. Run createTag

This will validate if all changes has been committed and of so it will create
a git tag based on the version from CHANGES.rst.

```
./gradlew createTag
```

3. Build a docker image

This step will build a docker image with the same as the git tag:

```
./gradlew buildDockerImage
```

4. Push the docker image

This step will push the just built docker image to the registry:

```
./gradlew pushDockerImage
```

## Running the docker image locally

In order to run the docker image locally you need to build it first using the gradle commands.
The `dev` tag is used to refer to the image, to ensure the image has been built before starting 
docker-compose.

```
docker-compose up -d
open http://localhost:18080
```
