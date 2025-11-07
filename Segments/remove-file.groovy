pipeline {
    agent any

    parameters {
        string(name: 'FILE_NAME', defaultValue: 'newfile.txt', description: 'Name of the file to remove')
    }
    stages {
        stage('Remove File') {
            steps {
                script {
                    // Define the file path
                    def filePath = 'newfile.txt'

                    // Remove the file
                    deleteFile file: filePath

                    // Optionally, print a message to confirm the file was removed
                    echo "File '${filePath}' has been removed."
                }
            }
        }
    }

                    // Write content to the file
                    writeFile file: filePath, text: fileContent

                    // Optionally, print a message to confirm the file was added
                    echo "File '${filePath}' has been created with content."
                }
            }
        }
    }
}