pipeline {
    agent any

    parameters {
        string(name: 'FILE_NAME', defaultValue: 'newfile.txt', description: 'Name of the file to remove')
    }
    stages {
        stage('Remove File') {
            steps {
                script {
                    // Use the parameter instead of hardcoded value
                    def filePath = params.FILE_NAME

                    // Check if file exists before removing
                    if (fileExists(filePath)) {
                        sh "rm -f ${filePath}"
                        echo "File '${filePath}' has been removed."
                    } else {
                        echo "File '${filePath}' does not exist, nothing to remove."
                    }
                }
            }
        }
    }
}