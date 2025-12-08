pipeline {
    agent any
    
    parameters {
        choice(
            name: 'ACTION',
            choices: ['deploy', 'destroy', 'status', 'restart'],
            description: 'Action to perform on monitoring stack'
        )
        string(
            name: 'MONITORING_DIR',
            defaultValue: '/home/vishnu/monitoring',
            description: 'Base directory for monitoring configuration'
        )
        choice(
            name: 'DASHBOARD_ID',
            choices: ['1860', '3662', 'both'],
            description: 'Grafana dashboard to import: 1860 (Node Exporter Full), 3662 (SRE Overview), both'
        )
        booleanParam(
            name: 'SETUP_DATASOURCE',
            defaultValue: true,
            description: 'Automatically configure Prometheus datasource in Grafana'
        )
    }
    
    environment {
        PROMETHEUS_PORT = '9090'
        GRAFANA_PORT = '3000'
        NODE_EXPORTER_PORT = '9100'
        GRAFANA_ADMIN_USER = 'admin'
        GRAFANA_ADMIN_PASS = 'admin'
    }
    
    stages {
        stage('Create Directory Structure') {
            when {
                expression { params.ACTION == 'deploy' }
            }
            steps {
                echo '📁 Creating monitoring directory structure...'
                sh """
                    echo "Creating directories at ${params.MONITORING_DIR}"
                    mkdir -p ${params.MONITORING_DIR}/prometheus
                    mkdir -p ${params.MONITORING_DIR}/grafana
                    
                    echo "✅ Directory structure created:"
                    tree ${params.MONITORING_DIR} 2>/dev/null || ls -R ${params.MONITORING_DIR}
                """
            }
        }
        
        stage('Create Prometheus Config') {
            when {
                expression { params.ACTION == 'deploy' }
            }
            steps {
                echo '📝 Creating Prometheus configuration...'
                sh """
                    cat > ${params.MONITORING_DIR}/prometheus/prometheus.yml << 'EOF'
global:
  scrape_interval: 5s
  evaluation_interval: 5s

scrape_configs:
  - job_name: "prometheus"
    static_configs:
      - targets: ["localhost:9090"]

  - job_name: "node_exporter"
    static_configs:
      - targets: ["host.docker.internal:9100"]
    metric_relabel_configs:
      - source_labels: [__name__]
        regex: 'node_.*'
        action: keep

  - job_name: "cassandra_jmx"
    static_configs:
      - targets: ["host.docker.internal:5556"]
    scrape_interval: 10s
EOF

                    echo "✅ Prometheus config created:"
                    cat ${params.MONITORING_DIR}/prometheus/prometheus.yml
                """
            }
        }
        
        stage('Cleanup Existing Containers') {
            when {
                expression { params.ACTION == 'deploy' || params.ACTION == 'restart' }
            }
            steps {
                echo '🧹 Cleaning up existing monitoring containers...'
                sh '''
                    echo "Stopping and removing existing containers..."
                    docker stop node_exporter prometheus grafana 2>/dev/null || true
                    sleep 3
                    docker rm -f node_exporter prometheus grafana 2>/dev/null || true
                    
                    echo "Freeing up ports..."
                    lsof -ti:9100 | xargs -r kill -9 2>/dev/null || true
                    lsof -ti:9090 | xargs -r kill -9 2>/dev/null || true
                    lsof -ti:3000 | xargs -r kill -9 2>/dev/null || true
                    
                    echo "Waiting for ports to be released..."
                    sleep 5
                    
                    echo "Verifying ports are free..."
                    if lsof -ti:9100 > /dev/null 2>&1; then
                        echo "Port 9100 still in use, forcing cleanup..."
                        lsof -ti:9100 | xargs -r kill -9 2>/dev/null || true
                        sleep 2
                    fi
                    
                    if lsof -ti:9090 > /dev/null 2>&1; then
                        echo "Port 9090 still in use, forcing cleanup..."
                        lsof -ti:9090 | xargs -r kill -9 2>/dev/null || true
                        sleep 2
                    fi
                    
                    if lsof -ti:3000 > /dev/null 2>&1; then
                        echo "Port 3000 still in use, forcing cleanup..."
                        lsof -ti:3000 | xargs -r kill -9 2>/dev/null || true
                        sleep 2
                    fi
                    
                    echo "✅ Cleanup complete"
                '''
            }
        }
        
        stage('Deploy Node Exporter') {
            when {
                expression { params.ACTION == 'deploy' || params.ACTION == 'restart' }
            }
            steps {
                echo '🔧 Deploying Node Exporter...'
                sh """
                    echo "Starting Node Exporter on port ${env.NODE_EXPORTER_PORT}..."
                    docker run -d \\
                      --name=node_exporter \\
                      -p ${env.NODE_EXPORTER_PORT}:9100 \\
                      --restart always \\
                      --pid="host" \\
                      quay.io/prometheus/node-exporter:latest \\
                      --path.rootfs=/
                    
                    echo "Waiting for Node Exporter to be ready..."
                    sleep 5
                    
                    echo "✅ Node Exporter deployed"
                    docker ps --filter "name=node_exporter" --format "table {{.Names}}\\t{{.Status}}\\t{{.Ports}}"
                """
            }
        }
        
        stage('Verify Node Exporter') {
            when {
                expression { params.ACTION == 'deploy' || params.ACTION == 'restart' }
            }
            steps {
                echo '✅ Verifying Node Exporter metrics...'
                sh """
                    echo "Testing Node Exporter endpoint..."
                    if curl -s localhost:${env.NODE_EXPORTER_PORT}/metrics | head -20; then
                        echo ""
                        echo "✅ Node Exporter is working! Metrics available at http://localhost:${env.NODE_EXPORTER_PORT}/metrics"
                    else
                        echo "❌ Node Exporter verification failed"
                        exit 1
                    fi
                """
            }
        }
        
        stage('Deploy Prometheus') {
            when {
                expression { params.ACTION == 'deploy' || params.ACTION == 'restart' }
            }
            steps {
                echo '🚀 Deploying Prometheus...'
                sh """
                    echo "Starting Prometheus on port ${env.PROMETHEUS_PORT}..."
                    docker run -d \\
                      --name=prometheus \\
                      -p ${env.PROMETHEUS_PORT}:9090 \\
                      -v ${params.MONITORING_DIR}/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml \\
                      --add-host=host.docker.internal:host-gateway \\
                      --restart always \\
                      prom/prometheus
                    
                    echo "Waiting for Prometheus to be ready..."
                    sleep 10
                    
                    echo "✅ Prometheus deployed"
                    docker ps --filter "name=prometheus" --format "table {{.Names}}\\t{{.Status}}\\t{{.Ports}}"
                """
            }
        }
        
        stage('Verify Prometheus Targets') {
            when {
                expression { params.ACTION == 'deploy' || params.ACTION == 'restart' }
            }
            steps {
                echo '🎯 Verifying Prometheus targets...'
                sh """
                    echo "Checking Prometheus targets..."
                    sleep 5
                    
                    curl -s http://localhost:${env.PROMETHEUS_PORT}/api/v1/targets | python3 -m json.tool || true
                    
                    echo ""
                    echo "✅ Prometheus UI: http://localhost:${env.PROMETHEUS_PORT}"
                    echo "✅ Targets: http://localhost:${env.PROMETHEUS_PORT}/targets"
                """
            }
        }
        
        stage('Deploy Grafana') {
            when {
                expression { params.ACTION == 'deploy' || params.ACTION == 'restart' }
            }
            steps {
                echo '📊 Deploying Grafana...'
                sh """
                    echo "Starting Grafana on port ${env.GRAFANA_PORT}..."
                    docker run -d \\
                      --name=grafana \\
                      -p ${env.GRAFANA_PORT}:3000 \\
                      -v ${params.MONITORING_DIR}/grafana:/var/lib/grafana \\
                      -e "GF_SECURITY_ADMIN_USER=${env.GRAFANA_ADMIN_USER}" \\
                      -e "GF_SECURITY_ADMIN_PASSWORD=${env.GRAFANA_ADMIN_PASS}" \\
                      -e "GF_INSTALL_PLUGINS=grafana-clock-panel,grafana-simple-json-datasource" \\
                      --restart always \\
                      grafana/grafana
                    
                    echo "Waiting for Grafana to be ready..."
                    sleep 15
                    
                    echo "✅ Grafana deployed"
                    docker ps --filter "name=grafana" --format "table {{.Names}}\\t{{.Status}}\\t{{.Ports}}"
                """
            }
        }
        
        stage('Configure Prometheus Datasource') {
            when {
                expression { 
                    (params.ACTION == 'deploy' || params.ACTION == 'restart') && params.SETUP_DATASOURCE == true 
                }
            }
            steps {
                echo '🔗 Configuring Prometheus datasource in Grafana...'
                sh """
                    echo "Waiting for Grafana API to be ready..."
                    for i in {1..30}; do
                        if curl -s http://localhost:${env.GRAFANA_PORT}/api/health > /dev/null 2>&1; then
                            echo "Grafana is ready!"
                            break
                        fi
                        echo "Waiting... (\$i/30)"
                        sleep 2
                    done
                    
                    echo "Adding Prometheus datasource..."
                    curl -X POST \\
                      -H "Content-Type: application/json" \\
                      -u "${env.GRAFANA_ADMIN_USER}:${env.GRAFANA_ADMIN_PASS}" \\
                      -d '{
                        "name": "Prometheus",
                        "type": "prometheus",
                        "url": "http://prometheus:9090",
                        "access": "proxy",
                        "isDefault": true,
                        "jsonData": {
                          "timeInterval": "5s"
                        }
                      }' \\
                      http://localhost:${env.GRAFANA_PORT}/api/datasources || echo "Datasource may already exist"
                    
                    echo ""
                    echo "✅ Prometheus datasource configured"
                """
            }
        }
        
        stage('Import SRE Dashboards') {
            when {
                expression { 
                    (params.ACTION == 'deploy' || params.ACTION == 'restart') && params.SETUP_DATASOURCE == true 
                }
            }
            steps {
                echo '📊 Importing Grafana dashboards...'
                script {
                    def dashboards = []
                    if (params.DASHBOARD_ID == '1860' || params.DASHBOARD_ID == 'both') {
                        dashboards.add('1860')
                    }
                    if (params.DASHBOARD_ID == '3662' || params.DASHBOARD_ID == 'both') {
                        dashboards.add('3662')
                    }
                    
                    dashboards.each { dashboardId ->
                        sh """
                            echo "Importing dashboard ${dashboardId}..."
                            
                            # Download dashboard JSON from Grafana.com
                            curl -s https://grafana.com/api/dashboards/${dashboardId}/revisions/latest/download -o /tmp/dashboard_${dashboardId}.json
                            
                            # Get datasource UID
                            DATASOURCE_UID=\$(curl -s -u "${env.GRAFANA_ADMIN_USER}:${env.GRAFANA_ADMIN_PASS}" \\
                              http://localhost:${env.GRAFANA_PORT}/api/datasources/name/Prometheus | python3 -c "import sys, json; print(json.load(sys.stdin)['uid'])" 2>/dev/null || echo "")
                            
                            if [ -z "\$DATASOURCE_UID" ]; then
                                echo "Warning: Could not get datasource UID, using default"
                                DATASOURCE_UID="prometheus"
                            fi
                            
                            # Wrap dashboard JSON for import
                            cat > /tmp/import_${dashboardId}.json << EOF2
{
  "dashboard": \$(cat /tmp/dashboard_${dashboardId}.json),
  "overwrite": true,
  "inputs": [{
    "name": "DS_PROMETHEUS",
    "type": "datasource",
    "pluginId": "prometheus",
    "value": "Prometheus"
  }]
}
EOF2
                            
                            # Import dashboard
                            curl -X POST \\
                              -H "Content-Type: application/json" \\
                              -u "${env.GRAFANA_ADMIN_USER}:${env.GRAFANA_ADMIN_PASS}" \\
                              -d @/tmp/import_${dashboardId}.json \\
                              http://localhost:${env.GRAFANA_PORT}/api/dashboards/import
                            
                            echo ""
                            echo "✅ Dashboard ${dashboardId} imported"
                        """
                    }
                }
            }
        }
        
        stage('Display Access Information') {
            when {
                expression { params.ACTION == 'deploy' || params.ACTION == 'restart' || params.ACTION == 'status' }
            }
            steps {
                echo '📋 Monitoring Stack Information'
                sh """
                    echo ""
                    echo "==================================="
                    echo "✅ MONITORING STACK READY"
                    echo "==================================="
                    echo ""
                    echo "📊 Grafana Dashboard:"
                    echo "   URL: http://localhost:${env.GRAFANA_PORT}"
                    echo "   User: ${env.GRAFANA_ADMIN_USER}"
                    echo "   Pass: ${env.GRAFANA_ADMIN_PASS}"
                    echo ""
                    echo "🎯 Prometheus:"
                    echo "   URL: http://localhost:${env.PROMETHEUS_PORT}"
                    echo "   Targets: http://localhost:${env.PROMETHEUS_PORT}/targets"
                    echo ""
                    echo "📈 Node Exporter Metrics:"
                    echo "   URL: http://localhost:${env.NODE_EXPORTER_PORT}/metrics"
                    echo ""
                    echo "==================================="
                    echo "Running Containers:"
                    echo "==================================="
                    docker ps --filter "name=node_exporter" --filter "name=prometheus" --filter "name=grafana" \\
                      --format "table {{.Names}}\\t{{.Status}}\\t{{.Ports}}"
                    echo ""
                    echo "==================================="
                    echo "Imported Dashboards:"
                    echo "==================================="
                    echo "🟦 Dashboard 1860: Node Exporter Full"
                    echo "🟩 Dashboard 3662: SRE Overview"
                    echo ""
                    echo "Navigate to Grafana → Dashboards to view"
                    echo ""
                """
            }
        }
        
        stage('Check Status') {
            when {
                expression { params.ACTION == 'status' }
            }
            steps {
                echo '🔍 Checking monitoring stack status...'
                sh """
                    echo "==================================="
                    echo "Container Status:"
                    echo "==================================="
                    docker ps -a --filter "name=node_exporter" --filter "name=prometheus" --filter "name=grafana" \\
                      --format "table {{.Names}}\\t{{.Status}}\\t{{.Ports}}"
                    
                    echo ""
                    echo "==================================="
                    echo "Health Checks:"
                    echo "==================================="
                    
                    # Node Exporter
                    if curl -s http://localhost:${env.NODE_EXPORTER_PORT}/metrics > /dev/null 2>&1; then
                        echo "✅ Node Exporter: UP"
                    else
                        echo "❌ Node Exporter: DOWN"
                    fi
                    
                    # Prometheus
                    if curl -s http://localhost:${env.PROMETHEUS_PORT}/-/healthy > /dev/null 2>&1; then
                        echo "✅ Prometheus: UP"
                    else
                        echo "❌ Prometheus: DOWN"
                    fi
                    
                    # Grafana
                    if curl -s http://localhost:${env.GRAFANA_PORT}/api/health > /dev/null 2>&1; then
                        echo "✅ Grafana: UP"
                    else
                        echo "❌ Grafana: DOWN"
                    fi
                    
                    echo ""
                    echo "==================================="
                    echo "Prometheus Targets:"
                    echo "==================================="
                    curl -s http://localhost:${env.PROMETHEUS_PORT}/api/v1/targets | \\
                      python3 -c "import sys, json; targets = json.load(sys.stdin)['data']['activeTargets']; [print(f\\"  {t['job']}: {t['health']}\\" ) for t in targets]" 2>/dev/null || echo "Could not fetch targets"
                """
            }
        }
        
        stage('Destroy Monitoring Stack') {
            when {
                expression { params.ACTION == 'destroy' }
            }
            steps {
                echo '🗑️ Destroying monitoring stack...'
                sh """
                    echo "Stopping and removing containers..."
                    docker rm -f node_exporter prometheus grafana 2>/dev/null || true
                    
                    echo "Containers removed. Configuration preserved at ${params.MONITORING_DIR}"
                    echo ""
                    echo "To completely remove configuration:"
                    echo "  rm -rf ${params.MONITORING_DIR}"
                    echo ""
                    echo "✅ Monitoring stack destroyed"
                """
            }
        }
    }
    
    post {
        success {
            script {
                def message = ""
                switch(params.ACTION) {
                    case 'deploy':
                        message = "✅ Monitoring stack deployed successfully!\\n" +
                                  "Grafana: http://localhost:${env.GRAFANA_PORT} (admin/admin)\\n" +
                                  "Prometheus: http://localhost:${env.PROMETHEUS_PORT}"
                        break
                    case 'destroy':
                        message = "✅ Monitoring stack destroyed successfully"
                        break
                    case 'restart':
                        message = "✅ Monitoring stack restarted successfully"
                        break
                    case 'status':
                        message = "✅ Status check completed"
                        break
                }
                
                echo message
            }
        }
        
        failure {
            echo "❌ Pipeline failed. Check logs for details."
        }
        
        always {
            echo "Pipeline execution completed at ${new Date()}"
        }
    }
}
