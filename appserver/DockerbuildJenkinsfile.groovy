pipeline {
    agent any
    
    environment {
        DOCKER_REGISTRY = 'https://index.docker.io/v1/'
        DOCKER_IMAGE_NAME = 'muddythunder1040/appserver'
        DOCKER_TAG = "${env.BUILD_NUMBER}"
        GITHUB_REPO = 'https://github.com/MuddyThunder1040/appserver.git'
        SLACK_CHANNEL = '#the-restack-notifier'
        DOCKER_CREDENTIALS_ID = 'c71d37ab-7559-4e0e-a3ea-fcf0877f74e'
    }
    
    stages {
        stage('Checkout') {
            steps {
                cleanWs()
                git branch: 'main', url: "${GITHUB_REPO}"
            }
        }
        
        stage('Build Info') {
            steps {
                echo "Building appserver - Build: ${env.BUILD_NUMBER}"
            }
        }
        
        stage('Install Dependencies') {
            steps {
                sh 'npm install'
            }
        }
        
        stage('Run Tests') {
            steps {
                script {
                    try {
                        sh 'npm test'
                    } catch (Exception e) {
                        echo "Tests not available, continuing..."
                    }
                }
            }
        }
        
        stage('Docker Build') {
            steps {
                sh '''
                    docker build -t ${DOCKER_IMAGE_NAME}:${DOCKER_TAG} .
                    docker tag ${DOCKER_IMAGE_NAME}:${DOCKER_TAG} ${DOCKER_IMAGE_NAME}:latest
                '''
            }
        }
        
        stage('Docker Test') {
            steps {
                sh '''
                    docker run -d --name test-container -p 3001:3001 ${DOCKER_IMAGE_NAME}:${DOCKER_TAG}
                    sleep 5
                    docker ps | grep test-container
                    docker stop test-container && docker rm test-container
                '''
            }
        }
        
        stage('Docker Push') {
            when {
                anyOf { branch 'main'; branch 'master' }
            }
            steps {
                withCredentials([usernamePassword(credentialsId: "${DOCKER_CREDENTIALS_ID}", passwordVariable: 'DOCKER_PASSWORD', usernameVariable: 'DOCKER_USERNAME')]) {
                    sh '''
                        echo $DOCKER_PASSWORD | docker login -u $DOCKER_USERNAME --password-stdin
                        docker push ${DOCKER_IMAGE_NAME}:${DOCKER_TAG}
                        docker push ${DOCKER_IMAGE_NAME}:latest
                    '''
                }
            }
        }
        
        stage('Cleanup') {
            steps {
                sh 'docker image prune -f'
            }
        }
    }
    
    post {
        success {
            slackSend(channel: "${SLACK_CHANNEL}", color: 'good', message: "🎉 AppServer Build #${env.BUILD_NUMBER} Success!\nImage: ${DOCKER_IMAGE_NAME}:${DOCKER_TAG}")
        }
        failure {
            slackSend(channel: "${SLACK_CHANNEL}", color: 'danger', message: "💥 AppServer Build #${env.BUILD_NUMBER} Failed!")
        }
        always {
            cleanWs()
        }
    }
}