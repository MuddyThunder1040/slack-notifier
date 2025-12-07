pipeline {
    agent any
    
    parameters {
        choice(
            name: 'ACTION',
            choices: ['deploy', 'destroy', 'status'],
            description: 'Action to perform on monitoring stack'
        )
    }
    
    stages {
        stage('Deploy Monitoring Stack') {
            when {
                expression { params.ACTION == 'deploy' }
            }
            steps {
                echo '📊 Deploying Prometheus + Grafana monitoring stack...'
                sh '''
                    cd /home/vishnu/jenkins-agent/workspace/cas-deploy || exit 1
                    cd monitoring
                    
                    export PATH="$PATH:~/bin:/usr/local/bin"
                    
                    terraform init
                    terraform apply -auto-approve
                    
                    echo ""
                    echo "==================================="
                    echo "✅ Monitoring Stack Deployed!"
                    echo "==================================="
                    terraform output -json
                '''
            }
        }
        
        stage('Destroy Monitoring Stack') {
            when {
                expression { params.ACTION == 'destroy' }
            }
            steps {
                echo '🗑️ Destroying monitoring stack...'
                sh '''
                    cd /home/vishnu/jenkins-agent/workspace/cas-deploy || exit 1
                    cd monitoring
                    
                    export PATH="$PATH:~/bin:/usr/local/bin"
                    
                    terraform destroy -auto-approve
                    
                    echo "✅ Monitoring stack destroyed"
                '''
            }
        }
        
        stage('Check Status') {
            steps {
                echo 'Checking monitoring stack status...'
                sh '''
                    echo "==================================="
                    echo "Monitoring Containers:"
                    echo "==================================="
                    docker ps --filter "name=prometheus" --filter "name=grafana" --filter "name=jmx" --format "table {{.Names}}\\t{{.Status}}\\t{{.Ports}}"
                    
                    echo ""
                    echo "==================================="
                    echo "Access URLs:"
                    echo "==================================="
                    echo "Grafana:    http://localhost:3000"
                    echo "Prometheus: http://localhost:9090"
                    echo "JMX Metrics: http://localhost:5556/metrics"
                    echo ""
                    echo "Grafana Login: admin / admin"
                '''
            }
        }
        
        stage('Configure Grafana') {
            when {
                expression { params.ACTION == 'deploy' }
            }
            steps {
                echo 'Waiting for services to be ready...'
                sh '''
                    echo "Waiting for Grafana to start..."
                    sleep 10
                    
                    # Check if services are responding
                    curl -s http://localhost:3000/api/health || echo "Grafana not ready yet"
                    curl -s http://localhost:9090/-/healthy || echo "Prometheus not ready yet"
                    curl -s http://localhost:5556/metrics | head -5 || echo "JMX Exporter not ready yet"
                    
                    echo ""
                    echo "==================================="
                    echo "📊 Next Steps:"
                    echo "==================================="
                    echo "1. Open Grafana: http://localhost:3000"
                    echo "2. Login: admin / admin"
                    echo "3. Add Prometheus data source:"
                    echo "   URL: http://prometheus:9090"
                    echo "4. Import dashboard ID: 11971"
                    echo "==================================="
                '''
            }
        }
    }
    
    post {
        success {
            echo "✅ Monitoring operation completed successfully!"
        }
        failure {
            echo "❌ Monitoring operation failed"
        }
    }
}
