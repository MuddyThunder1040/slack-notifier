pipeline {
    agent any
    
    environment {
        ENABLE_SLACK = 'true'
        SLACK_CHANNEL = '#the-restack-notifier'
        SLACK_CREDENTIAL_ID = 'slack-token'
        TF_WORKSPACE = '/var/jenkins_home/workspace/cassandra-cluster'
        GIT_REPO = 'https://github.com/MuddyThunder1040/aws-topology.git'
    }
    
    parameters {
        choice(
            name: 'TF_ACTION',
            choices: ['plan', 'apply', 'destroy', 'init', 'validate', 'show', 'output'],
            description: 'Terraform action to perform'
        )
        booleanParam(
            name: 'AUTO_APPROVE',
            defaultValue: false,
            description: 'Auto-approve terraform apply/destroy (skip confirmation)'
        )
    }
    
    stages {
        stage('Checkout') {
            steps {
                echo "Cloning repository: ${GIT_REPO}"
                git branch: 'master', url: "${GIT_REPO}"
            }
        }
        
        stage('Install Terraform') {
            steps {
                echo 'Checking Terraform installation...'
                sh '''
                    if ! command -v terraform &> /dev/null; then
                        echo "Terraform not found. Installing..."
                        mkdir -p ~/bin
                        cd /tmp
                        
                        # Try curl first, then wget
                        if command -v curl &> /dev/null; then
                            curl -sL https://releases.hashicorp.com/terraform/1.6.6/terraform_1.6.6_linux_amd64.zip -o terraform.zip
                        elif command -v wget &> /dev/null; then
                            wget -q https://releases.hashicorp.com/terraform/1.6.6/terraform_1.6.6_linux_amd64.zip -O terraform.zip
                        else
                            echo "ERROR: Neither curl nor wget found. Cannot download Terraform."
                            echo "Please install Terraform manually on the Jenkins agent."
                            exit 1
                        fi
                        
                        unzip -o terraform.zip
                        chmod +x terraform
                        mv terraform ~/bin/
                        rm -f terraform.zip
                        export PATH=$PATH:~/bin
                        echo "Terraform installed successfully to ~/bin"
                    else
                        echo "Terraform already installed: $(terraform version)"
                    fi
                    
                    # Verify installation
                    export PATH=$PATH:~/bin
                    if command -v terraform &> /dev/null; then
                        terraform version
                    elif [ -f ~/bin/terraform ]; then
                        ~/bin/terraform version
                    else
                        echo "ERROR: Terraform installation failed"
                        exit 1
                    fi
                '''
            }
        }
        
        stage('Terraform Init') {
            when {
                expression { params.TF_ACTION == 'init' || params.TF_ACTION == 'plan' || params.TF_ACTION == 'apply' || params.TF_ACTION == 'destroy' }
            }
            steps {
                echo 'Initializing Terraform...'
                sh '''
                    export PATH=$PATH:~/bin:/usr/local/bin
                    terraform init
                '''
            }
        }
        
        stage('Terraform Validate') {
            when {
                expression { params.TF_ACTION == 'validate' }
            }
            steps {
                echo 'Validating Terraform configuration...'
                sh '''
                    export PATH=$PATH:~/bin:/usr/local/bin
                    terraform init -backend=false
                    terraform validate
                '''
            }
        }
        
        stage('Terraform Plan') {
            when {
                expression { params.TF_ACTION == 'plan' }
            }
            steps {
                echo 'Creating Terraform plan...'
                sh '''
                    export PATH=$PATH:~/bin:/usr/local/bin
                    terraform plan -out=tfplan
                    echo ""
                    echo "==================================="
                    echo "Plan created successfully!"
                    echo "Review the plan above."
                    echo "==================================="
                '''
            }
        }
        
        stage('Terraform Apply') {
            when {
                expression { params.TF_ACTION == 'apply' }
            }
            steps {
                script {
                    echo 'Applying Terraform configuration...'
                    if (params.AUTO_APPROVE) {
                        sh '''
                            export PATH=$PATH:~/bin:/usr/local/bin
                            terraform apply -auto-approve
                            echo ""
                            echo "==================================="
                            echo "✅ Cassandra Cluster Deployed!"
                            echo "==================================="
                        '''
                    } else {
                        sh '''
                            export PATH=$PATH:~/bin:/usr/local/bin
                            terraform plan -out=tfplan
                            echo ""
                            echo "==================================="
                            echo "Review the plan above"
                            echo "==================================="
                        '''
                        input message: 'Approve terraform apply?', ok: 'Apply'
                        sh '''
                            export PATH=$PATH:~/bin:/usr/local/bin
                            terraform apply tfplan
                            echo ""
                            echo "==================================="
                            echo "✅ Cassandra Cluster Deployed!"
                            echo "==================================="
                        '''
                    }
                }
            }
        }
        
        stage('Terraform Destroy') {
            when {
                expression { params.TF_ACTION == 'destroy' }
            }
            steps {
                script {
                    echo 'Destroying Terraform resources...'
                    if (params.AUTO_APPROVE) {
                        sh '''
                            export PATH=$PATH:~/bin:/usr/local/bin
                            terraform destroy -auto-approve
                            echo ""
                            echo "==================================="
                            echo "🗑️  Cassandra Cluster Destroyed!"
                            echo "==================================="
                        '''
                    } else {
                        sh '''
                            export PATH=$PATH:~/bin:/usr/local/bin
                            terraform plan -destroy -out=tfplan
                            echo ""
                            echo "==================================="
                            echo "Review the destroy plan above"
                            echo "==================================="
                        '''
                        input message: 'Approve terraform destroy?', ok: 'Destroy'
                        sh '''
                            export PATH=$PATH:~/bin:/usr/local/bin
                            terraform destroy -auto-approve
                            echo ""
                            echo "==================================="
                            echo "🗑️  Cassandra Cluster Destroyed!"
                            echo "==================================="
                        '''
                    }
                }
            }
        }
        
        stage('Terraform Show') {
            when {
                expression { params.TF_ACTION == 'show' }
            }
            steps {
                echo 'Showing current Terraform state...'
                sh '''
                    export PATH=$PATH:~/bin:/usr/local/bin
                    terraform show
                '''
            }
        }
        
        stage('Terraform Output') {
            when {
                expression { params.TF_ACTION == 'output' }
            }
            steps {
                echo 'Displaying Terraform outputs...'
                sh '''
                    export PATH=$PATH:~/bin:/usr/local/bin
                    terraform output
                    echo ""
                    echo "==================================="
                    echo "Cassandra Cluster Information"
                    echo "==================================="
                '''
            }
        }
        
        stage('Verify Cluster') {
            when {
                expression { params.TF_ACTION == 'apply' }
            }
            steps {
                script {
                    echo 'Waiting for Cassandra cluster to initialize...'
                    sh '''
                        echo "Waiting 60 seconds for nodes to start..."
                        sleep 60
                        
                        echo ""
                        echo "==================================="
                        echo "Checking Cassandra cluster status..."
                        echo "==================================="
                        
                        if docker exec cassandra-node1 nodetool status 2>/dev/null; then
                            echo ""
                            echo "✅ Cluster is UP and running!"
                        else
                            echo "⚠️  Cluster is still initializing. Check status later with:"
                            echo "   docker exec -it cassandra-node1 nodetool status"
                        fi
                        
                        echo ""
                        echo "==================================="
                        echo "Connection Information:"
                        echo "==================================="
                        echo "CQL Ports:"
                        echo "  Node 1: localhost:9042"
                        echo "  Node 2: localhost:9043"
                        echo "  Node 3: localhost:9044"
                        echo "  Node 4: localhost:9045"
                        echo ""
                        echo "Connect: docker exec -it cassandra-node1 cqlsh"
                        echo "Status:  docker exec -it cassandra-node1 nodetool status"
                        echo "==================================="
                    '''
                }
            }
        }
    }
    
    post {
        success {
            script {
                if (env.ENABLE_SLACK == 'true') {
                    def actionEmoji = [
                        'init': '🔧',
                        'validate': '✅',
                        'plan': '📋',
                        'apply': '🚀',
                        'destroy': '🗑️',
                        'show': '👁️',
                        'output': '📊'
                    ]
                    def emoji = actionEmoji[params.TF_ACTION] ?: '✅'
                    
                    slackSend(
                        botUser: true,
                        channel: env.SLACK_CHANNEL,
                        color: 'good',
                        message: "${emoji} Terraform ${params.TF_ACTION.toUpperCase()} completed successfully!\nJob: ${env.JOB_NAME}\nBuild: ${env.BUILD_NUMBER}\nAction: ${params.TF_ACTION}\nAuto-Approve: ${params.AUTO_APPROVE}\nURL: ${env.BUILD_URL}",
                        tokenCredentialId: env.SLACK_CREDENTIAL_ID,
                        failOnError: false
                    )
                }
            }
        }
        failure {
            script {
                if (env.ENABLE_SLACK == 'true') {
                    slackSend(
                        botUser: true,
                        channel: env.SLACK_CHANNEL,
                        color: 'danger',
                        message: "💥 Terraform ${params.TF_ACTION.toUpperCase()} failed!\nJob: ${env.JOB_NAME}\nBuild: ${env.BUILD_NUMBER}\nAction: ${params.TF_ACTION}\nURL: ${env.BUILD_URL}",
                        tokenCredentialId: env.SLACK_CREDENTIAL_ID,
                        failOnError: false
                    )
                }
            }
        }
        always {
            echo "Terraform ${params.TF_ACTION} operation completed"
        }
    }
}
