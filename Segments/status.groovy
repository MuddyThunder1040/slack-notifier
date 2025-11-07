pipeline {
    agent any

    parameters {
        string(name: 'FILE_NAME', defaultValue: 'newfile.txt', description: 'Name of the file to check')
    }
    stages {
        stage('check for File') {
            steps {
                script {
                    def filePath = params.FILE_NAME
                    echo fileExists(filePath) ? "File '${filePath}' exists." : "File '${filePath}' does not exist."
                }
            }
        }
    }
}