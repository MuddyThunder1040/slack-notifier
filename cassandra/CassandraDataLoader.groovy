pipeline {
    agent any
    
    options {
        description('Python-based data loader pipeline for inserting stock market test data into Cassandra cluster with configurable batch sizes and parallel workers')
    }
    
    parameters {
        choice(
            name: 'NUM_RECORDS',
            choices: ['10000', '50000', '100000', '500000', '1000000', '2000000', '4000000'],
            description: 'Number of stock market records to insert'
        )
        choice(
            name: 'BATCH_SIZE',
            choices: ['50', '100', '500', '1000', '5000'],
            description: 'Batch size for inserts (higher = faster but more memory)'
        )
        choice(
            name: 'NUM_WORKERS',
            choices: ['1', '2', '4', '8'],
            description: 'Number of parallel workers for data loading'
        )
        string(
            name: 'CASSANDRA_HOST',
            defaultValue: 'cassandra-node-1',
            description: 'Cassandra node to connect to'
        )
        booleanParam(
            name: 'CREATE_SCHEMA',
            defaultValue: true,
            description: 'Create keyspace and tables before loading data'
        )
        booleanParam(
            name: 'DROP_EXISTING',
            defaultValue: false,
            description: 'Drop existing keyspace before creating (WARNING: deletes all data)'
        )
    }
    
    stages {
        stage('Setup Python Environment') {
            steps {
                echo 'Installing Python dependencies...'
                sh '''
                    # Check Python installation
                    if ! command -v python3 &> /dev/null; then
                        echo "ERROR: Python3 not found. Please install Python 3.8+"
                        exit 1
                    fi
                    
                    echo "Python3 found: $(python3 --version)"
                    
                    # Install required packages
                    echo "Installing Python packages..."
                    pip3 install --user --quiet cassandra-driver faker numpy pandas 2>/dev/null || \
                    python3 -m pip install --user --quiet cassandra-driver faker numpy pandas || \
                    echo "Some packages may already be installed"
                    
                    echo "✅ Python environment ready"
                '''
            }
        }
        
        stage('Create Data Loader Script') {
            steps {
                script {
                    sh '''
                        cat > /tmp/cassandra_loader.py << 'PYTHON_SCRIPT'
import sys
import random
import time
from datetime import datetime, timedelta
from cassandra.cluster import Cluster
from cassandra.query import BatchStatement, SimpleStatement
from cassandra import ConsistencyLevel
from faker import Faker
import multiprocessing as mp
from concurrent.futures import ThreadPoolExecutor
import numpy as np

fake = Faker()

# Stock market companies (top tech stocks and indices)
COMPANIES = [
    'AAPL', 'MSFT', 'GOOGL', 'AMZN', 'META', 'TSLA', 'NVDA', 'AMD', 
    'INTC', 'NFLX', 'ORCL', 'IBM', 'CSCO', 'ADBE', 'CRM', 'PYPL',
    'UBER', 'LYFT', 'SPOT', 'SQ', 'SHOP', 'SNAP', 'TWTR', 'PINS',
    'ZM', 'DOCU', 'OKTA', 'NET', 'DDOG', 'SNOW', 'PLTR', 'RBLX'
]

SECTORS = ['Technology', 'Finance', 'Healthcare', 'Energy', 'Consumer', 'Industrial']
EXCHANGES = ['NYSE', 'NASDAQ', 'LSE', 'TSE', 'HKEX']

def create_schema(session, drop_existing=False):
    """Create Cassandra keyspace and tables"""
    print("Creating schema...")
    
    if drop_existing:
        print("Dropping existing keyspace...")
        session.execute("DROP KEYSPACE IF EXISTS stock_market")
    
    # Create keyspace with replication factor 1 for single node
    session.execute("""
        CREATE KEYSPACE IF NOT EXISTS stock_market 
        WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}
    """)
    
    session.set_keyspace('stock_market')
    
    # Table 1: Stock prices (time-series data)
    session.execute("""
        CREATE TABLE IF NOT EXISTS stock_prices (
            symbol text,
            trade_date date,
            trade_time timestamp,
            open_price decimal,
            close_price decimal,
            high_price decimal,
            low_price decimal,
            volume bigint,
            PRIMARY KEY ((symbol, trade_date), trade_time)
        ) WITH CLUSTERING ORDER BY (trade_time DESC)
    """)
    
    # Table 2: Companies (reference data)
    session.execute("""
        CREATE TABLE IF NOT EXISTS companies (
            symbol text PRIMARY KEY,
            company_name text,
            sector text,
            exchange text,
            market_cap bigint,
            ipo_date date,
            ceo text,
            headquarters text
        )
    """)
    
    # Table 3: Trades (high-frequency trading data)
    session.execute("""
        CREATE TABLE IF NOT EXISTS trades (
            trade_id uuid,
            symbol text,
            trade_time timestamp,
            price decimal,
            quantity int,
            trade_type text,
            trader_id text,
            PRIMARY KEY (symbol, trade_time, trade_id)
        ) WITH CLUSTERING ORDER BY (trade_time DESC, trade_id ASC)
    """)
    
    # Table 4: Portfolio holdings (investor data)
    session.execute("""
        CREATE TABLE IF NOT EXISTS portfolio_holdings (
            investor_id text,
            symbol text,
            purchase_date date,
            quantity int,
            purchase_price decimal,
            current_value decimal,
            PRIMARY KEY (investor_id, symbol)
        )
    """)
    
    # Table 5: Market analytics (aggregated data)
    session.execute("""
        CREATE TABLE IF NOT EXISTS market_analytics (
            symbol text,
            analysis_date date,
            avg_price decimal,
            total_volume bigint,
            price_change_percent decimal,
            volatility decimal,
            PRIMARY KEY (symbol, analysis_date)
        ) WITH CLUSTERING ORDER BY (analysis_date DESC)
    """)
    
    print("Schema created successfully!")

def generate_stock_price(symbol, base_date, base_price):
    """Generate realistic stock price data"""
    trade_date = base_date.date()
    trade_time = base_date
    
    # Add some randomness to price movements
    price_change = random.uniform(-0.05, 0.05)
    open_price = round(base_price, 2)
    close_price = round(base_price * (1 + price_change), 2)
    high_price = round(max(open_price, close_price) * random.uniform(1.0, 1.02), 2)
    low_price = round(min(open_price, close_price) * random.uniform(0.98, 1.0), 2)
    volume = random.randint(1000000, 100000000)
    
    return (symbol, trade_date, trade_time, open_price, close_price, high_price, low_price, volume)

def generate_company():
    """Generate company reference data"""
    symbol = random.choice(COMPANIES)
    company_name = f"{fake.company()} Inc."
    sector = random.choice(SECTORS)
    exchange = random.choice(EXCHANGES)
    market_cap = random.randint(1000000000, 3000000000000)
    ipo_date = fake.date_between(start_date='-30y', end_date='-1y')
    ceo = fake.name()
    headquarters = f"{fake.city()}, {fake.country()}"
    
    return (symbol, company_name, sector, exchange, market_cap, ipo_date, ceo, headquarters)

def insert_batch(session, table, data, batch_size):
    """Insert data in batches"""
    batch = BatchStatement(consistency_level=ConsistencyLevel.ONE)
    count = 0
    
    if table == 'stock_prices':
        insert_query = session.prepare("""
            INSERT INTO stock_prices (symbol, trade_date, trade_time, open_price, 
                                     close_price, high_price, low_price, volume)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """)
    elif table == 'companies':
        insert_query = session.prepare("""
            INSERT INTO companies (symbol, company_name, sector, exchange, 
                                  market_cap, ipo_date, ceo, headquarters)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """)
    elif table == 'trades':
        insert_query = session.prepare("""
            INSERT INTO trades (trade_id, symbol, trade_time, price, quantity, 
                               trade_type, trader_id)
            VALUES (uuid(), ?, ?, ?, ?, ?, ?)
        """)
    elif table == 'portfolio_holdings':
        insert_query = session.prepare("""
            INSERT INTO portfolio_holdings (investor_id, symbol, purchase_date, 
                                           quantity, purchase_price, current_value)
            VALUES (?, ?, ?, ?, ?, ?)
        """)
    elif table == 'market_analytics':
        insert_query = session.prepare("""
            INSERT INTO market_analytics (symbol, analysis_date, avg_price, 
                                         total_volume, price_change_percent, volatility)
            VALUES (?, ?, ?, ?, ?, ?)
        """)
    
    for row in data:
        batch.add(insert_query, row)
        count += 1
        
        if count >= batch_size:
            session.execute(batch)
            batch = BatchStatement(consistency_level=ConsistencyLevel.ONE)
            count = 0
    
    if count > 0:
        session.execute(batch)

def load_stock_prices(host, num_records, batch_size, worker_id, num_workers):
    """Load stock price data (worker function)"""
    cluster = Cluster([host])
    session = cluster.connect('stock_market')
    
    records_per_worker = num_records // num_workers
    start_record = worker_id * records_per_worker
    end_record = start_record + records_per_worker if worker_id < num_workers - 1 else num_records
    
    print(f"Worker {worker_id}: Loading {end_record - start_record} stock price records...")
    
    base_date = datetime.now() - timedelta(days=365)
    batch = []
    
    for i in range(start_record, end_record):
        symbol = random.choice(COMPANIES)
        days_offset = random.randint(0, 365)
        hours_offset = random.randint(0, 6)
        trade_time = base_date + timedelta(days=days_offset, hours=hours_offset)
        base_price = random.uniform(50, 500)
        
        batch.append(generate_stock_price(symbol, trade_time, base_price))
        
        if len(batch) >= batch_size:
            insert_batch(session, 'stock_prices', batch, batch_size)
            batch = []
            
            if (i - start_record) % 10000 == 0:
                print(f"Worker {worker_id}: Inserted {i - start_record} records...")
    
    if batch:
        insert_batch(session, 'stock_prices', batch, len(batch))
    
    cluster.shutdown()
    print(f"Worker {worker_id}: Completed!")

def load_trades(host, num_records, batch_size):
    """Load high-frequency trading data"""
    cluster = Cluster([host])
    session = cluster.connect('stock_market')
    
    print(f"Loading {num_records} trade records...")
    
    batch = []
    base_time = datetime.now() - timedelta(days=30)
    
    for i in range(num_records):
        symbol = random.choice(COMPANIES)
        trade_time = base_time + timedelta(seconds=random.randint(0, 2592000))
        price = round(random.uniform(50, 500), 2)
        quantity = random.randint(1, 10000)
        trade_type = random.choice(['BUY', 'SELL'])
        trader_id = f"TRADER_{random.randint(1000, 9999)}"
        
        batch.append((symbol, trade_time, price, quantity, trade_type, trader_id))
        
        if len(batch) >= batch_size:
            insert_batch(session, 'trades', batch, batch_size)
            batch = []
            
            if i % 10000 == 0:
                print(f"Inserted {i} trade records...")
    
    if batch:
        insert_batch(session, 'trades', batch, len(batch))
    
    cluster.shutdown()

def load_portfolio_holdings(host, num_records, batch_size):
    """Load investor portfolio data"""
    cluster = Cluster([host])
    session = cluster.connect('stock_market')
    
    print(f"Loading {num_records} portfolio holding records...")
    
    batch = []
    
    for i in range(num_records):
        investor_id = f"INV_{random.randint(10000, 99999)}"
        symbol = random.choice(COMPANIES)
        purchase_date = fake.date_between(start_date='-2y', end_date='today')
        quantity = random.randint(10, 10000)
        purchase_price = round(random.uniform(50, 500), 2)
        current_value = round(purchase_price * quantity * random.uniform(0.8, 1.3), 2)
        
        batch.append((investor_id, symbol, purchase_date, quantity, purchase_price, current_value))
        
        if len(batch) >= batch_size:
            insert_batch(session, 'portfolio_holdings', batch, batch_size)
            batch = []
            
            if i % 10000 == 0:
                print(f"Inserted {i} portfolio records...")
    
    if batch:
        insert_batch(session, 'portfolio_holdings', batch, len(batch))
    
    cluster.shutdown()

def load_market_analytics(host, num_records, batch_size):
    """Load market analytics data"""
    cluster = Cluster([host])
    session = cluster.connect('stock_market')
    
    print(f"Loading {num_records} market analytics records...")
    
    batch = []
    base_date = datetime.now() - timedelta(days=365)
    
    for i in range(num_records):
        symbol = random.choice(COMPANIES)
        analysis_date = (base_date + timedelta(days=random.randint(0, 365))).date()
        avg_price = round(random.uniform(50, 500), 2)
        total_volume = random.randint(10000000, 500000000)
        price_change_percent = round(random.uniform(-10, 10), 2)
        volatility = round(random.uniform(0.1, 5.0), 2)
        
        batch.append((symbol, analysis_date, avg_price, total_volume, price_change_percent, volatility))
        
        if len(batch) >= batch_size:
            insert_batch(session, 'market_analytics', batch, batch_size)
            batch = []
            
            if i % 10000 == 0:
                print(f"Inserted {i} analytics records...")
    
    if batch:
        insert_batch(session, 'market_analytics', batch, len(batch))
    
    cluster.shutdown()

def main():
    host = sys.argv[1]
    num_records = int(sys.argv[2])
    batch_size = int(sys.argv[3])
    num_workers = int(sys.argv[4])
    create_schema_flag = sys.argv[5].lower() == 'true'
    drop_existing = sys.argv[6].lower() == 'true'
    
    print(f"Connecting to Cassandra at {host}...")
    cluster = Cluster([host])
    session = cluster.connect()
    
    if create_schema_flag:
        create_schema(session, drop_existing)
    else:
        session.set_keyspace('stock_market')
    
    # Insert company reference data
    print("Loading company data...")
    companies_data = [generate_company() for _ in range(len(COMPANIES))]
    insert_batch(session, 'companies', companies_data, batch_size)
    
    cluster.shutdown()
    
    # Load stock prices with parallel workers
    start_time = time.time()
    print(f"\n{'='*60}")
    print(f"Starting data load with {num_workers} workers...")
    print(f"Total records: {num_records:,}")
    print(f"Batch size: {batch_size}")
    print(f"{'='*60}\n")
    
    # Distribute stock prices across workers
    stock_price_records = int(num_records * 0.5)
    processes = []
    for i in range(num_workers):
        p = mp.Process(target=load_stock_prices, args=(host, stock_price_records, batch_size, i, num_workers))
        p.start()
        processes.append(p)
    
    for p in processes:
        p.join()
    
    # Load other tables
    trade_records = int(num_records * 0.25)
    load_trades(host, trade_records, batch_size)
    
    portfolio_records = int(num_records * 0.15)
    load_portfolio_holdings(host, portfolio_records, batch_size)
    
    analytics_records = int(num_records * 0.10)
    load_market_analytics(host, analytics_records, batch_size)
    
    elapsed = time.time() - start_time
    print(f"\n{'='*60}")
    print(f"Data load completed!")
    print(f"Total time: {elapsed:.2f} seconds")
    print(f"Records per second: {num_records/elapsed:,.0f}")
    print(f"{'='*60}")

if __name__ == '__main__':
    main()
PYTHON_SCRIPT
                    '''
                }
            }
        }
        
        stage('Wait for Cassandra') {
            steps {
                echo 'Waiting for Cassandra cluster to be ready...'
                sh '''
                    MAX_WAIT=60
                    COUNTER=0
                    
                    while [ $COUNTER -lt $MAX_WAIT ]; do
                        if docker exec ${CASSANDRA_HOST} nodetool status 2>/dev/null | grep -q "UN"; then
                            echo "✅ Cassandra cluster is ready!"
                            docker exec ${CASSANDRA_HOST} nodetool status
                            break
                        fi
                        echo "Waiting for Cassandra... ($COUNTER/$MAX_WAIT)"
                        sleep 2
                        COUNTER=$((COUNTER + 1))
                    done
                    
                    if [ $COUNTER -eq $MAX_WAIT ]; then
                        echo "ERROR: Cassandra did not become ready in time"
                        exit 1
                    fi
                '''
            }
        }
        
        stage('Load Data') {
            steps {
                script {
                    def startTime = System.currentTimeMillis()
                    echo "Starting data load: ${params.NUM_RECORDS} records..."
                    
                    sh """
                        python3 /tmp/cassandra_loader.py \
                            ${params.CASSANDRA_HOST} \
                            ${params.NUM_RECORDS} \
                            ${params.BATCH_SIZE} \
                            ${params.NUM_WORKERS} \
                            ${params.CREATE_SCHEMA} \
                            ${params.DROP_EXISTING}
                    """
                    
                    def endTime = System.currentTimeMillis()
                    def duration = (endTime - startTime) / 1000
                    
                    echo """
                    ================================================
                    ✅ Data Load Summary
                    ================================================
                    Total Records: ${params.NUM_RECORDS}
                    Duration: ${duration} seconds
                    Throughput: ${(params.NUM_RECORDS.toInteger() / duration).round(0)} records/sec
                    Batch Size: ${params.BATCH_SIZE}
                    Workers: ${params.NUM_WORKERS}
                    ================================================
                    """
                }
            }
        }
        
        stage('Verify Data') {
            steps {
                echo 'Verifying loaded data...'
                sh '''
                    docker exec ${CASSANDRA_HOST} cqlsh -e "
                        USE stock_market;
                        SELECT COUNT(*) FROM companies;
                        SELECT symbol, company_name, sector FROM companies LIMIT 5;
                        SELECT COUNT(*) FROM stock_prices;
                        SELECT symbol, trade_date, open_price, close_price FROM stock_prices LIMIT 5;
                        SELECT COUNT(*) FROM trades;
                        SELECT COUNT(*) FROM portfolio_holdings;
                        SELECT COUNT(*) FROM market_analytics;
                    " || echo "Note: COUNT queries may timeout on large datasets"
                    
                    echo ""
                    echo "Data verification completed!"
                    echo "Connect to query data: docker exec -it ${CASSANDRA_HOST} cqlsh"
                    echo "Keyspace: stock_market"
                '''
            }
        }
    }
    
    post {
        success {
            echo "✅ Successfully loaded ${params.NUM_RECORDS} stock market records!"
        }
        failure {
            echo "❌ Data load failed. Check logs for details."
        }
        always {
            echo "Data load operation completed"
        }
    }
}
