# ECM Identity Service - Deployment & Operations Guide

## Container & Orchestration

### Docker Configuration
```dockerfile
FROM openjdk:21-jre-slim
COPY target/ecm-identity-service.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS="-Xmx2g -Xms1g -XX:+UseG1GC"
ENTRYPOINT ["java", "$JAVA_OPTS", "-jar", "/app.jar"]
```

### Kubernetes Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ecm-identity-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: ecm-identity-service
  template:
    spec:
      containers:
      - name: ecm-identity-service
        image: ecm-identity-service:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "production"
        - name: DATABASE_URL
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: url
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
```

### Service & Ingress Configuration
```yaml
apiVersion: v1
kind: Service
metadata:
  name: ecm-identity-service
spec:
  selector:
    app: ecm-identity-service
  ports:
  - port: 80
    targetPort: 8080
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ecm-identity-ingress
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
spec:
  tls:
  - hosts:
    - identity.ecm.com
    secretName: ecm-identity-tls
  rules:
  - host: identity.ecm.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: ecm-identity-service
            port:
              number: 80
```

## Database Deployment

### PostgreSQL Configuration
```yaml
apiVersion: postgresql.cnpg.io/v1
kind: Cluster
metadata:
  name: ecm-identity-db
spec:
  instances: 3
  postgresql:
    parameters:
      max_connections: "200"
      shared_buffers: "256MB"
      effective_cache_size: "1GB"
      work_mem: "16MB"
      maintenance_work_mem: "64MB"
      checkpoint_completion_target: "0.9"
      wal_buffers: "16MB"
      default_statistics_target: "100"
      random_page_cost: "1.1"
  
  bootstrap:
    initdb:
      database: ecm_identity
      owner: ecm_user
      secret:
        name: db-credentials
  
  storage:
    size: 100Gi
    storageClass: fast-ssd
  
  monitoring:
    enabled: true
```

### Redis Configuration
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: redis-config
data:
  redis.conf: |
    maxmemory 1gb
    maxmemory-policy allkeys-lru
    timeout 300
    tcp-keepalive 60
    save 900 1
    save 300 10
    save 60 10000
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: redis
spec:
  serviceName: redis
  replicas: 3
  selector:
    matchLabels:
      app: redis
  template:
    spec:
      containers:
      - name: redis
        image: redis:7-alpine
        ports:
        - containerPort: 6379
        volumeMounts:
        - name: redis-data
          mountPath: /data
        - name: redis-config
          mountPath: /usr/local/etc/redis/redis.conf
          subPath: redis.conf
        command: ["redis-server", "/usr/local/etc/redis/redis.conf"]
  volumeClaimTemplates:
  - metadata:
      name: redis-data
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 20Gi
```

## Environment Configuration

### Production Application Properties
```properties
# Server Configuration
server.port=8080
server.servlet.context-path=/
server.compression.enabled=true

# Database Configuration
spring.datasource.url=${DATABASE_URL}
spring.datasource.username=${DATABASE_USERNAME}
spring.datasource.password=${DATABASE_PASSWORD}
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=20000
spring.datasource.hikari.idle-timeout=300000
spring.datasource.hikari.max-lifetime=1200000

# Redis Configuration
spring.data.redis.host=${REDIS_HOST}
spring.data.redis.port=${REDIS_PORT}
spring.data.redis.password=${REDIS_PASSWORD}
spring.data.redis.lettuce.pool.max-active=20
spring.data.redis.lettuce.pool.max-idle=8
spring.data.redis.lettuce.pool.min-idle=0

# Security Configuration
ecm.security.jwt.secret=${JWT_SECRET}
ecm.security.jwt.access-token-expiration=900
ecm.security.jwt.refresh-token-expiration=2592000
ecm.security.rate-limiting.enabled=true

# Monitoring
management.endpoints.web.exposure.include=health,metrics,info,prometheus
management.endpoint.health.show-details=when-authorized
management.metrics.export.prometheus.enabled=true

# Logging
logging.level.com.ecm.security.identity=INFO
logging.level.org.springframework.security=WARN
logging.level.org.hibernate.SQL=WARN
```

### Secret Management
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: ecm-identity-secrets
type: Opaque
data:
  database-url: <base64-encoded-url>
  database-username: <base64-encoded-username>
  database-password: <base64-encoded-password>
  redis-password: <base64-encoded-password>
  jwt-secret: <base64-encoded-secret>
  oauth2-client-secret: <base64-encoded-secret>
```

## Load Balancing & High Availability

### Nginx Load Balancer Configuration
```nginx
upstream ecm_identity_backend {
    least_conn;
    server ecm-identity-1:8080 max_fails=3 fail_timeout=30s;
    server ecm-identity-2:8080 max_fails=3 fail_timeout=30s;
    server ecm-identity-3:8080 max_fails=3 fail_timeout=30s;
}

server {
    listen 443 ssl http2;
    server_name identity.ecm.com;
    
    ssl_certificate /etc/ssl/certs/identity.ecm.com.crt;
    ssl_certificate_key /etc/ssl/private/identity.ecm.com.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-RSA-AES256-GCM-SHA512:DHE-RSA-AES256-GCM-SHA512;
    ssl_prefer_server_ciphers off;
    
    # Security Headers
    add_header Strict-Transport-Security "max-age=63072000; includeSubDomains; preload";
    add_header X-Frame-Options DENY;
    add_header X-Content-Type-Options nosniff;
    add_header X-XSS-Protection "1; mode=block";
    
    # Rate Limiting
    limit_req_zone $binary_remote_addr zone=login:10m rate=5r/s;
    limit_req_zone $binary_remote_addr zone=api:10m rate=100r/s;
    
    location /api/v1/auth/login {
        limit_req zone=login burst=10 nodelay;
        proxy_pass http://ecm_identity_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
    
    location /api/ {
        limit_req zone=api burst=200 nodelay;
        proxy_pass http://ecm_identity_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

## Monitoring & Alerting

### Prometheus Configuration
```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - "ecm_identity_rules.yml"

scrape_configs:
  - job_name: 'ecm-identity-service'
    static_configs:
      - targets: ['ecm-identity-service:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 30s
```

### Alert Rules
```yaml
groups:
- name: ecm_identity_alerts
  rules:
  - alert: HighErrorRate
    expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.1
    for: 2m
    annotations:
      summary: "High error rate detected"
      description: "Error rate is {{ $value }} errors per second"
  
  - alert: HighResponseTime
    expr: histogram_quantile(0.95, http_server_requests_seconds) > 1
    for: 5m
    annotations:
      summary: "High response time detected"
      description: "95th percentile response time is {{ $value }} seconds"
  
  - alert: DatabaseConnectionPoolExhausted
    expr: hikaricp_connections_active / hikaricp_connections_max > 0.8
    for: 3m
    annotations:
      summary: "Database connection pool usage high"
      description: "Connection pool usage is {{ $value | humanizePercentage }}"
```

### Grafana Dashboard
```json
{
  "dashboard": {
    "title": "ECM Identity Service",
    "panels": [
      {
        "title": "Request Rate",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(http_server_requests_seconds_count[5m])",
            "legendFormat": "{{method}} {{uri}}"
          }
        ]
      },
      {
        "title": "Response Time",
        "type": "graph",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, http_server_requests_seconds)",
            "legendFormat": "95th percentile"
          }
        ]
      },
      {
        "title": "Active Sessions",
        "type": "singlestat",
        "targets": [
          {
            "expr": "ecm_identity_active_sessions_total",
            "legendFormat": "Active Sessions"
          }
        ]
      }
    ]
  }
}
```

## Backup & Disaster Recovery

### Database Backup Strategy
```bash
#!/bin/bash
# Automated backup script
BACKUP_DIR="/backups/ecm-identity"
DB_NAME="ecm_identity"
DATE=$(date +%Y%m%d_%H%M%S)

# Create encrypted backup
pg_dump -h $DB_HOST -U $DB_USER $DB_NAME | \
  gzip | \
  gpg --cipher-algo AES256 --compress-algo 1 --symmetric \
      --output "$BACKUP_DIR/ecm_identity_$DATE.sql.gz.gpg"

# Upload to cloud storage
aws s3 cp "$BACKUP_DIR/ecm_identity_$DATE.sql.gz.gpg" \
  s3://ecm-identity-backups/database/

# Cleanup old backups (keep 30 days)
find $BACKUP_DIR -name "*.sql.gz.gpg" -mtime +30 -delete
```

### Redis Backup
```bash
#!/bin/bash
# Redis backup script
REDIS_HOST="redis.ecm.com"
BACKUP_DIR="/backups/redis"
DATE=$(date +%Y%m%d_%H%M%S)

# Create Redis snapshot
redis-cli -h $REDIS_HOST BGSAVE
sleep 10

# Copy RDB file
scp redis@$REDIS_HOST:/var/lib/redis/dump.rdb \
  "$BACKUP_DIR/redis_dump_$DATE.rdb"

# Encrypt and upload
gpg --cipher-algo AES256 --symmetric \
    --output "$BACKUP_DIR/redis_dump_$DATE.rdb.gpg" \
    "$BACKUP_DIR/redis_dump_$DATE.rdb"

aws s3 cp "$BACKUP_DIR/redis_dump_$DATE.rdb.gpg" \
  s3://ecm-identity-backups/redis/
```

## Security Hardening

### Network Security
- **Firewall Rules**: Restrict database access to application pods only
- **Network Policies**: Kubernetes network segmentation
- **VPN Access**: Administrative access through VPN only
- **TLS Everywhere**: End-to-end encryption for all communications

### Container Security
- **Non-root User**: Run containers with non-privileged user
- **Read-only Filesystem**: Mount application directories as read-only
- **Security Scanning**: Regular vulnerability scans of container images
- **Resource Limits**: CPU and memory limits to prevent resource exhaustion

### Secret Rotation
```bash
#!/bin/bash
# Automated secret rotation script
NEW_JWT_SECRET=$(openssl rand -base64 64)
NEW_DB_PASSWORD=$(openssl rand -base64 32)

# Update secrets in Kubernetes
kubectl patch secret ecm-identity-secrets \
  -p '{"data":{"jwt-secret":"'$(echo -n $NEW_JWT_SECRET | base64)'"}}'

# Trigger rolling deployment
kubectl rollout restart deployment/ecm-identity-service

# Verify deployment
kubectl rollout status deployment/ecm-identity-service
```

This deployment guide provides production-ready configuration for high availability, security, monitoring, and disaster recovery of the ECM Identity Service.