# Semantic OLAP MCP Server

A **Model Context Protocol (MCP)** server that exposes a governed OLAP semantic layer to AI agents.

Built with **Spring Boot**, **Spring AI MCP**, and **PostgreSQL**. Agents can discover cubes, inspect metadata, list dimension members, and run structured analytical queries (slice, dice, drill) without writing SQL.

---

## Features

- **Semantic metamodel** – Cubes, dimensions, hierarchies, levels, measures, joins
- **Logical query model** – Structured `OlapQuery` (measures, drill levels, filters, sorts, limit/offset)
- **SQL generation** – Star/snowflake ROLAP SQL for PostgreSQL
- **Safe execution** – Hard row limits, result mapping to a compact `CellSet`
- **MCP surface**
    - **Resources** – Schema summary & per-cube metadata
    - **Tools** – `execute_olap_query`, `list_dimension_members`
    - **Prompts** – Root-cause analysis & cube exploration workflows
- **Agent-friendly results** – JSON `CellSet` + optional Markdown preview

---

## Architecture

```
AI Agent / MCP Inspector
        │
        ▼
┌─────────────────────────────┐
│  OlapMcpController          │  @McpTool / @McpResource / @McpPrompt
│  (MCP adapter)              │
└─────────────┬───────────────┘
              │
              ▼
┌─────────────────────────────┐
│  OlapQueryExecutor          │   limits, JDBC execution, CellSet mapping
└─────────────┬───────────────┘
              │
              ▼
┌─────────────────────────────┐
│  PostgresSqlGenerator       │  Cube + OlapQuery → SQL
└─────────────┬───────────────┘
              │
              ▼
┌─────────────────────────────┐
│  PostgreSQL                 │  fact + dimension tables
└─────────────────────────────┘
```

Core packages:

| Package | Responsibility |
|---------|----------------|
| `metamodel.models` / `enums` | Cube, Dimension, Hierarchy, Level, Measure, … |
| `metamodel.query` | Logical `OlapQuery`, filters, drill levels, sorts |
| `metamodel.validators` | Strategy-based validation rules |
| `dialect` | `SqlGenerator` + PostgreSQL dialect |
| `execution` | `OlapQueryExecutor` + `CellSet` result model |
| `server` | MCP tools, resources, prompts |
| `config` | Beans (`CubeSchema`, `SqlGenerator`, `OlapQueryExecutor`) |
| `dto` | Request/response types for MCP tools |

---

## Prerequisites

- **Java 25+**
- **Node.js 22+** (only for MCP Inspector)
- **Docker** (for PostgreSQL)
- **ollama** (for using granite model)
- **python**
- **ollmcp** (to test the tools and resources via granite model)
- **Gradle** 

---

## Quick start

### 1. Start PostgreSQL

```bash
docker compose up -d
```

`docker-compose.yaml` exposes Postgres on port `5432` with:

| Setting  | Value     |
|----------|-----------|
| Database | `springai` |
| User     | `springai` |
| Password | `springai` |

### 2. Initialize sample schema (optional but recommended)

Run the DDL/seed script (e.g. `src/main/resources/schema.sql`) against the database, or enable Spring SQL init:


Sample tables: `fact_sales`, `dim_time`, `dim_product`.

### 3. Configure the application

`src/main/resources/application.yaml`:

```yaml
spring:
  application:
    name: semantic-olap-mcp

  datasource:
    url: jdbc:postgresql://localhost:5432/springai
    username: springai
    password: springai
    driver-class-name: org.postgresql.Driver

  ai:
    mcp:
      server:
        name: olap-mcp-server
        version: 1.0.0
        protocol: STREAMABLE
        type: SYNC

server:
  port: 8080
```

### 4. Run the server

```bash
./gradlew bootRun
```

Default MCP endpoint: **`http://localhost:8080/mcp`**

---

## Test with MCP Inspector

```bash
npx @modelcontextprotocol/inspector
```

In the UI:

| Field       | Value                         |
|-------------|-------------------------------|
| Transport   | **Streamable HTTP**           |
| URL         | `http://localhost:8080/mcp`   |

Click **Connect**.

### Suggested test sequence

1. **Resources →** read `cube://schema/summary`
2. **Resources →** read `cube://schema/Sales`
3. **Tools →** `list_dimension_members`

```json
{
  "cubeName": "Sales",
  "dimensionName": "Time",
  "hierarchyName": "Calendar",
  "levelName": "Year"
}
```

4. **Tools →** `execute_olap_query` (minimal)

| Field         | Value |
|---------------|--------|
| `cubeName`    | `Sales` |
| `measures`    | `["SalesAmount"]` |
| `drillLevels` | `[{"dimension":"Time","hierarchy":"Calendar","level":"Year"}]` |
| `filters`     | `[]` |
| `limit`       | `10` |
| `offset`      | `0` |

5. **Tools →** `execute_olap_query` with filter

```json
{
  "cubeName": "Sales",
  "measures": ["SalesAmount", "OrderCount"],
  "drillLevels": [
    { "dimension": "Time", "hierarchy": "Calendar", "level": "Year" },
    { "dimension": "Product", "hierarchy": "ProductHierarchy", "level": "Category" }
  ],
  "filters": [
    {
      "dimension": "Time",
      "hierarchy": "Calendar",
      "level": "Year",
      "operator": "EQUALS",
      "values": [2023]
    }
  ],
  "limit": 20,
  "offset": 0
}
```

---

# Test with ollmcp + Granite 4.1 (3B)

Drive the Semantic OLAP MCP server from a local Ollama model using [ollmcp](https://pypi.org/project/ollmcp/) (MCP Client for Ollama).

---

## Prerequisites

```bash
# Ollama must be running
ollama serve

# Pull a tool-capable model
ollama pull granite4.1:3b

# Install the client (Python 3.11+)
pip install --upgrade ollmcp
# or
uv tool install --upgrade ollmcp
```

Keep the Spring Boot OLAP MCP server running at:

```text
http://localhost:8080/mcp
```

Ensure `application.yaml` has:

```yaml
spring:
  ai:
    mcp:
      server:
        protocol: STREAMABLE
```

---

## Register the server

### Option A – one-shot CLI

```bash
ollmcp mcp add --transport http olap http://localhost:8080/mcp
```

### Option B – JSON config file

Create `olap-servers.json`:

```json
{
  "mcpServers": {
    "olap": {
      "type": "streamable_http",
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

Start with:

```bash
ollmcp -m granite4.1:3b -j olap-servers.json
```

---

## Start the client

```bash
ollmcp -m granite4.1:3b
```

(or just `ollmcp` if the server was already added and you want the last-used model)

### Useful in-session commands

| Command | Purpose |
|---------|---------|
| `/help` | Help |
| `/tools` (`t`) | List / enable / disable tools |
| `/model` (`m`) | Switch model |
| `/resources` | Browse MCP resources |
| `/prompts` | Browse MCP prompts |

---

## Example prompts

**Discover schema**

```text
Read the resource cube://schema/summary and tell me which cubes and measures exist.
```

**Inspect Sales cube**

```text
Read cube://schema/Sales and summarise the dimensions and hierarchies.
```

**List dimension members**

```text
Use list_dimension_members for cube Sales, dimension Time, hierarchy Calendar, level Year.
```

**Run a simple query**

```text
Run execute_olap_query on cube Sales with measure SalesAmount,
drill to Time/Calendar/Year, limit 10. Summarise the result.
```

**Richer query with filter**

```text
Query Sales: measures SalesAmount and OrderCount,
drill Year and Product Category, filter Year = 2023, limit 20.
Explain the numbers.
```

---


## Minimal happy path

```bash
# Terminal 1 – MCP server
./gradlew bootRun
# or: ./mvnw spring-boot:run

# Terminal 2 – client
ollama pull granite4.1:3b
ollmcp mcp add --transport http olap http://localhost:8080/mcp
ollmcp -m granite4.1:3b
```

Then ask:

```text
What cubes are available? Use the schema summary resource.
```


---
## MCP API surface

### Resources

| URI | Description |
|-----|-------------|
| `cube://schema/summary` | All cubes with measure & dimension names |
| `cube://schema/{cubeName}` | Full metadata for one cube (JSON) |

### Tools

| Tool | Description |
|------|-------------|
| `execute_olap_query` | Run a structured OLAP query → `CellSet` |
| `list_dimension_members` | Distinct members of a dimension level |

### Prompts

| Prompt | Description |
|--------|-------------|
| `root_cause_analysis` | Guided drill-down RCA workflow |
| `explore_cube` | Discover a cube and suggest questions |

---

## Sample cube (`Sales`)

| Element | Details |
|---------|---------|
| Fact table | `public.fact_sales` (`f`) |
| Dimensions | **Time** (Calendar: Year → Quarter → Month), **Product** (Category → Product) |
| Measures | `SalesAmount` (SUM), `OrderCount` (COUNT DISTINCT), `Quantity` (SUM) |

Defined in `CubeSchemaConfig` as a Spring `@Bean`. Replace or extend with your own cubes.

---

## Project structure (main sources)

```
src/main/java/olap/ai/mcp/
├── StartApp.java
├── config/
│   ├── CubeSchemaConfig.java      # CubeSchema bean (sample Sales cube)
│   └── OlapEngineConfig.java      # SqlGenerator, OlapQueryExecutor
├── dialect/
│   ├── SqlGenerator.java
│   ├── SqlDialect.java
│   ├── PostgresDialect.java
│   └── PostgresSqlGenerator.java
├── execution/
│   ├── OlapQueryExecutor.java
│   └── model/                     # AxisMember, Cell, CellSet
├── metamodel/
│   ├── models/                    # Cube, Dimension, Hierarchy, Level, Measure, …
│   ├── enums/
│   ├── query/                     # OlapQuery, DrillLevel, FilterPredicate, SortSpec
│   └── validators/                # Strategy-based validation rules
├── dto/                           # MCP request/response types
└── server/
    └── OlapMcpController.java     # @McpTool / @McpResource / @McpPrompt
```

---

  

## Roadmap 

- [ ] Load `CubeSchema` from YAML/JSON instead of hard-coded Java
- [ ] Additional SQL dialects (DuckDB, MySQL, …)
- [ ] Query validation against the metamodel before SQL generation
- [ ] Markdown-friendly tool variant of `execute_olap_query`
- [ ] Authentication / row-level security for multi-tenant deployments
- [ ] Aggregate awareness / simple query cache
