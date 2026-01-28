# Research Project Application Form Generator

An intelligent agent system for automatically generating research project application forms using Spring AI Alibaba and multi-agent architecture.

## Architecture Overview

The system implements a **4-step pipeline** with specialized agents:

1. **ResearcherAgent** - Evidence collection from Elasticsearch and web search
2. **PlannerAgent** - Creates logical blueprint with scientific questions, hypotheses, and innovation points
3. **WriterAgent** - Generates proposal content based on evidence and blueprint
4. **CriticAgent** - Quality assurance, compliance checking, and validation

### Key Components

- `ProposalEvidencePack` - Standardized evidence container with credibility hints
- `ProposalDraftPack` - Structured proposal output with logical chains
- `ProposalGenerationOrchestrator` - Coordinates the 4-step pipeline

## Prerequisites

- Java 17+
- Maven 3.6+
- Redis
- MongoDB
- Elasticsearch 7.10.x
- DashScope API access (Alibaba Cloud)

## Environment Variables

Create a `.env` file or set the following environment variables before running:

### Required - LLM API Keys

```bash
# Option 1: Single API key
export DASHSCOPE_API_KEY=sk-your-dashscope-api-key

# Option 2: Multiple API keys for load balancing (comma-separated)
export DASHSCOPE_API_KEYS=sk-key1,sk-key2,sk-key3

# Or use numbered keys (LLM_API_KEY_1 through LLM_API_KEY_10)
export LLM_API_KEY_1=sk-key1
export LLM_API_KEY_2=sk-key2
```

### Required - Database Connections

```bash
# Elasticsearch
export ES_URIS=http://your-elasticsearch-host:9200
export ES_USERNAME=elastic
export ES_PASSWORD=your-es-password

# MongoDB (dev profile uses dual MongoDB)
export MONGODB_EVIMED_URI=mongodb://user:password@host:27017/evimed_test
export MONGODB_EVIMED_RELEASE_URI=mongodb://user:password@host:27017/evimed_new

# MongoDB (release profile uses single MongoDB)
export MONGODB_URI=mongodb://user:password@host:27017/evimed_new

# Redis
export REDIS_HOST=your-redis-host
export REDIS_PORT=6379
export REDIS_DATABASE=1
export REDIS_PASSWORD=your-redis-password
```

### Required - Web Search APIs

```bash
export SEARCH_API_KEY=your-search-api-key
export BOCHA_API_KEY=your-bocha-api-key
```

### Optional - Aliyun OSS (for file storage)

```bash
export OSS_ENDPOINT=oss-cn-beijing.aliyuncs.com
export OSS_ACCESS_KEY_ID=your-access-key-id
export OSS_ACCESS_KEY_SECRET=your-access-key-secret
export OSS_BUCKET_NAME=your-bucket-name
export OSS_FILE_URL=https://your-oss-url
```

## Building

```bash
# Clean and build
mvn clean package -DskipTests

# Build with tests
mvn clean package
```

## Running

### Development Mode

```bash
# Set environment variables first, then:
java -jar target/project-application-form-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

### Production Mode

```bash
# Set environment variables first, then:
java -jar target/project-application-form-0.0.1-SNAPSHOT.jar --spring.profiles.active=release
```

### Using Docker (recommended)

```dockerfile
FROM openjdk:17-slim

WORKDIR /app
COPY target/project-application-form-0.0.1-SNAPSHOT.jar app.jar

# Environment variables should be provided at runtime
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
# Build image
docker build -t proposal-generator .

# Run with environment variables
docker run -d \
  -e DASHSCOPE_API_KEY=sk-xxx \
  -e ES_URIS=http://es:9200 \
  -e ES_USERNAME=elastic \
  -e ES_PASSWORD=xxx \
  -e MONGODB_URI=mongodb://... \
  -e REDIS_HOST=redis \
  -e REDIS_PASSWORD=xxx \
  -p 2037:2037 \
  proposal-generator
```

## API Endpoints

The application runs on port `2037` (dev) or `2039` (release) by default.

### Health Check
```
GET /actuator/health
```

### Generate Proposal
Refer to the API documentation (Swagger UI available at `/swagger-ui.html` when running).

## Configuration Files

| File | Purpose |
|------|---------|
| `application.yml` | Base configuration, sets active profile |
| `application-dev.yml` | Development environment settings |
| `application-release.yml` | Production environment settings |

## Security Notes

- **Never commit credentials** - All secrets are loaded from environment variables
- API keys are loaded dynamically and support rotation
- The `.gitignore` excludes sensitive files and logs
- Use environment variable injection for all deployments

## Compliance Features

The CriticAgent enforces academic writing standards:

- Detects forbidden phrases: "首次", "首创", "唯一", "已完成", "已验证"
- Suggests alternatives like "有望", "拟", "预期"
- Validates citation traceability
- Checks logical consistency between sections

## Troubleshooting

### Connection Issues

1. Verify all environment variables are set correctly
2. Check network connectivity to external services
3. Review logs in `logs/faers-analysis.log`

### API Key Errors

1. Ensure API keys are valid and have sufficient quota
2. Check if multiple keys are configured for failover
3. Review the LLM invocation logs for specific errors

### Memory Issues

For large proposals, increase JVM heap:
```bash
java -Xmx4g -jar target/project-application-form-0.0.1-SNAPSHOT.jar
```

## License

Proprietary - All rights reserved.
