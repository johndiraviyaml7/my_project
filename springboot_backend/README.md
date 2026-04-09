# QuantixMed DICOM Backend — Spring Boot

REST API layer for the QuantixMed DICOM system.  
Reads parsed data from PostgreSQL and exposes it to the ReactJS frontend.

---

## Prerequisites

- Java 21
- Maven 3.9+
- PostgreSQL 15+ (schema created by the Python parser)

---

## Configuration

Edit `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/quantixmed_dicom
spring.datasource.username=postgres
spring.datasource.password=postgres
```

---

## Build & Run

```bash
cd springboot_backend
mvn clean package -DskipTests
java -jar target/dicom-backend-1.0.0.jar
```

Or during development:
```bash
mvn spring-boot:run
```

API runs on **http://localhost:8080**  
Swagger UI: **http://localhost:8080/swagger-ui.html**

---

## REST API Reference

### Authentication
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/login` | Login (returns JWT) |

### PACS Devices
| Method | Path | Description |
|--------|------|-------------|
| GET    | `/api/devices` | List all devices |
| GET    | `/api/devices/{id}` | Get device details |
| POST   | `/api/devices` | Register new device |
| PUT    | `/api/devices/{id}` | Update device |
| DELETE | `/api/devices/{id}` | Delete device |

### Subjects
| Method | Path | Description |
|--------|------|-------------|
| GET    | `/api/devices/{deviceId}/subjects` | List subjects for device |
| GET    | `/api/subjects/{id}` | Get subject details |

### Studies
| Method | Path | Description |
|--------|------|-------------|
| GET    | `/api/subjects/{subjectId}/studies` | List studies for subject |
| GET    | `/api/studies/{id}` | Get study details |

### Series
| Method | Path | Description |
|--------|------|-------------|
| GET    | `/api/studies/{studyId}/series` | List series for study |
| GET    | `/api/series/{id}` | Get series details |

### Instances
| Method | Path | Description |
|--------|------|-------------|
| GET    | `/api/series/{seriesId}/instances` | List instances |
| GET    | `/api/instances/{id}` | Get instance details |

### Images
| Method | Path | Description |
|--------|------|-------------|
| GET    | `/api/series/{seriesId}/images` | DICOM images + JPEG URLs |
| GET    | `/api/images/jpeg/{filename}` | Serve JPEG image |
| GET    | `/api/images/thumb/{filename}` | Serve thumbnail |
| GET    | `/api/images/collage/{filename}` | Serve study collage |

---

## Architecture

```
ReactJS Frontend (port 5173)
        │  REST calls
        ▼
Spring Boot API (port 8080)
        │  JPA / JDBC
        ▼
PostgreSQL Database (port 5432)
        ▲
        │  INSERT parsed data
Python DICOM Parser (port 8000)
```

---

## De-identification Compliance

All PHI fields stored in masked BYTEA columns.  
De-identified surrogate IDs available on all entities.  
Audit trail in `deid_audit_log` table.

Standards:
- DICOM PS3.15 Annex E (2026)
- HIPAA 45 CFR §164.514(b) Safe Harbor
- FDA 21 CFR Part 11
