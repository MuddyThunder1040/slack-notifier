# AppServer Jenkins Pipeline

This directory contains Jenkins pipeline configurations for building and deploying the AppServer application from the GitHub repository: https://github.com/MuddyThunder1040/appserver.git

## Files

- `DockerbuildJenkinsfile.groovy` - Main pipeline for building and testing the AppServer Docker image
- `AppServerPipeline.groovy` - Alternative orchestration pipeline (optional)

## Pipeline Features

### DockerbuildJenkinsfile.groovy

This pipeline includes the following stages:

1. **Checkout** - Clones the AppServer repository from GitHub
2. **Build Info** - Displays build information and package details
3. **Install Dependencies** - Runs `npm install` to install Node.js dependencies
4. **Run Tests** - Executes `npm test` (gracefully handles if no tests are configured)
5. **Docker Build** - Builds the Docker image using the Dockerfile
6. **Docker Test** - Tests that the Docker container starts successfully
7. **Docker Push** - Pushes to Docker registry (conditional on branch)
8. **Cleanup** - Cleans up Docker images and containers

### Slack Notifications

The pipeline sends notifications to the configured Slack channel (`#the-restack-notifier`) for:
- ✅ Successful builds
- ❌ Failed builds  
- ⚠️ Unstable builds

## Setup Instructions

### 1. Create Jenkins Job

1. Create a new Pipeline job in Jenkins
2. In the Pipeline section, choose "Pipeline script from SCM"
3. Set SCM to Git and use this repository URL
4. Set the Script Path to `appserver/DockerbuildJenkinsfile.groovy`

### 2. Configure Environment Variables

Update the following environment variables in the pipeline:

```groovy
environment {
    DOCKER_REGISTRY = 'your-registry.com'  // Your Docker registry URL
    DOCKER_IMAGE_NAME = 'appserver'        // Docker image name
    SLACK_CHANNEL = '#your-channel'        // Your Slack channel
}
```

### 3. Prerequisites

Ensure your Jenkins environment has:
- Docker installed and accessible
- Node.js and npm installed
- Slack plugin configured with proper credentials
- Git access to the AppServer repository

### 4. Optional: Docker Registry Setup

To enable Docker image pushing:

1. Uncomment the Docker push section in the pipeline
2. Configure Docker registry credentials in Jenkins
3. Update the `DOCKER_REGISTRY` environment variable

```groovy
docker.withRegistry("https://${DOCKER_REGISTRY}", 'docker-registry-credentials') {
    def dockerImage = docker.image("${DOCKER_IMAGE_NAME}:${DOCKER_TAG}")
    dockerImage.push()
    dockerImage.push("latest")
}
```

## AppServer Application Details

The pipeline builds a Node.js Express application that:
- Runs on port 3001
- Uses Express.js framework
- Includes a library management system API
- Is containerized using Docker with Node.js 18 Alpine base image

## Customization

### Branch Configuration

By default, the pipeline pushes Docker images only for `main` or `master` branches. Modify the `when` condition in the Docker Push stage to change this:

```groovy
when {
    anyOf {
        branch 'main'
        branch 'master'
        branch 'your-branch'  // Add your branches here
    }
}
```

### Test Configuration

If the AppServer repository adds proper tests, the pipeline will automatically run them. Currently, it gracefully handles the absence of tests.

### Health Checks

You can add application health checks in the Docker Test stage:

```groovy
// Add after container startup
curl -f http://localhost:3001/health || exit 1
```

## Troubleshooting

### Common Issues

1. **Docker Build Fails**: Check that the Dockerfile exists in the repository root
2. **NPM Install Fails**: Ensure package.json exists and dependencies are valid
3. **Container Doesn't Start**: Check application logs and port configurations
4. **Slack Notifications Not Working**: Verify Slack plugin configuration and channel permissions

### Debug Commands

```bash
# Check Docker images
docker images | grep appserver

# Check running containers
docker ps | grep appserver

# View container logs
docker logs <container-id>
```

## Pipeline Parameters

The `AppServerPipeline.groovy` file includes parameters for:
- `FORCE_PUSH`: Force push to registry regardless of branch
- `BRANCH`: Select which branch to build

## Integration with Main Pipeline

You can integrate this AppServer pipeline with your main restack pipeline by adding a stage:

```groovy
stage('Build AppServer') {
    steps {
        build job: 'appserver-docker-build', wait: true
    }
}
```